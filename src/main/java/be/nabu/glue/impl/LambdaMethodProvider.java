package be.nabu.glue.impl;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.EnclosedLambda;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.Lambda;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.MethodProvider;
import be.nabu.glue.api.DescribedOperation;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseMethodOperation;
import be.nabu.libs.evaluator.impl.NativeOperation;

public class LambdaMethodProvider implements MethodProvider {

	@Override
	public Operation<ExecutionContext> resolve(String name) {
		Map<String, Lambda> lambdasInScope = getLambdasInScope();
		if (lambdasInScope.containsKey(name)) {
			Lambda lambda = lambdasInScope.get(name);
			return new LambdaExecutionOperation(lambda.getDescription(), lambda.getOperation(), 
				lambda instanceof EnclosedLambda ? ((EnclosedLambda) lambda).getEnclosedContext() : new HashMap<String, Object>());
		}
		return null;
	}

	@Override
	public List<MethodDescription> getAvailableMethods() {
		Map<String, Lambda> lambdasInScope = getLambdasInScope();
		List<MethodDescription> descriptions = new ArrayList<MethodDescription>();
		for (String name : lambdasInScope.keySet()) {
			MethodDescription description = lambdasInScope.get(name).getDescription();
			if (description != null) {
				descriptions.add(description);
			}
		}
		return descriptions;
	}

	private Map<String, Lambda> getLambdasInScope() {
		Map<String, Lambda> methods = new HashMap<String, Lambda>();
		if (ScriptRuntime.getRuntime() != null) {
			Map<String, Object> pipeline = ScriptRuntime.getRuntime().getExecutionContext().getPipeline();
			for (String key : pipeline.keySet()) {
				if (pipeline.get(key) instanceof Lambda) {
					methods.put(key, (Lambda) pipeline.get(key));
				}
			}
		}
		return methods;
	}
	
	/**
	 * All lambda operations are wrapped in this operation which does the input variable mapping
	 */
	public static class LambdaExecutionOperation extends BaseMethodOperation<ExecutionContext> implements DescribedOperation<ExecutionContext> {

		private Operation<ExecutionContext> operation;
		private Map<String, Object> enclosedContext;
		private MethodDescription description;

		public LambdaExecutionOperation(MethodDescription description, Operation<ExecutionContext> operation, Map<String, Object> enclosedContext) {
			this.description = description;
			this.operation = operation;
			this.enclosedContext = enclosedContext;
		}
		
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			ForkedExecutionContext forkedContext = new ForkedExecutionContext(context, new HashMap<String, Object>());
			forkedContext.getPipeline().putAll(enclosedContext);
			if (getParts().size() - 1 > description.getParameters().size() && (description.getParameters().isEmpty() || !description.getParameters().get(description.getParameters().size() - 1).isList())) {
				throw new EvaluationException("Too many parameters for lambda");
			}
			boolean wasOriginalList = false;
			for (int i = 1; i < getParts().size(); i++) {
				Operation<ExecutionContext> argumentOperation = ((Operation<ExecutionContext>) getParts().get(i).getContent());
				// named parameters don't get to this point except if the lambda explicitly allowed them, at this point we want to rewrite correctly
				// this is harder to combine with varargs etc...
				if (argumentOperation.getType() == OperationType.CLASSIC && argumentOperation.getParts().size() == 3 && argumentOperation.getParts().get(1).getType() == Type.NAMING && argumentOperation.getParts().get(1).getContent().equals(":")) {
					String name = argumentOperation.getParts().get(0).getContent().toString();
					Object value = argumentOperation.getParts().get(2).getType() == QueryPart.Type.OPERATION 
						? ((Operation<ExecutionContext>) argumentOperation.getParts().get(2).getContent()).evaluate(context)
						: argumentOperation.getParts().get(2).getContent();
					boolean found = false;
					for (ParameterDescription parameter : description.getParameters()) {
						if (parameter.getName().equals(name)) {
							found = true;
							break;
						}
					}
					if (!found) {
						throw new EvaluationException("Unknown parameter found: " + name);
					}
					forkedContext.getPipeline().put(name, value);
				}
				else {
					Object value = argumentOperation.evaluate(context);
					if (value == null) {
						value = description.getParameters().get(i - 1).getDefaultValue();
					}
					ParameterDescription parameterDescription = description.getParameters().get(i > description.getParameters().size() ? description.getParameters().size() - 1 : i - 1);
					if (i > description.getParameters().size()) {
						Object object = forkedContext.getPipeline().get(parameterDescription.getName());
						if (i == description.getParameters().size() + 1 && wasOriginalList) {
							List list = new ArrayList();
							list.add(object);
							list.add(value);
							value = list;
						}
						else {
							((List) object).add(value);
							value = object;
						}
					}
					else if (parameterDescription.isList() && !(value instanceof Iterable)) {
						List list = new ArrayList();
						list.add(value);
						value = list;
					}
					else if (value instanceof Iterable) {
						wasOriginalList = i == description.getParameters().size();
					}
					forkedContext.getPipeline().put(parameterDescription.getName(), value);
				}
			}
			ExecutionContext previousContext = ScriptRuntime.getRuntime().getExecutionContext();
			ScriptRuntime.getRuntime().setExecutionContext(forkedContext);
			try {
				return operation.evaluate(forkedContext); 
			}
			finally {
				ScriptRuntime.getRuntime().setExecutionContext(previousContext);
			}
		}

		@Override
		public void finish() throws ParseException {
			// do nothing
		}

		@Override
		public MethodDescription getMethodDescription() {
			return description;
		}
		
		public Operation<ExecutionContext> getOperation() {
			return operation;
		}

		public Map<String, Object> getEnclosedContext() {
			return enclosedContext;
		}

		@SuppressWarnings("rawtypes")
		public Object evaluateWithParameters(ExecutionContext context, Object...parameters) throws EvaluationException {
			LambdaExecutionOperation lambda = new LambdaExecutionOperation(getMethodDescription(), operation, enclosedContext);
			lambda.getParts().add(new QueryPart(Type.STRING, "anonymous"));
			for (int i = 0; i < parameters.length; i++) {
				NativeOperation<?> operation = new NativeOperation();
				operation.add(new QueryPart(Type.UNKNOWN, parameters[i]));
				lambda.getParts().add(new QueryPart(Type.OPERATION, operation));
			}
			return lambda.evaluate(context);
		}
		
	}
	
}
