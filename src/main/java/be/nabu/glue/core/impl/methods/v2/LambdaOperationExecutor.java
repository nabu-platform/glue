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

package be.nabu.glue.core.impl.methods.v2;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.core.api.EnclosedLambda;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.impl.LambdaImpl;
import be.nabu.glue.core.impl.LambdaMethodProvider.LambdaExecutionOperation;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.impl.SimpleParameterDescription;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.operations.OperationExecutor;
import be.nabu.libs.evaluator.impl.ClassicOperation;
import be.nabu.libs.evaluator.impl.NativeOperation;
import be.nabu.libs.evaluator.impl.VariableOperation;

public class LambdaOperationExecutor implements OperationExecutor {

	@Override
	public boolean support(Object leftOperand, Type operator, Object rightOperand) {
		return (leftOperand instanceof Lambda || rightOperand instanceof Lambda) && operator != QueryPart.Type.IN && operator != QueryPart.Type.NOT_IN
				&& operator != QueryPart.Type.EQUALS && operator != QueryPart.Type.NOT_EQUALS && operator != QueryPart.Type.NOT;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Object calculate(Object leftOperand, Type operator, Object rightOperand) {
		switch(operator) {
			case COMPOSE: return ScriptMethods.compose(rightOperand, leftOperand);
			default: 
				ScriptRuntime runtime = ScriptRuntime.getRuntime();
				Map pipeline = new HashMap();
				if (runtime != null) {
					pipeline.putAll(runtime.getExecutionContext().getPipeline());
				}
				ClassicOperation<ExecutionContext> classic = new ClassicOperation<ExecutionContext>();
				classic.getParts().add(toOperation(leftOperand));
				classic.getParts().add(new QueryPart(operator, operator.toString()));
				classic.getParts().add(toOperation(rightOperand));
				MethodDescription description = new SimpleMethodDescription("lambda", "anonymous", "Lambda Operation", 
						Arrays.asList(new ParameterDescription [] { new SimpleParameterDescription("x", "The value", "object") }), 
						Arrays.asList(new ParameterDescription [] { new SimpleParameterDescription("result", "The result", "object") }));
				return new LambdaImpl(description, classic, pipeline);
		}
	}

	@SuppressWarnings("rawtypes")
	private QueryPart toOperation(Object leftOperand) {
		if (leftOperand instanceof Lambda) {
			Lambda lambda = (Lambda) leftOperand;
			LambdaExecutionOperation operation = new LambdaExecutionOperation(lambda.getDescription(), lambda.getOperation(), 
					lambda instanceof EnclosedLambda ? ((EnclosedLambda) lambda).getEnclosedContext() : new HashMap<String, Object>());
			operation.getParts().add(new QueryPart(Type.STRING, "anonymous"));
			VariableOperation<ExecutionContext> variableOperation = new VariableOperation<ExecutionContext>();
			variableOperation.add(new QueryPart(Type.VARIABLE, "x"));
			operation.getParts().add(new QueryPart(Type.OPERATION, variableOperation));
			return new QueryPart(Type.OPERATION, operation);
		}
		else {
			NativeOperation<?> operation = new NativeOperation();
			operation.add(new QueryPart(Type.UNKNOWN, leftOperand));
			return new QueryPart(Type.OPERATION, operation);
		}
	}
	
}
