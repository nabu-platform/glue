package be.nabu.glue.impl.providers;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.MethodProvider;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.api.StaticMethodFactory;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.impl.SimpleParameterDescription;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.impl.MethodOperation;

public class StaticJavaMethodProvider implements MethodProvider {
	
	private Class<?> [] methodClasses;
	
	public StaticJavaMethodProvider() {
		this(new Class[0]);
	}
	
	public StaticJavaMethodProvider(Class<?>...methodClasses) {
		this.methodClasses = methodClasses;
		// if you passed in no method classes, use SPI to find factories
		if (this.methodClasses.length == 0) {
			List<Class<?>> allClasses = new ArrayList<Class<?>>();
			for (StaticMethodFactory staticMethodFactory : ServiceLoader.load(StaticMethodFactory.class)) {
				allClasses.addAll(staticMethodFactory.getStaticMethodClasses());
			}
			this.methodClasses = allClasses.toArray(new Class[0]);
		}
	}

	@Override
	public Operation<ExecutionContext> resolve(String name) {
		MethodOperation<ExecutionContext> methodOperation = new MethodOperation<ExecutionContext>(methodClasses);
		try {
			if (methodOperation.getMethod(name) != null) {
				return methodOperation;
			}
		}
		catch (ClassNotFoundException e) {
			// ignore this, you might be referencing a namespaced script or something
		}
		return null;
	}

	@Override
	public List<MethodDescription> getAvailableMethods() {
		List<MethodDescription> descriptions = new ArrayList<MethodDescription>();
		for (Class<?> methodClass : methodClasses) {
			for (Method method : methodClass.getDeclaredMethods()) {
				if (Modifier.isStatic(method.getModifiers())) {
					List<ParameterDescription> parameters = new ArrayList<ParameterDescription>();
					int i = 0;
					for (Class<?> parameter : method.getParameterTypes()) {
						parameters.add(new SimpleParameterDescription("arg" + i++, null, parameter.isArray() ? parameter.getComponentType().getSimpleName() + "[]" : parameter.getSimpleName()));
					}
					descriptions.add(new SimpleMethodDescription(method.getName(), null, parameters.toArray(new ParameterDescription[0])));
				}
			}
		}
		return descriptions;
	}
	
}