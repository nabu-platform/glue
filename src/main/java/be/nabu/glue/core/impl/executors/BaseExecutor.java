package be.nabu.glue.core.impl.executors;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.core.api.DescribedOperation;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.api.MethodProvider;
import be.nabu.glue.core.impl.operations.GlueOperationProvider;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.PathAnalyzer;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.impl.VariableOperation;

abstract public class BaseExecutor implements Executor {

	private ExecutorContext context;
	private Operation<ExecutionContext> condition, rewritten;
	private UUID uuid;
	private ExecutorGroup parent;
	private OperationProvider<ExecutionContext> operationProvider;

	public BaseExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition) {
		this.parent = parent;
		this.context = context;
		this.condition = condition;
	}

	@Override
	public ExecutorContext getContext() {
		return context;
	}
	
	public String getId() {
		if (uuid == null) {
			uuid = UUID.randomUUID();
		}
		return uuid.toString();
	}

	private Operation<ExecutionContext> getRewrittenCondition() throws ExecutionException {
		// can only rewrite operations if we have a glue operation provider that can give us static method descriptions
		if (rewritten == null && condition != null) {
			synchronized(this) {
				if (rewritten == null) {
					try {
						rewritten = rewrite(condition);
					}
					catch (ParseException e) {
						throw new ExecutionException(e);
					}
				}
			}
		}
		return rewritten;
	}

	@Override
	public boolean shouldExecute(ExecutionContext context) throws ExecutionException {
		if (getContext().getAnnotations().containsKey("disabled")) {
			return false;
		}
		else if (!ScriptRuntime.getRuntime().getFormatter().shouldExecute(this)) {
			return false;
		}
		boolean shouldExecute = true;
		if (getContext().getLabel() != null) {
			shouldExecute = context.getLabelEvaluator() != null
				? context.getLabelEvaluator().shouldExecute(getContext().getLabel(), context.getExecutionEnvironment())
				: getContext().getLabel().equalsIgnoreCase(context.getExecutionEnvironment().getName());
		}
		if (shouldExecute && condition != null) {
			try {
				shouldExecute = (Boolean) getRewrittenCondition().evaluate(context);
			}
			catch (EvaluationException e) {
				throw new ExecutionException(e);
			}
		}
		return shouldExecute;
	}

	@Override
	public ExecutorGroup getParent() {
		return parent;
	}

	public Operation<ExecutionContext> getCondition() {
		return condition;
	}

	public void setParent(ExecutorGroup parent) {
		this.parent = parent;
	}

	public void setCondition(Operation<ExecutionContext> condition) {
		this.condition = condition;
	}
	
	@Override
	public boolean isGenerated() {
		return false;
	}
	
	public OperationProvider<?> getOperationProvider() {
		return operationProvider;
	}

	public void setOperationProvider(OperationProvider<ExecutionContext> operationProvider) {
		this.operationProvider = operationProvider;
	}
	
	public Operation<ExecutionContext> rewrite(Operation<ExecutionContext> operation) throws ParseException {
		return operationProvider instanceof GlueOperationProvider
			? rewrite((GlueOperationProvider) operationProvider, operation)
			: operation;
	}
	
	@SuppressWarnings("unchecked")
	public static Operation<ExecutionContext> rewrite(GlueOperationProvider operationProvider, Operation<ExecutionContext> operation) throws ParseException {
		List<QueryPart> parts = null;
		PathAnalyzer<ExecutionContext> pathAnalyzer = null;
		if (operation.getType() == OperationType.METHOD) {
			String fullName = operation.getParts().get(0).getContent().toString();
			MethodDescription description = null;
			int minimumAmountOfParameters = 0;
			if (operation instanceof DescribedOperation) {
				description = ((DescribedOperation<?>) operation).getMethodDescription();
			}
			// try to get dynamic description
			if (description == null) {
				ScriptRuntime runtime = ScriptRuntime.getRuntime();
				if (runtime != null) {
					Object object = null;
					
					// at this point the full operation could be for example:
					// create()/doSomething()
					// we would have to run the create() to get to the definition of doSomething
					// in the end, we call create() twice: once to resolve doSomething and later on again
					// in the initial phase, we resolve the fullname to the pipeline, assuming it was directly on the pipeline
					// so "doSomething()" would work, but "a/doSomething()" would not
					// now we try at least to resolve recursively if a / is found but we take care not to execute the entire operation
					// this at least allows for a/doSomething()
					// anything more extreme though and we can't solve it at this point
					// we can't use context access as it won't resolve lambdas for us, we _need_ to run the operation, but we _must not_ have unwanted side-effects
					// this means currently we _can not_ do named parameter access to lambdas being accessed complexly:
					// create()/doSomething(a: 1)
					// that will throw an exception because we can't find the definition of doSomething
					// you can however do:
					// create()/doSomething(1)
					// and 
					// a = create()
					// a/doSomething(a: 1)
					if (fullName.contains("/") && !fullName.contains("(") && !fullName.contains("[") && operation.getParts().get(0).getContent() instanceof Operation) {
						try {
							object = ((Operation<ExecutionContext>) operation.getParts().get(0).getContent()).evaluate(runtime.getExecutionContext());
						}
						catch (EvaluationException e) {
							e.printStackTrace();
							throw new RuntimeException("Can not resolve: " + fullName, e);
						}
					}
					else {
						object = runtime.getExecutionContext().getPipeline().get(fullName);
					}
					if (object instanceof Lambda) {
						description = ((Lambda) object).getDescription();
					}
				}
			}
			if (description == null) {
				// we need to find the description that has the most parameters for this method
				// the methods with the same name are assumed to be overloaded versions of one another!
				// depending on the variable calculation, it is possible that fewer parameters are sent along
				for (MethodProvider provider : operationProvider.getMethodProviders()) {
					for (MethodDescription possibleDescription : provider.getAvailableMethods()) {
						if (possibleDescription.getName().equals(fullName) || (possibleDescription.getNamespace() != null && fullName.equals(possibleDescription.getNamespace() + "." + possibleDescription.getName()))) {
							if (description == null || possibleDescription.getParameters().size() > description.getParameters().size()) {
								description = possibleDescription;
							}
							if (possibleDescription.getParameters().size() <= minimumAmountOfParameters) {
								minimumAmountOfParameters = possibleDescription.getParameters().size() - 1;
							}
						}
					}
				}
			}
			else {
				minimumAmountOfParameters = description.getParameters().size() - 1;
			}
			boolean canRewrite = description != null;
			// note the "isNamedParametersAllowed" is from the perspective of the method! If the method supports named parameters on its own, we don't need to resolve them here!
			boolean shouldRewrite = description == null || !description.isNamedParametersAllowed();
			// some methods don't need to be rewritten
			// even if we should rewrite but we can't we need to check, because in that case you should _not_ have a named parameter
			if (shouldRewrite) {
				// we can only rewrite if we have a description
				Map<String, QueryPart> newParts = new LinkedHashMap<String, QueryPart>();
				if (description != null) {
					for (ParameterDescription parameter : description.getParameters()) {
						// two parameters with same name are not allowed!
						if (newParts.containsKey(parameter.getName())) {
							canRewrite = false;
							break;
						}
						// set it to null
						Operation<ExecutionContext> nullOperation = operationProvider.newOperation(OperationType.NATIVE);
						nullOperation.add(new QueryPart(Type.NULL, null));
						newParts.put(parameter.getName(), new QueryPart(Type.OPERATION, nullOperation));
					}
				}
				// for index-based access
				List<String> parameterNames = new ArrayList<String>(newParts.keySet());
				// even if we can't rewrite, we need to check that you don't actually use the syntax in such a case
				// the last set parameter index in case of mingling named and unnamed
				int lastParameterIndex = -1;
				int highestIndex = -1;
				for (int i = 1; i < operation.getParts().size(); i++) {
					boolean isRewritten = false;
					if (operation.getParts().get(i).getContent() instanceof Operation) {
						Operation<ExecutionContext> argumentOperation = (Operation<ExecutionContext>) operation.getParts().get(i).getContent();
						if (argumentOperation.getType() == OperationType.CLASSIC && argumentOperation.getParts().size() == 3 && argumentOperation.getParts().get(1).getType() == Type.NAMING && argumentOperation.getParts().get(1).getContent().equals(":")) {
							if (!canRewrite) {
								throw new ParseException("The method '" + fullName + "' does not allow for named parameters", 0);
							}
							String parameterName = argumentOperation.getParts().get(0).getContent().toString();
							QueryPart valuePart = argumentOperation.getParts().get(2);
							if (!newParts.containsKey(parameterName)) {
								throw new ParseException("The parameter '" + parameterName + "' does not exist in the method description of '" + description.getName() + "' (" + description.getNamespace() + ")", 0);
							}
							if (pathAnalyzer == null) {
								pathAnalyzer = new PathAnalyzer<ExecutionContext>(operationProvider);
							}
							newParts.put(parameterName, valuePart.getType() == Type.OPERATION ? valuePart : new QueryPart(Type.OPERATION, pathAnalyzer.analyze(Arrays.asList(valuePart))));
							lastParameterIndex = parameterNames.indexOf(parameterName);
							isRewritten = true;
						}
					}
					// we did not do a name-based rewrite in the above
					if (canRewrite && !isRewritten) {
						// if you have more parameters than defined, it must be a varargs parameter
						if (lastParameterIndex + 1 >= parameterNames.size()) {
							if (!description.getParameters().get(lastParameterIndex).isVarargs()) {
								throw new ParseException("Too many parameters when calling: " + fullName, 0);
							}
							newParts.put(parameterNames.get(lastParameterIndex) + newParts.size(), operation.getParts().get(i));
							// set the highest index higher!
							highestIndex = newParts.size() - 1; 
						}
						else {
							newParts.put(parameterNames.get(++lastParameterIndex), operation.getParts().get(i));
						}
					}
					if (lastParameterIndex > highestIndex) {
						highestIndex = lastParameterIndex;
					}
				}
				// only replace the parts if we can rewrite
				if (canRewrite) {
					// refresh names, some might have been added dynamically
					parameterNames = new ArrayList<String>(newParts.keySet());
					parts = new ArrayList<QueryPart>();
					parts.add(operation.getParts().get(0).clone());
					// put at least as many parameters in there as the smallest matching method expects
					for (int i = 0; i <= Math.max(highestIndex, minimumAmountOfParameters); i++) {
						parts.add(newParts.get(parameterNames.get(i)));
					}
				}
			}
		}
		if (parts == null) {
			parts = operation.getParts();
		}
		// rewrite children
		Operation<ExecutionContext> clone = operationProvider.newOperation(operation.getType());
		for (int i = 0; i < parts.size(); i++) {
			boolean isAdded = false;
			if (parts.get(i).getContent() instanceof Operation) {
				Operation<ExecutionContext> original = (Operation<ExecutionContext>) parts.get(i).getContent();
				if (original.getType() != OperationType.NATIVE) {
					clone.add(new QueryPart(Type.OPERATION, rewrite(operationProvider, original)));
					isAdded = true;
				}
			}
			if (!isAdded) {
				clone.add(parts.get(i));
			}
		}
		clone.finish();
		return clone;
	}
	
}
