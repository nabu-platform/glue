package be.nabu.glue.impl.providers;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
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
	private List<MethodDescription> descriptions;
	
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
			if (methodOperation.findMethod(name) != null) {
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
		if (descriptions == null) {
			synchronized(this) {
				if (descriptions == null) {
					List<MethodDescription> descriptions = new ArrayList<MethodDescription>();
					for (Class<?> methodClass : methodClasses) {
						for (Method method : methodClass.getDeclaredMethods()) {
							if (Modifier.isStatic(method.getModifiers())) {
								GlueMethod methodAnnotation = method.getAnnotation(GlueMethod.class);
								List<ParameterDescription> parameters = new ArrayList<ParameterDescription>();
								Annotation[][] parameterAnnotations = method.getParameterAnnotations();
								Class<?>[] parameterTypes = method.getParameterTypes();
								for (int i = 0; i < parameterTypes.length; i++) {
									String parameterName = null;
									String parameterDescription = null;
									String parameterDefaultValue = null;
									Class<?> parameter = parameterTypes[i];
									for (int j = 0; j < parameterAnnotations[i].length; j++) {
										if (parameterAnnotations[i][j] instanceof GlueParam) {
											parameterName = ((GlueParam) parameterAnnotations[i][j]).name();
											parameterDescription = ((GlueParam) parameterAnnotations[i][j]).description();
											parameterDefaultValue = ((GlueParam) parameterAnnotations[i][j]).defaultValue();
											break;
										}
									}
									if (parameterName == null) {
										parameterName = "arg" + i;
									}
									boolean isVarargs = i == parameterTypes.length - 1 && parameter.isArray();
									if (Enum.class.isAssignableFrom(parameter)) {
										parameters.add(new SimpleParameterDescription(parameterName, parameterDescription, parameter.isArray() ? parameter.getComponentType().getName() : parameter.getName(), isVarargs, parameter.getEnumConstants())
												.setDefaultValue(parameterDefaultValue)
												.setList(parameter.isArray()));
									}
									else {
										parameters.add(new SimpleParameterDescription(parameterName, parameterDescription, 
											parameter.isArray() ? parameter.getComponentType().getSimpleName() : parameter.getName(), isVarargs)
												.setDefaultValue(parameterDefaultValue == null || parameterDefaultValue.isEmpty() ? null : parameterDefaultValue)
												.setList(parameter.isArray()));
									}
								}
								List<ParameterDescription> returnValues = new ArrayList<ParameterDescription>();
								if (!Void.class.isAssignableFrom(method.getReturnType())) {
									returnValues.add(new SimpleParameterDescription(null, methodAnnotation == null ? null : methodAnnotation.returns(), method.getReturnType().isArray() ? method.getReturnType().getComponentType().getName() : method.getReturnType().getName(), false)
											.setList(method.getReturnType().isArray()));
								}
								descriptions.add(new SimpleMethodDescription(
										method.getDeclaringClass().getName(), 
										method.getName(), 
										methodAnnotation == null ? null : methodAnnotation.description(), 
												parameters, 
												returnValues)
										);
							}
						}
					}
					this.descriptions = descriptions;
				}
			}
		}
		return descriptions;
	}
	
}