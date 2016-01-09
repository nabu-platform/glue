package be.nabu.glue.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.MethodProvider;
import be.nabu.libs.evaluator.api.Operation;

public class SPIMethodProvider implements MethodProvider {

	private List<MethodProvider> methodProviders;

	@Override
	public Operation<ExecutionContext> resolve(String name) {
		for (MethodProvider provider : getMethodProviders()) {
			Operation<ExecutionContext> operation = provider.resolve(name);
			if (operation != null) {
				return operation;
			}
		}
		return null;
	}
	
	private List<MethodProvider> getMethodProviders() {
		if (methodProviders == null) {
			synchronized(this) {
				if (methodProviders == null) {
					List<MethodProvider> methodProviders = new ArrayList<MethodProvider>();
					for (MethodProvider provider : ServiceLoader.load(MethodProvider.class)) {
						methodProviders.add(provider);
					}
					this.methodProviders = methodProviders;
				}
			}
		}
		return methodProviders;
	}

	@Override
	public List<MethodDescription> getAvailableMethods() {
		List<MethodDescription> methods = new ArrayList<MethodDescription>();
		for (MethodProvider methodProvider : getMethodProviders()) {
			methods.addAll(methodProvider.getAvailableMethods());
		}
		return methods;
	}
}