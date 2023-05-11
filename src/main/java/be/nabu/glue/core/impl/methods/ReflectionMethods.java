package be.nabu.glue.core.impl.methods;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.core.api.MethodProvider;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.operations.GlueOperationProvider;
import be.nabu.glue.core.impl.parsers.GlueParserProvider;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "reflection")
public class ReflectionMethods {
	
	@GlueMethod(version = 1)
	public static Script getScript() {
		return ScriptRuntime.getRuntime().getScript();
	}
	
	@GlueMethod(version = 2)
	public static Script script() {
		return getScript();
	}
	
	@GlueMethod(version = 1)
	public static ScriptRepository getRepository() {
		ScriptRepository repository = getScript().getRepository();
		while (repository.getParent() != null) {
			repository = repository.getParent();
		}
		return repository;
	}
	
	@GlueMethod(version = 2)
	public static ScriptRepository repository() {
		return getRepository();
	}
	
	public static String typeof(Object object) {
		return object == null ? "null" : object.getClass().getName();
	}
	
	public static String identity(Object object) {
		return object == null ? null : Integer.toString(object.hashCode());
	}
	
	public static boolean instanceOf(Object object, String name) throws ClassNotFoundException {
		return Thread.currentThread().getContextClassLoader().loadClass(name).isAssignableFrom(object.getClass());
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Object newInstance(String className, Object...parameters) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
		List list = new ArrayList();
		if (parameters != null && parameters.length > 0) {
			for (Object parameter : GlueUtils.toSeries(parameters)) {
				list.add(parameter);
			}
		}
		List parametersToUse = new ArrayList();
		Constructor<?> constructorToUse = null;
		for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
			// we assume this is it!
			if (constructor.getParameterCount() == list.size()) {
				for (int i = 0; i < constructor.getParameterTypes().length; i++) {
					Class<?> targetType = constructor.getParameterTypes()[i];
					if (list.get(i) == null || targetType.isAssignableFrom(list.get(i).getClass())) {
						parametersToUse.add(list.get(i));
					}
					else { 
						Object converted = ConverterFactory.getInstance().getConverter().convert(list.get(i), targetType); 
						if (converted != null) {
							parametersToUse.add(converted);
						}
					}
				}
				constructorToUse = constructor;
				break;
			}
		}
		if (constructorToUse == null) {
			throw new IllegalArgumentException("Can not find correct constructor for class '" + clazz.getName() + "' with parameters: " + list);
		}
		return constructorToUse.newInstance(parametersToUse.toArray());
	}

	@GlueMethod(version = 2)
	public static MethodDescription method(String name) {
		return getMethodDescription(name);
	}
	
	@GlueMethod(version = 1)
	public static MethodDescription getMethodDescription(String name) {
		GlueOperationProvider newOperationProvider = new GlueParserProvider().newOperationProvider(getRepository());
		MethodDescription nameOnlyMatch = null;
		for (MethodProvider methodProvider : newOperationProvider.getMethodProviders()) {
			for (MethodDescription description : methodProvider.getAvailableMethods()) {
				String fullName = (description.getNamespace() == null ? "" : description.getNamespace() + ".") + description.getName();
				if (name.equals(fullName)) {
					return description;
				}
				else if (nameOnlyMatch == null && name.equals(description.getName())) {
					nameOnlyMatch = description;
				}
			}
		}
		return nameOnlyMatch;
	}
	
	@GlueMethod(version = 1)
	public static MethodDescription [] getMethodDescriptions() {
		GlueOperationProvider newOperationProvider = new GlueParserProvider().newOperationProvider(getRepository());
		List<MethodDescription> descriptions = new ArrayList<MethodDescription>();
		for (MethodProvider methodProvider : newOperationProvider.getMethodProviders()) {
			descriptions.addAll(methodProvider.getAvailableMethods());
		}
		return descriptions.toArray(new MethodDescription[descriptions.size()]);
	}
	
	@GlueMethod(version = 2)
	public static MethodDescription [] methods() {
		return getMethodDescriptions();
	}
}
