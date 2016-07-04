package be.nabu.glue.impl.methods.v2;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.impl.GlueUtils;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "script")
public class ScriptMethods {

	@GlueMethod(description = "Write content to the standard output", version = 2)
	public static void echo(Object...original) {
		if (original != null) {
			for (Object object : original) {
				if (object instanceof Iterable) {
					object = SeriesMethods.resolve((Iterable<?>) object);
				}
				else if (object instanceof ExecutionContext) {
					object = ((ExecutionContext) object).getPipeline();
				}
				ScriptRuntime.getRuntime().getFormatter().print(GlueUtils.convert(object, String.class));
			}
		}
	}
	
	@GlueMethod(description = "Write content to the console", version = 2)
	public static void console(Object...original) {
		if (original == null) {
			System.out.println("null");
		}
		else {
			for (Object object : original) {
				if (object instanceof Iterable) {
					object = SeriesMethods.resolve((Iterable<?>) object);
				}
				System.out.println(GlueUtils.convert(object, String.class));
			}
		}
	}
	
	@GlueMethod(description = "Write content to the console", version = 2)
	public static void sleep(long amount) throws InterruptedException {
		Thread.sleep(amount);
	}
	
	@GlueMethod(description = "Return the root cause of the exception", version = 2)
	public static Throwable cause(Throwable exception) {
		while (exception.getCause() != null) {
			exception = exception.getCause();
		}
		return exception;
	}
}
