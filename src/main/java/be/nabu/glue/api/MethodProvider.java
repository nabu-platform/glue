package be.nabu.glue.api;

import java.util.List;

import be.nabu.libs.evaluator.api.Operation;

public interface MethodProvider {
	public Operation<ExecutionContext> resolve(String name);
	public List<MethodDescription> getAvailableMethods();
}
