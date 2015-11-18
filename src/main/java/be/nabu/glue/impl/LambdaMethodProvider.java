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
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.base.BaseMethodOperation;

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
		
		@SuppressWarnings("unchecked")
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			ForkedExecutionContext forkedContext = new ForkedExecutionContext(context, true);
			forkedContext.getPipeline().putAll(enclosedContext);
			if (getParts().size() - 1 > description.getParameters().size()) {
				throw new EvaluationException("Too many parameters for lambda");
			}
			for (int i = 1; i < getParts().size(); i++) {
				Operation<ExecutionContext> argumentOperation = ((Operation<ExecutionContext>) getParts().get(i).getContent());
				Object value = argumentOperation.evaluate(context);
				if (value == null) {
					value = description.getParameters().get(i - 1).getDefaultValue();
				}
				forkedContext.getPipeline().put(description.getParameters().get(i - 1).getName(), value);
			}
			ScriptRuntime.getRuntime().setExecutionContext(forkedContext);
			Object result = operation.evaluate(forkedContext);
			ScriptRuntime.getRuntime().setExecutionContext(context);
			return result;
		}

		@Override
		public void finish() throws ParseException {
			// do nothing
		}

		@Override
		public MethodDescription getMethodDescription() {
			return description;
		}
		
	}
	
}
