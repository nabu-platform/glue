package be.nabu.glue.core.impl.providers;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.core.api.MethodProvider;
import be.nabu.glue.core.api.StaticMethodFactory;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.impl.SimpleParameterDescription;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.impl.MethodOperation;
import be.nabu.libs.evaluator.impl.MethodOperation.MethodFilter;

public class StaticJavaMethodProvider implements MethodProvider {
	
	private Collection<Class<?>> methodClasses;
	private List<MethodDescription> descriptions;
	private boolean includeDeprecated = Boolean.parseBoolean(System.getProperty("include.deprecated", "false"));
	private Object context;
	
	public StaticJavaMethodProvider() {
		// auto construct
	}
	
	public StaticJavaMethodProvider(Class<?>...methodClasses) {
		if (methodClasses != null && methodClasses.length > 0) {
			this.methodClasses = Arrays.asList(methodClasses);
		}
	}
	
	public StaticJavaMethodProvider(Object context) {
		if (context instanceof Class) {
			this.methodClasses = Arrays.asList(new Class<?>[] { (Class<?>) context });	
		}
		else {
			this.methodClasses = Arrays.asList(new Class<?>[] { context.getClass() });
			this.context = context;
		}
	}

	Collection<Class<?>> getMethodClasses() {
		// if you passed in no method classes, use SPI to find factories
		if (methodClasses == null) {
			synchronized(this) {
				if (methodClasses == null) {
					if (context != null) {
						this.methodClasses = Arrays.asList(new Class<?>[] { context.getClass() });
					}
					else {
						List<Class<?>> allClasses = new ArrayList<Class<?>>();
						for (StaticMethodFactory staticMethodFactory : ServiceLoader.load(StaticMethodFactory.class)) {
							allClasses.addAll(staticMethodFactory.getStaticMethodClasses());
						}
						this.methodClasses = allClasses;
					}
				}
			}
		}
		return methodClasses;
	}

	@Override
	public Operation<ExecutionContext> resolve(String name) {
		MethodOperation<ExecutionContext> methodOperation = new MethodOperation<ExecutionContext>(getMethodClasses());
		methodOperation.setContext(context);
		methodOperation.setMethodFilter(new MethodFilter() {
			@Override
			public boolean isAllowed(Method method) {
				GlueMethod methodAnnotation = method.getAnnotation(GlueMethod.class);
				Double version = methodAnnotation == null ? null : methodAnnotation.version();
				MethodProviderClass annotation = method.getDeclaringClass().getAnnotation(MethodProviderClass.class);
				String namespace = annotation == null || annotation.namespace() == null || annotation.namespace().isEmpty() ? method.getDeclaringClass().getName() : annotation.namespace();
				GlueUtils.VersionRange range = GlueUtils.getVersion(namespace, method.getName());
				return range == null || range.contains(version);
			}
		});
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
					for (Class<?> methodClass : getMethodClasses()) {
						for (Method method : methodClass.getDeclaredMethods()) {
							if ((context != null || Modifier.isStatic(method.getModifiers())) && Modifier.isPublic(method.getModifiers())) {
								Deprecated deprecatedAnnotation = method.getAnnotation(Deprecated.class);
								// ignore deprecated methods unless specifically requested
								if (deprecatedAnnotation != null && !includeDeprecated) {
									continue;
								}
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
									boolean isVarargs = i == parameterTypes.length - 1 && (parameter.isArray() || method.isVarArgs());
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
								MethodProviderClass annotation = method.getDeclaringClass().getAnnotation(MethodProviderClass.class);
								String namespace = annotation == null || annotation.namespace() == null || annotation.namespace().isEmpty() ? method.getDeclaringClass().getName() : annotation.namespace(); 

								Double version = methodAnnotation == null ? null : methodAnnotation.version();
								GlueUtils.VersionRange range = GlueUtils.getVersion(namespace, method.getName());
								if (range == null || range.contains(version)) {
									descriptions.add(new SimpleMethodDescription(
											namespace, 
											method.getName(), 
											methodAnnotation == null ? null : methodAnnotation.description(), 
													parameters, 
													returnValues,
													false,
													version)
											);
								}
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