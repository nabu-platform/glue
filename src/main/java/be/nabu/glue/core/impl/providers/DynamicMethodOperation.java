/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.glue.core.impl.providers;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.Script;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.api.MethodProvider;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.LambdaMethodProvider;
import be.nabu.glue.impl.ForkedExecutionContext;
import be.nabu.glue.impl.TransactionalCloseable;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.glue.utils.ScriptUtils;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseOperation;
import be.nabu.libs.evaluator.impl.MethodOperation;
import be.nabu.libs.metrics.api.MetricInstance;
import be.nabu.libs.metrics.api.MetricProvider;
import be.nabu.libs.metrics.api.MetricTimer;
import be.nabu.utils.io.IOUtils;

@SuppressWarnings("rawtypes")
public class DynamicMethodOperation extends BaseOperation {

	private Operation<ExecutionContext> operation;

	private MethodProvider [] methodProviders;
	
	public static final String METRIC_EXECUTION_TIME = "methodExecutionTime";
	
	// this was added to prevent static resolution of lambdas, this was the case for long form lambdas that had other lambdas as input
	// the operation was looked up the first time (so first lambda passed in) and cached in the operation here
	// any subsequent calls to the long form lambda used the first parameter, not subsequent lambdas passed in
	// the same goes for the rewriting logic of method calls but it is assumed that all passed in lambdas have the same specification so that step is still cached
	private boolean isDynamic;
	
	public DynamicMethodOperation(MethodProvider...methodProviders) {
		this.methodProviders = methodProviders;
	}
	
	@SuppressWarnings({ "unchecked" })
	@Override
	public Object evaluate(Object context) throws EvaluationException {
		try {
			Operation<ExecutionContext> operation = buildOperation();
			if (operation != null) {
				ExecutionContext executionContext;
				if (context instanceof ExecutionContext) {
					executionContext = (ExecutionContext) context;
				}
				// for example when doing calculations inside a variable scope: employees[age > 60] in this case the employees is not an execution context
				else if (context instanceof Map) {
					executionContext = new ForkedExecutionContext(ScriptRuntime.getRuntime().getExecutionContext(), true);
					executionContext.getPipeline().putAll((Map) context);
				}
				else {
					// TODO: can use accessor to map anything that is supported
//					throw new RuntimeException("Only execution context and map are supported currently, can not execute: " + operation + " on " + context);
					executionContext = new ForkedExecutionContext(ScriptRuntime.getRuntime().getExecutionContext(), true);
					executionContext.getPipeline().put("$this", context);
				}
				MetricInstance metrics = getMetrics(operation);
				MetricTimer timer = metrics == null ? null : metrics.start(METRIC_EXECUTION_TIME);
				Object evaluated;
				try {
					evaluated = operation.evaluate(executionContext);
				}
				finally {
					if (timer != null) {
						timer.stop();
					}
				}
				return postProcess(evaluated);
			}
			else if (((List<QueryPart>) getParts()).get(0).getContent() instanceof Operation) {
				Object result = ((Operation) ((List<QueryPart>) getParts()).get(0).getContent()).evaluate(context);
				if (result instanceof Lambda) {
					List parameters = new ArrayList();
					for (int i = 1; i < getParts().size(); i++) {
						parameters.add(((List<QueryPart>) getParts()).get(i).getContent() instanceof Operation ? ((Operation) ((List<QueryPart>) getParts()).get(i).getContent()).evaluate(context) : ((List<QueryPart>) getParts()).get(i).getContent());
					}
					return postProcess(GlueUtils.calculate((Lambda) result, ScriptRuntime.getRuntime(), parameters));
				}
				throw new EvaluationException("Could not resolve a lambda: " + ((List<QueryPart>) getParts()).get(0).getContent() + " (resolved: " + result + ")");
			}
			else {
				throw new EvaluationException("Could not resolve a method with the name: " + ((List<QueryPart>) getParts()).get(0).getContent());
			}
		}
		catch (ParseException e) {
			throw new EvaluationException(e);
		}
	}

	private MetricInstance getMetrics(Operation<ExecutionContext> operation) {
		MetricInstance metrics = null;
		ExecutionContext executionContext = ScriptRuntime.getRuntime().getExecutionContext();
		Executor executor = executionContext.getCurrent();
		while (executionContext instanceof ForkedExecutionContext) {
			executionContext = ((ForkedExecutionContext) executionContext).getParent();
		}
		if (executionContext instanceof MetricProvider) {
			Script script = ScriptRuntime.getRuntime().getScript();
			int line = executor != null && executor.getContext() != null ? executor.getContext().getLineNumber() + 1 : -1;
			String id = (script.getNamespace() != null ? script.getNamespace() + "." : "") + script.getName() + "$" + line + "$" + operation.getParts().get(0).getContent();
			metrics = ((MetricProvider) executionContext).getMetricInstance(id);
		}
		return metrics;
	}
	
	private static Object postProcess(Object returnValue) {
		// from version 2 onwards we immediately load all streams
		// in the unlikely event that you open the stream only to stream it to somewhere else we can provide dedicated methods, in all other cases it will be loaded in memory anyway
		// this way we at least have no leaks and the streams are not "read-once" until converted to bytes and autoconversion is easier
		if (!GlueUtils.getVersion().contains(1.0) && returnValue instanceof InputStream) {
			InputStream stream = (InputStream) returnValue;
			try {
				try {
					returnValue = IOUtils.toBytes(IOUtils.wrap(stream));
				}
				finally {
					stream.close();
				}
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		// in version 1 we have to make sure all closeables are added as transactionables
		else if (returnValue instanceof Closeable) {
			ScriptRuntime.getRuntime().addTransactionable(new TransactionalCloseable((Closeable) returnValue));
		}
		else if (returnValue instanceof Future) {
			ScriptRuntime.getRuntime().addFuture((Future<?>) returnValue);
		}
		// process recursively, there could be a stream deep down
		// this code was disabled again cause in a lot of scenario's it doesn't make sense:
		// - glue code itself can only get streams from direct java methods which generally immediately return an inputstream, not as a nested part (so they should be converted already, even if deep in a glue return value)
		// - if you have a java object or even structure or the like and it contains a stream, transforming it to bytes and setting it again will throw an exception or reconvert to a stream respectively
//		else if (returnValue != null) {
//			ContextAccessor accessor = ContextAccessorFactory.getInstance().getAccessor(returnValue.getClass());
//			if (accessor instanceof ListableContextAccessor && accessor instanceof WritableContextAccessor) {
//				Collection<String> keys = ((ListableContextAccessor) accessor).list(returnValue);
//				for (String key : keys) {
//					try {
//						Object value = accessor.get(returnValue, key);
//						if (value != null) {
//							Object newValue = postProcess(value);
//							if (!value.equals(newValue)) {
//								((WritableContextAccessor) accessor).set(returnValue, key, newValue);
//							}
//						}
//					}
//					catch (EvaluationException e) {
//						e.printStackTrace();
//					}
//				}
//			}
//		}
		return returnValue;
	}

	@Override
	public void finish() throws ParseException {
		buildOperation();
	}

	@SuppressWarnings("unchecked")
	private Operation<ExecutionContext> buildOperation() throws ParseException {
		Operation<ExecutionContext> operation = this.operation;
		if (operation == null && !(((List<QueryPart>) getParts()).get(0).getContent() instanceof Operation)) {
			String fullName = (String) ((List<QueryPart>) getParts()).get(0).getContent();
			// let's resolve it initially
			operation = getOperation(fullName);
			// if it is not a namespaced name, we check the imports (if any)
			if (fullName.indexOf('.') < 0) {
				// if we resolved a lambda, we don't check imports though, you might override that with a local lambda (your choice!)
				if (!isDynamic) {
					ScriptRuntime runtime = ScriptRuntime.getRuntime();
					// we need a runtime to resolve imports
					if (runtime != null) {
						Operation<ExecutionContext> explicitMatch = null;
						Operation<ExecutionContext> starredMatch = null;
						List<String> imports = runtime.getImports();
						// we run backwards meaning we take the latest imports firsts, this allows you to "reimport" something
						// note that if you do a starred import _after_ a specific import, the specific one still wins atm
						// so import("math2.sum") followed by import("math.*") will still take the math2.sum one!
						for (int i = imports.size() - 1; i >= 0; i--) {
							String entry = imports.get(i);
							// we found an exact match, reresolve it!
							if (entry.endsWith("." + fullName)) {
								fullName = entry;
								explicitMatch = getOperation(entry);
								// if you did an explicit import and we can't find it, we should throw an exception
								if (explicitMatch == null) {
									throw new ParseException("Can not resolve import: " + entry, 0);
								}
								// otherwise, we stop the lookin'!
								else {
									break;
								}
							}
							// a wildcard import, we can try it...
							else if (starredMatch == null && entry.endsWith(".*")) {
								// strip the star and add the name (leave the dot)
								starredMatch = getOperation(entry.substring(0, entry.length() - 1) + fullName);
							}
						}
						if (explicitMatch != null) {
							operation = explicitMatch;
						}
						else if (starredMatch != null) {
							operation = starredMatch;
						}
						// we attempt a package lookup (if relevant)
						else {
							String namespace = runtime.getScript().getNamespace();
							if (namespace != null) {
								Operation<ExecutionContext> packageMatch = getOperation(namespace + "." + fullName);
								if (packageMatch != null) {
									operation = packageMatch;
								}
							}
						}
					}
				}
			}
			if (operation != null) {
				for (QueryPart part : ((List<QueryPart>) getParts())) {
					operation.add(new QueryPart(part.getType(), part.getContent()));
				}
				operation.finish();
			}
		}
		if (!isDynamic) {
			this.operation = operation;
		}
		return operation;
	}

	protected Operation<ExecutionContext> getOperation(String fullName) {
		for (MethodProvider provider : methodProviders) {
			Operation<ExecutionContext> operation = provider.resolve(fullName);
			if (operation != null) {
				isDynamic = provider instanceof LambdaMethodProvider;
				return operation;
			}
		}
		// if no operations were found, just return a method operation, it will fail later on but at least it will show something
		return null;
	}
	
	@Override
	public OperationType getType() {
		return OperationType.METHOD;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public String toString() {
		Operation<ExecutionContext> operation = this.operation;
		if (operation == null) {
			operation = new MethodOperation<ExecutionContext>();
			for (QueryPart part : ((List<QueryPart>) getParts())) {
				operation.add(new QueryPart(part.getType(), part.getContent()));
			}
		}
		return operation.toString();
	}

	public MethodProvider[] getMethodProviders() {
		return methodProviders;
	}
}
