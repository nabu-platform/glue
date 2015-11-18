package be.nabu.glue.api;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.libs.evaluator.api.Operation;

public interface Lambda {
	public Operation<ExecutionContext> getOperation();
	public MethodDescription getDescription();
}
