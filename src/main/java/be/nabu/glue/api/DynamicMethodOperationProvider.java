package be.nabu.glue.api;

import java.util.List;

import be.nabu.libs.evaluator.api.OperationProvider;

public interface DynamicMethodOperationProvider extends OperationProvider<ExecutionContext> {
	public List<MethodProvider> getMethodProviders();
}
