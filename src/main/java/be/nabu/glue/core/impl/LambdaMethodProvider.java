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

package be.nabu.glue.core.impl;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.core.api.DescribedOperation;
import be.nabu.glue.core.api.EnclosedLambda;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.api.MethodProvider;
import be.nabu.glue.impl.ForkedExecutionContext;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseMethodOperation;
import be.nabu.libs.evaluator.impl.NativeOperation;
import be.nabu.libs.evaluator.impl.VariableOperation;
import be.nabu.libs.metrics.api.MetricInstance;
import be.nabu.libs.metrics.api.MetricProvider;
import be.nabu.libs.metrics.api.MetricTimer;

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

		public static final String METRIC_EXECUTION_TIME = "lambdaExecutionTime";
		
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
				throw new EvaluationException("Too many parameters for lambda: " + (getParts().size() - 1) + "/" + description.getParameters().size());
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
					else if (value != null && parameterDescription.isList() && !(value instanceof Iterable)) {
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
			MetricInstance metrics = null;
			if (previousContext instanceof MetricProvider) {
				metrics = ((MetricProvider) previousContext).getMetricInstance((description.getNamespace() != null ? description.getNamespace() + "." : "") + description.getName());
			}
			MetricTimer timer = metrics != null ? metrics.start(METRIC_EXECUTION_TIME) : null;
			VariableOperation.registerRoot();
			try {
				return operation.evaluate(forkedContext); 
			}
			finally {
				if (timer != null) {
					timer.stop();
				}
				ScriptRuntime.getRuntime().setExecutionContext(previousContext);
				VariableOperation.unregisterRoot();
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
