/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.glue.core.impl.methods.v2;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
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
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.OutputFormatter;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.api.Parser;
import be.nabu.glue.api.ParserProvider;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.runs.GlueAttachment;
import be.nabu.glue.api.runs.GlueValidation;
import be.nabu.glue.core.api.EnclosedLambda;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.api.MethodProvider;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.GlueUtils.ObjectHandler;
import be.nabu.glue.core.impl.LambdaImpl;
import be.nabu.glue.core.impl.LambdaMethodProvider.LambdaExecutionOperation;
import be.nabu.glue.core.impl.methods.GlueAttachmentImpl;
import be.nabu.glue.core.impl.operations.GlueOperationProvider;
import be.nabu.glue.core.impl.operations.ScriptVariableOperation.LambdaJavaAccessor;
import be.nabu.glue.core.impl.parsers.GlueParser;
import be.nabu.glue.core.impl.parsers.GlueParserProvider;
import be.nabu.glue.core.impl.providers.ScriptMethodProvider.DecoratorOperation;
import be.nabu.glue.core.impl.providers.ScriptMethodProvider.ScriptOperation;
import be.nabu.glue.impl.ForkedExecutionContext;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.glue.utils.ScriptUtils;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.api.ListableContextAccessor;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseMethodOperation;

@MethodProviderClass(namespace = "script")
public class ScriptMethods {

	public static class CapturingFormatter implements OutputFormatter {
		private OutputFormatter parent;
		private StringBuilder builder = new StringBuilder();
		public CapturingFormatter(OutputFormatter parent) {
			this.parent = parent;
		}
		@Override
		public void start(Script script) {
			parent.start(script);
		}
		@Override
		public void before(Executor executor) {
			parent.before(executor);
		}
		@Override
		public void after(Executor executor) {
			parent.after(executor);
		}
		@Override
		public void validated(GlueValidation... validations) {
			parent.validated(validations);
		}
		@Override
		public void print(Object... messages) {
			if (messages != null) {
				for (Object object : messages) {
					if (object instanceof Iterable) {
						object = SeriesMethods.resolve((Iterable<?>) object);
					}
					builder.append(GlueUtils.convert(object, String.class)).append("\n");
				}
			}
		}
		@Override
		public void end(Script script, Date started, Date stopped, Exception exception) {
			parent.end(script, started, stopped, exception);
		}
		@Override
		public boolean shouldExecute(Executor executor) {
			return parent.shouldExecute(executor);
		}
		public OutputFormatter getParent() {
			return parent;
		}
		public String getCaptured() {
			return builder.toString();
		}
	}
	
	@GlueMethod(description = "Redirect echo to be captured", version = 2)
	public static void captureEcho() {
		final OutputFormatter formatter = ScriptRuntime.getRuntime().getFormatter();
		ScriptRuntime.getRuntime().setFormatter(new CapturingFormatter(formatter));
	}
	
	@GlueMethod(description = "Release the echo again and get the captured content", version = 2)
	public static String releaseEcho() {
		final OutputFormatter formatter = ScriptRuntime.getRuntime().getFormatter();
		if (formatter instanceof CapturingFormatter) {
			ScriptRuntime.getRuntime().setFormatter(((CapturingFormatter) formatter).getParent());
			return ((CapturingFormatter) formatter).getCaptured();
		}
		return null;
	}
	
	@GlueMethod(description = "Write content to the standard output", version = 2)
	public static void echo(Object...original) {
		if (original != null) {
			for (Object object : original) {
				// if we have an array, you must have explicitly unwrapped the parameter as part of a list of parameters, echo it as such
				if (object instanceof Object[]) {
					echo((Object[]) object);
				}
				else {
					// resolve iterables so we can see the content
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
	
	public static final String ATTACHMENT = "$attachment";
	
	@SuppressWarnings("unchecked")
	public static void attach(@GlueParam(name = "name") String name, @GlueParam(name = "content") byte[] content, @GlueParam(name = "contentType") String contentType) {
		ScriptRuntime runtime = ScriptRuntime.getRuntime();
		if (!runtime.getContext().containsKey(ATTACHMENT)) {
			runtime.getContext().put(ATTACHMENT, new ArrayList<GlueAttachment>());
		}
		List<GlueAttachment> attachments = (List<GlueAttachment>) runtime.getContext().get(ATTACHMENT);
		
		GlueAttachment attachment = new GlueAttachmentImpl(runtime.getExecutionContext().getCurrent(), name, content, contentType);
		attachments.add(attachment);
		runtime.getFormatter().attached(attachment);
	}
	
	@SuppressWarnings("unchecked")
	public static List<GlueAttachment> attachments() {
		ScriptRuntime runtime = ScriptRuntime.getRuntime();
		return (List<GlueAttachment>) runtime.getContext().get(ATTACHMENT);
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
	
	// TODO: can add support for parameters
	@GlueMethod(description = "Returns an instance of the given object", version = 2)
	public static Object instantiate(Object clazz) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		if (!(clazz instanceof Class)) {
			clazz = Thread.currentThread().getContextClassLoader().loadClass(clazz.toString());
		}
		return ((Class<?>) clazz).getConstructor().newInstance();
	}
	
	@GlueMethod(description = "Fill in the template with the given series of objects", version = 2)
	public static Object template(final String template, Object...original) throws EvaluationException {
		if (original == null || original.length == 0) {
			return template == null ? null : ScriptRuntime.getRuntime().getSubstituter().substitute(template, ScriptRuntime.getRuntime().getExecutionContext(), true);
		}
		final ScriptRuntime runtime = ScriptRuntime.getRuntime();
		// fork it on the current state of affairs
		final ForkedExecutionContext originalFork = new ForkedExecutionContext(runtime.getExecutionContext(), true);
		return GlueUtils.wrap(new ObjectHandler() {
			@SuppressWarnings("unchecked")
			@Override
			public Object handle(Object single) {
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
				// start a new fork with local modifications
				ForkedExecutionContext fork = new ForkedExecutionContext(originalFork, true);
				fork.getPipeline().putAll(pipeline);
				ScriptRuntime newRuntime = new ScriptRuntime(runtime.getScript(), fork, new HashMap<String, Object>());
				ScriptRuntime oldRuntime = ScriptRuntime.getRuntime();
				newRuntime.registerInThread(true);
				try {
					return runtime.getSubstituter().substitute(template, fork, true);
				}
				finally {
					newRuntime.unregisterInThread();
					if (oldRuntime != null) {
						oldRuntime.registerInThread();
					}
				}
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
	
	// TODO: should maybe retrofit the resolving of non-namespaced names to match the resolving added to DynamicMethodOperation?
	// this includes adhering to imports, prioritizing package-level scripts etc
	@GlueMethod(description = "Allows you to make a lambda of any function", version = 2)
	public static Lambda function(String name, Object context) throws EvaluationException {
		if (context == null) {
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
					throw new RuntimeException("The function '" + name + "' does not have a runtime definition");
				}
			}
		}
		else {
			LambdaJavaAccessor accessor = new LambdaJavaAccessor(null);
			Object object = accessor.get(context, name);
			if (object instanceof Lambda) {
				return (Lambda) object;
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
		Operation<ExecutionContext> operation = original.getOperation();
		if (operation instanceof ScriptOperation) {
			operation = new ScriptOperation(((ScriptOperation) operation).getScript(), enclosed);
		}
		return new LambdaImpl(original.getDescription(), operation, enclosed, original.isMutable());
	}

	@GlueMethod(description = "Returns the keys available in this object", version = 2)
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Collection<String> keys(Object object, Boolean hasValue) throws EvaluationException {
		if (object == null) {
			return null;
		}
		ContextAccessor accessor = ContextAccessorFactory.getInstance().getAccessor(object.getClass());
		if (accessor instanceof ListableContextAccessor) {
			Collection<String> list = ((ListableContextAccessor) accessor).list(object);
			// if you want to limit the keys to only those that actually have a value
			if (hasValue != null && hasValue) {
				List<String> filtered = new ArrayList<String>();
				for (String single : list) {
					if (accessor.hasValue(object, single)) {
						filtered.add(single);
					}
				}
				return filtered;
			}
			return list;
		}
		return null;
	}

	@GlueMethod(description = "Figure out where a function is coming from", version = 2)
	public static List<String> whereis(String name) {
		List<String> results = new ArrayList<String>();
		Parser parser = ScriptRuntime.getRuntime().getScript().getParser();
		if (parser instanceof GlueParser) {
			OperationProvider<ExecutionContext> operationProvider = ((GlueParser) parser).getOperationProvider();
			if (operationProvider instanceof GlueOperationProvider) {
				List<MethodProvider> methodProviders = ((GlueOperationProvider) operationProvider).getMethodProviders();
				if (methodProviders != null) {
					for (MethodProvider provider : methodProviders) {
						Operation<ExecutionContext> resolve = provider.resolve(name);
						if (resolve != null) {
							results.add(provider.getClass().getName());
						}
					}
				}
			}
		}
		return results;
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

	static InputStream toStream(Object content) throws IOException {
		if (content instanceof String) {
			return new ByteArrayInputStream(((String) content).getBytes(ScriptRuntime.getRuntime().getScript().getCharset())); 
		}
		else if (content instanceof byte[]) {
			return new ByteArrayInputStream((byte []) content);
		}
		else if (content instanceof InputStream) {
			return (InputStream) content;
		}
		else if (ConverterFactory.getInstance().getConverter().canConvert(content.getClass(), String.class)) {
			return new ByteArrayInputStream(ConverterFactory.getInstance().getConverter().convert(content, String.class).getBytes(ScriptRuntime.getRuntime().getScript().getCharset())); 
		}
		else {
			return new ByteArrayInputStream(content.toString().getBytes(ScriptRuntime.getRuntime().getScript().getCharset()));
		}
	}
	
	// create a copy of a lambda, can update function to first check for lambdas
	// we need a way to freeze the context of a method lambda
//	public static Lambda freeze(EnclosedLambda original) {
//		
//	}
}
