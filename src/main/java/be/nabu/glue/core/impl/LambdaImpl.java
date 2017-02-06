package be.nabu.glue.core.impl;

import java.util.Map;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.core.api.EnclosedLambda;
import be.nabu.libs.evaluator.api.Operation;

public class LambdaImpl implements EnclosedLambda {

	private MethodDescription description;
	private Operation<ExecutionContext> operation;
	private Map<String, Object> context;
	private boolean mutable;
	private Operation<ExecutionContext> originalOperation;

	public LambdaImpl(MethodDescription description, Operation<ExecutionContext> operation, Map<String, Object> context) {
		this(description, operation, context, false);
	}
	
	public LambdaImpl(MethodDescription description, Operation<ExecutionContext> operation, Map<String, Object> context, boolean mutable) {
		this.description = description;
		this.originalOperation = operation;
		this.operation = operation;
		this.context = context;
		this.mutable = mutable;
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

	@Override
	public String toString() {
		return this.operation.toString();
	}

	@Override
	public boolean isMutable() {
		return mutable;
	}

	public Map<String, Object> getContext() {
		return context;
	}

	public void setContext(Map<String, Object> context) {
		this.context = context;
	}

	public void setDescription(MethodDescription description) {
		this.description = description;
	}

	public void setOperation(Operation<ExecutionContext> operation) {
		this.operation = operation;
	}

	public void setMutable(boolean mutable) {
		this.mutable = mutable;
	}
	
	public Operation<ExecutionContext> getOriginal() {
		return originalOperation;
	}
}
