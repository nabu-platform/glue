package be.nabu.glue.core.api;

import be.nabu.glue.api.MethodDescription;
import be.nabu.libs.evaluator.api.Operation;

public interface DescribedOperation<T> extends Operation<T> {
	public MethodDescription getMethodDescription();
}
