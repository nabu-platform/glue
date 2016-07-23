package be.nabu.glue.impl.methods.v2;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.ScriptUtils;
import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.Lambda;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.api.Script;
import be.nabu.glue.impl.ForkedExecutionContext;
import be.nabu.glue.impl.GlueUtils;
import be.nabu.glue.impl.LambdaImpl;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.impl.GlueUtils.ObjectHandler;
import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.api.ListableContextAccessor;
import be.nabu.libs.evaluator.base.BaseMethodOperation;

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
	
	@GlueMethod(description = "Return the resource as a stream", version = 2)
	public static InputStream resource(@GlueParam(name = "name") String name, @GlueParam(name = "script", defaultValue = "The current script") String script) throws IOException, ParseException {
		if (name == null) {
			return null;
		}
		if (script != null) {
			return ScriptUtils.getRoot(ScriptRuntime.getRuntime().getScript().getRepository()).getScript(script).getResource(name);
		}
		else {
			return ScriptRuntime.getRuntime().getScript().getResource(name);
		}
	}
	
	@GlueMethod(description = "Returns a list of all the resources", version = 2)
	public static List<String> resources(@GlueParam(name = "script", defaultValue = "The current script") String scriptName) throws IOException, ParseException {
		Script script;
		if (scriptName != null) {
			script = ScriptUtils.getRoot(ScriptRuntime.getRuntime().getScript().getRepository()).getScript(scriptName);
		}
		else {
			script = ScriptRuntime.getRuntime().getScript();
		}
		List<String> resources = new ArrayList<String>();
		if (script != null) {
			for (String resource : script) {
				resources.add(resource);
			}
		}
		return resources;
	}
	
	@GlueMethod(description = "Fill in the template with the given series of objects", version = 2)
	public static Object template(final String template, Object...original) throws EvaluationException {
		if (original == null || original.length == 0) {
			return ScriptRuntime.getRuntime().getSubstituter().substitute(template, ScriptRuntime.getRuntime().getExecutionContext(), true);
		}
		return GlueUtils.wrap(new ObjectHandler() {
			@SuppressWarnings("unchecked")
			@Override
			public Object handle(Object single) {
				ExecutionContext parentContext = ScriptRuntime.getRuntime().getExecutionContext();
				Map<String, Object> pipeline;
				if (single instanceof Map) {
					pipeline = (Map<String, Object>) single;
				}
				else if (single instanceof ExecutionContext) {
					pipeline = ((ExecutionContext) single).getPipeline();
				}
				else {
					ContextAccessor<?> accessor = ContextAccessorFactory.getInstance().getAccessor(single.getClass());
					if (accessor instanceof ListableContextAccessor) {
						try {
							pipeline = toKeyValuePairs(single);
						}
						catch (EvaluationException e) {
							throw new RuntimeException(e);
						}
					}
					else {
						pipeline = new HashMap<String, Object>();
						pipeline.put("$value", single);
					}
				}
				ForkedExecutionContext fork = new ForkedExecutionContext(parentContext, pipeline);
				String result = ScriptRuntime.getRuntime().getSubstituter().substitute(template, fork, true);
				ScriptRuntime.getRuntime().setExecutionContext(parentContext);
				return result;
			}
		}, false, original);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Map<String, Object> toKeyValuePairs(Object value) throws EvaluationException {
		Map<String, Object> pipeline = new HashMap<String, Object>();
		pipeline.putAll(ScriptRuntime.getRuntime().getExecutionContext().getPipeline());
		ContextAccessor accessor = ContextAccessorFactory.getInstance().getAccessor(value.getClass());
		if (accessor instanceof ListableContextAccessor) {
			Collection<String> list = ((ListableContextAccessor) accessor).list(value);
			for (String key : list) {
				Object single = accessor.get(value, key);
				pipeline.put(key, single);
			}
		}
		else {
			throw new IllegalArgumentException("The object " + value + " is not context listable");
		}
		return pipeline;
	}
	
	@SuppressWarnings("unchecked")
	@GlueMethod(description = "Create a new lambda that creates a pipeline of the given lambdas", version = 2)
	public static Lambda compose(Object...objects) {
		if (objects == null || objects.length == 0) {
			return null;
		}
		List<Lambda> resolved = (List<Lambda>) SeriesMethods.resolve(GlueUtils.toSeries(objects));
		MethodDescription first = resolved.get(0).getDescription();
		MethodDescription second = resolved.get(resolved.size() - 1).getDescription();
		
		MethodDescription description = new SimpleMethodDescription(first.getNamespace(), first.getName(), first.getDescription(), first.getParameters(), second.getReturnValues());
		return new LambdaImpl(description, new ChainingOperation(resolved), new HashMap<String, Object>());
	}
	
	private static class ChainingOperation extends BaseMethodOperation<ExecutionContext> {

		private List<Lambda> lambdas;

		public ChainingOperation(List<Lambda> lambdas) {
			this.lambdas = lambdas;
		}
		
		@Override
		public void finish() throws ParseException {
			// do nothing
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			ScriptRuntime runtime = ScriptRuntime.getRuntime();
			Map<String, Object> pipeline = new HashMap<String, Object>(context.getPipeline());
			boolean first = true;
			Object result = null;
			for (Lambda lambda : lambdas) {
				List parameters = new ArrayList();
				if (first) {
					for (ParameterDescription description : lambda.getDescription().getParameters()) {
						parameters.add(pipeline.get(description.getName()));
					}
					first = false;
				}
				else {
					parameters.add(result);
				}
				result = GlueUtils.calculate(lambda, runtime, parameters);
			}
			return result;
		}
		
	}
}
