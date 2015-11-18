package be.nabu.glue.api;

import be.nabu.libs.evaluator.api.Operation;

public interface DescribedOperation<T> extends Operation<T> {
	public MethodDescription getMethodDescription();
}
