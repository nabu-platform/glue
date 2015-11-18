package be.nabu.glue.impl;

import java.util.Map;

import be.nabu.glue.api.EnclosedLambda;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.libs.evaluator.api.Operation;

public class LambdaImpl implements EnclosedLambda {

	private MethodDescription description;
	private Operation<ExecutionContext> operation;
	private Map<String, Object> context;

	public LambdaImpl(MethodDescription description, Operation<ExecutionContext> operation, Map<String, Object> context) {
		this.description = description;
		this.operation = operation;
		this.context = context;
	}
	
	@Override
	public Operation<ExecutionContext> getOperation() {
		return operation;
	}

	@Override
	public MethodDescription getDescription() {
		return description;
	}

	@Override
	public Map<String, Object> getEnclosedContext() {
		return context;
	}

}
