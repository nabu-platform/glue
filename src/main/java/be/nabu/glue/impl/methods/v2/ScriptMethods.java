package be.nabu.glue.impl.methods.v2;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.impl.GlueUtils;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "script")
public class ScriptMethods {

	@GlueMethod(description = "Write content to the standard output", version = 2)
	public static void echo(Object...original) {
		for (Object object : GlueUtils.toSeries(original)) {
			ScriptRuntime.getRuntime().getFormatter().print(GlueUtils.convert(object, String.class));
		}
	}
	
	@GlueMethod(description = "Write content to the console", version = 2)
	public static void console(Object...original) {
		for (Object object : GlueUtils.toSeries(original)) {
			System.out.println(GlueUtils.convert(object, String.class));
		}
	}
	
	@GlueMethod(description = "Write content to the console", version = 2)
	public static void sleep(long amount) throws InterruptedException {
		Thread.sleep(amount);
	}
}
