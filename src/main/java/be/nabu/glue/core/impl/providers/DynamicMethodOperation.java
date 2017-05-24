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
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.api.MethodProvider;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.LambdaMethodProvider;
import be.nabu.glue.impl.ForkedExecutionContext;
import be.nabu.glue.impl.TransactionalCloseable;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseOperation;
import be.nabu.libs.evaluator.impl.MethodOperation;
import be.nabu.utils.io.IOUtils;

@SuppressWarnings("rawtypes")
public class DynamicMethodOperation extends BaseOperation {

	private Operation<ExecutionContext> operation;

	private MethodProvider [] methodProviders;
	
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
					throw new RuntimeException("Only execution context and map are supported atm");
				}
				return postProcess(operation.evaluate(executionContext));
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
				throw new EvaluationException("Could not resolve a lambda: " + ((List<QueryPart>) getParts()).get(0).getContent());
			}
			else {
				throw new EvaluationException("Could not resolve a method with the name: " + ((List<QueryPart>) getParts()).get(0).getContent());
			}
		}
		catch (ParseException e) {
			throw new EvaluationException(e);
		}
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
			operation = getOperation(fullName);
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
