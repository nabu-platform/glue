package be.nabu.glue.impl.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.glue.api.DynamicMethodOperationProvider;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodProvider;
import be.nabu.glue.impl.providers.DynamicMethodOperation;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.impl.ClassicOperation;
import be.nabu.libs.evaluator.impl.NativeOperation;

public class GlueOperationProvider implements DynamicMethodOperationProvider {
	
	private List<MethodProvider> methodProviders = new ArrayList<MethodProvider>();

	public GlueOperationProvider(MethodProvider...methodProviders) {
		this.methodProviders.addAll(Arrays.asList(methodProviders));
	}
	
	@Override
	public Operation<ExecutionContext> newOperation(OperationType type) {
		switch (type) {
			case CLASSIC:
				return new ClassicOperation<ExecutionContext>();
			case METHOD:
				return new DynamicMethodOperation(methodProviders.toArray(new MethodProvider[0]));
			case VARIABLE:
				return new ScriptVariableOperation<ExecutionContext>();
			case NATIVE:
				return new NativeOperation<ExecutionContext>();
		}
		throw new RuntimeException("Unknown operation type: " + type);
	}

	@Override
	public List<MethodProvider> getMethodProviders() {
		return methodProviders;
	}
}
