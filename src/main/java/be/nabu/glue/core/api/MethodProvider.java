package be.nabu.glue.core.api;

import java.util.List;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.libs.evaluator.api.Operation;

public interface MethodProvider {
	public Operation<ExecutionContext> resolve(String name);
	public List<MethodDescription> getAvailableMethods();
}
