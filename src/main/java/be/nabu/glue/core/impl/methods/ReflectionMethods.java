package be.nabu.glue.core.impl.methods;

import java.util.ArrayList;
import java.util.List;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.core.api.MethodProvider;
import be.nabu.glue.core.impl.operations.GlueOperationProvider;
import be.nabu.glue.core.impl.parsers.GlueParserProvider;
import be.nabu.glue.utils.ScriptRuntime;
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
