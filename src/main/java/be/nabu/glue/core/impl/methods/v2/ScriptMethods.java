package be.nabu.glue.core.impl.methods.v2;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.api.ParserProvider;
import be.nabu.glue.api.Script;
import be.nabu.glue.core.api.EnclosedLambda;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.api.MethodProvider;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.LambdaImpl;
import be.nabu.glue.core.impl.GlueUtils.ObjectHandler;
import be.nabu.glue.core.impl.LambdaMethodProvider.LambdaExecutionOperation;
import be.nabu.glue.core.impl.operations.GlueOperationProvider;
import be.nabu.glue.core.impl.parsers.GlueParserProvider;
import be.nabu.glue.core.impl.providers.ScriptMethodProvider.DecoratorOperation;
import be.nabu.glue.impl.ForkedExecutionContext;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.glue.utils.ScriptUtils;
import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.api.ListableContextAccessor;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseMethodOperation;

@MethodProviderClass(namespace = "script")
public class ScriptMethods {

	@GlueMethod(description = "Write content to the standard output", version = 2)
	public static void echo(Object...original) {
		if (original != null && original.length > 0) {
			boolean isIterable = original.length == 1 && original[0] instanceof Iterable;
			List<?> resolved = SeriesMethods.resolve(GlueUtils.toSeries(original));
			for (Object object : resolved) {
				// only recursively resolve if the parent is not an iterable
				if (object instanceof Iterable && !isIterable) {
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
			return template == null ? null : ScriptRuntime.getRuntime().getSubstituter().substitute(template, ScriptRuntime.getRuntime().getExecutionContext(), true);
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
	
	@GlueMethod(description = "Executes the lambda with the given arguments", version = 2)
	public static Object apply(Lambda lambda, Object...objects) {
		List<?> resolved = SeriesMethods.resolve(GlueUtils.toSeries(objects));
		return GlueUtils.calculate(lambda, ScriptRuntime.getRuntime(), resolved);
	}
	
	@GlueMethod(description = "Allows you to make a lambda of any function", version = 2)
	public static Lambda function(String name) {
		ParserProvider parserProvider = ScriptRuntime.getRuntime().getScript().getRepository().getParserProvider();
		if (!(parserProvider instanceof GlueParserProvider)) {
			parserProvider = new GlueParserProvider();
		}
		MethodProvider[] methodProviders = ((GlueParserProvider) parserProvider).getMethodProviders(ScriptUtils.getRoot(ScriptRuntime.getRuntime().getScript().getRepository()));
		for (MethodProvider provider : methodProviders) {
			Operation<ExecutionContext> resolved = provider.resolve(name);
			if (resolved instanceof LambdaExecutionOperation) {
				return new LambdaImpl(((LambdaExecutionOperation) resolved).getMethodDescription(), 
					((LambdaExecutionOperation) resolved).getOperation(),
					((LambdaExecutionOperation) resolved).getEnclosedContext());
			}
			else if (resolved != null) {
				// find the description
				for (MethodDescription description : provider.getAvailableMethods()) {
					if (description.getName().equals(name) || ((description.getNamespace() == null ? "" : description.getNamespace() + ".") + description.getName()).equals(name)) {
						resolved.getParts().add(new QueryPart(Type.STRING, name));
						// because of the method description, the lambda execution will inject all the correct variables, so we simply make sure the final execution takes the original variables
						GlueOperationProvider operationProvider = new GlueOperationProvider(methodProviders);
						for (ParameterDescription parameter : description.getParameters()) {
							Operation<ExecutionContext> newOperation = operationProvider.newOperation(OperationType.VARIABLE);
							newOperation.getParts().add(new QueryPart(Type.VARIABLE, parameter.getName()));
							resolved.getParts().add(new QueryPart(Type.OPERATION, newOperation));
						}
						return new LambdaImpl(description, resolved, new HashMap<String, Object>());
					}
				}
				throw new RuntimeException("Can only wrap functions with a runtime definition");
			}
		}
		return null;
	}
	
	public static Lambda decorate(Lambda decorated, Lambda...decorators) {
		for (Lambda decorator : decorators) {
			decorated = decorateSingle(decorated, decorator);
		}
		return decorated;
	}
	
	private static Lambda decorateSingle(Lambda decorated, Lambda decorator) {
		return new LambdaImpl(decorated.getDescription(), new DecoratorOperation(decorated, decorator), new HashMap<String, Object>());
	}
	
	@SuppressWarnings("unchecked")
	public static Lambda bind(@GlueParam(name = "lambda") EnclosedLambda original, @GlueParam(name = "scope") Object scope) {
		if (scope == null) {
			scope = ScriptRuntime.getRuntime().getExecutionContext().getPipeline();
		}
		else if (scope instanceof ExecutionContext) {
			scope = ((ExecutionContext) scope).getPipeline();
		}
		Map<String, Object> enclosed = original.isMutable() ? (Map<String, Object>) scope : new HashMap<String, Object>((Map<String, Object>) scope);
		return new LambdaImpl(original.getDescription(), original.getOperation(), enclosed, original.isMutable());
	}

	@GlueMethod(description = "Returns the keys available in this object", version = 2)
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Collection<String> keys(Object object) {
		if (object == null) {
			return null;
		}
		ContextAccessor accessor = ContextAccessorFactory.getInstance().getAccessor(object.getClass());
		if (accessor instanceof ListableContextAccessor) {
			return ((ListableContextAccessor) accessor).list(object);
		}
		return null;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@GlueMethod(version = 2)
	public static Object map(Object...objects) {
		// this will merge arrays etc
//		objects = array(objects);
		Set<String> keys = new LinkedHashSet<String>();
		List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();
		for (Object object : GlueUtils.toSeries(objects)) {
			if (object == null) {
				continue;
			}
			else if (object instanceof String) {
				keys.add((String) object);
			}
			else if (object instanceof Map) {
				keys.addAll(((Map<String, Object>) object).keySet());
				maps.add((Map<String, Object>) object);
			}
			else if (object instanceof Object[] || object instanceof Collection || object instanceof Iterable) {
				if (keys.isEmpty()) {
					throw new IllegalArgumentException("The map has no defined keys");
				}
				Iterable iterable;
				if (object instanceof Iterable) {
					iterable = GlueUtils.resolve((Iterable) object);
				}
				else if (object instanceof Object[]) {
					iterable = Arrays.asList((Object[]) object);
				}
				else {
					iterable = (Collection) object;
				}
				if (iterable.iterator().hasNext()) {
					Object first = iterable.iterator().next();
					// it's a matrix
					if (first instanceof Object[] || first instanceof Collection) {
						Iterator iterator = iterable.iterator();
						while(iterator.hasNext()) {
							Object record = iterator.next();
							if (!(record instanceof Object[]) && !(record instanceof Collection)) {
								throw new IllegalArgumentException("The record is not an array or collection: " + record);
							}
							List<Object> fields = record instanceof Object[] ? Arrays.asList((Object[]) record) : new ArrayList<Object>((Collection<Object>) record);
							// use linked hashmaps to retain key order
							Map<String, Object> result = new LinkedHashMap<String, Object>();
							if (fields.size() > keys.size()) {
								throw new IllegalArgumentException("There are " + fields.size() + " objects but only " + keys.size() + " keys in: " + fields);
							}
							int i = 0;
							for (String key : keys) {
								result.put(key, fields.size() > i ? fields.get(i++) : null);
							}
							maps.add(result);
						}
					}
					else {
						Iterator iterator = iterable.iterator();
						// use linked hashmaps to retain key order
						Map<String, Object> result = new LinkedHashMap<String, Object>();
						for (String key : keys) {
							result.put(key, iterator.hasNext() ? iterator.next() : null);
						}
						maps.add(result);
					}
				}
			}
			else {
				throw new IllegalArgumentException("Invalid object for a map: " + object);
			}
		}
		if (GlueUtils.getVersion().contains(1.0)) {
			return maps.toArray(new Map[0]);
		}
		else {
			return maps;
		}
	}
	
	// create a copy of a lambda, can update function to first check for lambdas
	// we need a way to freeze the context of a method lambda
//	public static Lambda freeze(EnclosedLambda original) {
//		
//	}
}