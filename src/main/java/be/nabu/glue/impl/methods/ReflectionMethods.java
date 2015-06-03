package be.nabu.glue.impl.methods;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "reflection")
public class ReflectionMethods {
	
	public static Script getScript() {
		return ScriptRuntime.getRuntime().getScript();
	}
	
	public static ScriptRepository getRepository() {
		ScriptRepository repository = getScript().getRepository();
		while (repository.getParent() != null) {
			repository = repository.getParent();
		}
		return repository;
	}
	
	public static String typeof(Object object) {
		return object == null ? "null" : object.getClass().getName();
	}
	
	public static boolean instanceOf(Object object, String name) throws ClassNotFoundException {
		return Thread.currentThread().getContextClassLoader().loadClass(name).isAssignableFrom(object.getClass());
	}

}
