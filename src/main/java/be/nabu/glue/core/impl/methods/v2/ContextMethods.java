package be.nabu.glue.core.impl.methods.v2;

import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "context")
public class ContextMethods {
	public static Object get(String name) {
		return ScriptRuntime.getRuntime().getContext().get(name);
	}
	
	public static Object set(String name, Object value) {
		return ScriptRuntime.getRuntime().getContext().put(name, value);
	}
}
