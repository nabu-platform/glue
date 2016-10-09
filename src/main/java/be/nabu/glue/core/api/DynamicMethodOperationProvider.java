package be.nabu.glue.core.api;

import java.util.List;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.libs.evaluator.api.OperationProvider;

public interface DynamicMethodOperationProvider extends OperationProvider<ExecutionContext> {
	public List<MethodProvider> getMethodProviders();
}
