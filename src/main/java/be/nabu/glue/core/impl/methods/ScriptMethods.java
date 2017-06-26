package be.nabu.glue.core.impl.methods;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.Script;
import be.nabu.glue.core.api.EnclosedLambda;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.executors.EvaluateExecutor;
import be.nabu.glue.impl.ForkedExecutionContext;
import be.nabu.glue.impl.TransactionalCloseable;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.glue.utils.ScriptRuntimeException;
import be.nabu.glue.utils.ScriptUtils;
import be.nabu.glue.utils.VirtualScript;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.api.ListableContextAccessor;

@MethodProviderClass(namespace = "script")
public class ScriptMethods {

	public static final String SEQUENCES = "sequences";
	
	public static List<String> extensions = Arrays.asList(new String [] { "xml", "json", "txt", "ini", "properties", "sql", "csv", "html", "htm", "glue", "py", "c++", "cpp", "c", "php", "js", "java" });

	/**
	 * All the objects passed into this method are logged one after another to the runtime's output
	 */
	@GlueMethod(version = 1)
	public static void echo(Object...messages) {
		ScriptRuntime.getRuntime().getFormatter().print(messages);
	}
	
	@GlueMethod(version = 1)
	public static void console(Object...messages) {
		if (messages != null && messages.length > 0) {
			for (Object message : messages) {
				System.out.println(message);
			}
		}
	}
	
	@GlueMethod(version = 1)
	public static void debug(Object...messages) {
		if (ScriptRuntime.getRuntime().getExecutionContext().isDebug()) {
			echo(messages);
		}
	}
	
	@SuppressWarnings("unchecked")
	public static Object eval(String evaluation, Object context) throws IOException, ParseException, ExecutionException, EvaluationException {
		if (evaluation == null || evaluation.trim().isEmpty()) {
			return null;
		}
		ExecutionContext executionContext;
		if (context instanceof ExecutionContext) {
			executionContext = (ExecutionContext) context;
		}
		else if (context instanceof Map) {
			executionContext = new ForkedExecutionContext(ScriptRuntime.getRuntime().getExecutionContext(), (Map<String, Object>) context);
		}
		else {
			throw new IllegalArgumentException("Eval can only be done on an execution context or a map, not: " + context);
		}
		// if it's a multiline, execute as script
		if (evaluation.contains("\n")) {
			ScriptRuntime fork = ScriptRuntime.getRuntime().fork(new VirtualScript(ScriptRuntime.getRuntime().getScript(), evaluation), true);
			fork.setExecutionContext(executionContext);
			fork.run();
			return fork.getExecutionContext();
		}
		else {
			ExecutorGroup parsed = ScriptRuntime.getRuntime().getScript().getParser().parse(new StringReader(evaluation));
			if (parsed.getChildren().size() > 1) {
				throw new ParseException("Only single lines of code are allowed for eval", 0);
			}
			if (!(parsed.getChildren().get(0) instanceof EvaluateExecutor)) {
				throw new ParseException("Invalid evaluation string: " + evaluation, 0);
			}
			return ((EvaluateExecutor) parsed.getChildren().get(0)).getOperation().evaluate(executionContext);
		}
	}
	
	public static Object eval(String evaluation) throws IOException, ParseException, ExecutionException, EvaluationException {
		return eval(evaluation, ScriptRuntime.getRuntime().getExecutionContext());
	}
	
	public static void inject(Object object) throws EvaluationException {
		inject(object, false);
	}
	
	@SuppressWarnings("unchecked")
	public static void inject(Object object, boolean overwriteExisting) throws EvaluationException {
		if (object != null) {
			Map<String, Object> current = ScriptRuntime.getRuntime().getExecutionContext().getPipeline();
			
			Map<String, Object> pipeline;
			if (object instanceof ExecutionContext) {
				pipeline = ((ExecutionContext) object).getPipeline();
			}
			else if (object instanceof Map) {
				pipeline = (Map<String, Object>) object;
			}
			else if (object instanceof Number) {
				pipeline = scope(((Number) object).intValue());
			}
			else {
				ContextAccessor accessor = ContextAccessorFactory.getInstance().getAccessor(object.getClass());
				if (!(accessor instanceof ListableContextAccessor)) {
					throw new IllegalArgumentException("Can not inject: " + object);
				}
				pipeline = new HashMap<String, Object>();
				Collection<String> keys = ((ListableContextAccessor) accessor).list(object);
				for (String key : keys) {
					pipeline.put(key, accessor.get(object, key));
				}
			}
			for (String key : pipeline.keySet()) {
				if (overwriteExisting || !current.containsKey(key)) {
					current.put(key, pipeline.get(key));
				}
			}
		}
	}
	
	public static Map<String, Object> scope(Object object) {
		// take the current scope
		if (object == null) {
			object = 0;
		}
		else if (object instanceof Lambda) {
			return object instanceof EnclosedLambda ? ((EnclosedLambda) object).getEnclosedContext() : null;
		}
		ScriptRuntime runtime = ScriptRuntime.getRuntime();
		for (int i = 0; i < GlueUtils.convert(object, Integer.class); i++) {
			if (runtime.getParent() == null) {
				break;
			}
			runtime = runtime.getParent();
		}
		return runtime.getExecutionContext().getPipeline();
	}

	/**
	 * Returns the value of an environment variable
	 */
	public static String environment(String name) {
		return ScriptRuntime.getRuntime().getExecutionContext().getExecutionEnvironment().getParameters().containsKey(name) 
			? ScriptRuntime.getRuntime().getExecutionContext().getExecutionEnvironment().getParameters().get(name)
			: System.getProperty(name, System.getenv(name));
	}
	
	public static String environment(String name, String defaultValue) {
		String value = environment(name);
		return value == null ? defaultValue : value;
	}
	
	@GlueMethod(version = 1)
	public static void fail(Object message) {
		if (message instanceof Throwable) {
			throw new ScriptRuntimeException(ScriptRuntime.getRuntime(), (Throwable) message);
		}
		else {
			throw new ScriptRuntimeException(ScriptRuntime.getRuntime(), (String) message);
		}
	}

	@GlueMethod(version = 1)
	public static Object [] flatten(int column, Object...objects) {
		List<Object> flattened = new ArrayList<Object>();
		for (Object object : objects) {
			Object [] values = object instanceof Object [] ? (Object[]) object : Arrays.asList((Collection<?>) object).toArray();
			flattened.add(column < values.length ? values[column] : null);
		}
		return flattened.toArray();
	}
	
	@GlueMethod(version = 1)
	public static Object [] slice(@GlueParam(name = "start", defaultValue = "0") Integer start, @GlueParam(name = "stop", defaultValue = "End of the list") Integer stop, @GlueParam(name = "objects") Object...objects) {
		Object[] array = array(objects);
		return array(Arrays.asList(array).subList(start == null ? 0 : start, stop == null ? array.length : Math.min(stop, array.length)).toArray());
	}
	
	@GlueMethod(version = 1)
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object [] sort(@GlueParam(name = "objects") Object...objects) {
		List<? extends Comparable> list = new ArrayList(Arrays.asList(array(objects)));
		Collections.sort(list);
		return array(list.toArray());
	}
	
	@GlueMethod(version = 1)
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object [] reverse(@GlueParam(name = "objects") Object...objects) {
		List<? extends Comparable> list = new ArrayList(Arrays.asList(array(objects)));
		Collections.reverse(list);
		return array(list.toArray());
	}
	
	/**
	 * Creates an array of objects. If the objects themselves contain arrays, they are merged
	 */
	@GlueMethod(version = 1)
	public static Object[] array(Object...objects) {
		if (objects == null) {
			return new Object[0];
		}
		else if (objects.length == 0) {
			return objects;
		}
		Class<?> componentType = null;
		List<Object> results = new ArrayList<Object>();
		boolean componentTypeAccurate = true;
		for (int i = 0; i < objects.length; i++) {
			if (objects[i] == null) {
				continue;
			}
			if (componentType == null || componentType.equals(Object.class)) {
				componentType = objects[i] == null ? Object.class : objects[i].getClass();
				while (componentType.isArray()) {
					componentType = componentType.getComponentType();
				}
			}
			if (objects[i] instanceof Object[]) {
				for (Object single : (Object[]) objects[i]) {
					Object converted = ConverterFactory.getInstance().getConverter().convert(single, componentType);
					if (converted == null && single != null) {
						throw new ClassCastException("Can not cast " + single.getClass().getName() + " to " + componentType.getName());
					}
					results.add(ConverterFactory.getInstance().getConverter().convert(single, componentType));	
				}
			}
			else {
				Object value = objects[i];
				if (ConverterFactory.getInstance().getConverter().canConvert(objects[i].getClass(), componentType)) {
					value = ConverterFactory.getInstance().getConverter().convert(objects[i], componentType);
					if (value == null && objects[i] != null) {
						throw new ClassCastException("Can not cast " + objects[i].getClass().getName() + " to " + componentType.getName());
					}
				}
				else {
					componentTypeAccurate = false;
				}
				results.add(value);
			}
		}
		if (results.isEmpty()) {
			return new Object[0];
		}
		return componentTypeAccurate ? results.toArray((Object[]) Array.newInstance(componentType, results.size())) : results.toArray();
	}
	
	@SuppressWarnings({ "rawtypes", "unused" })
	public static int size(Object object) {
		if (object == null) {
			return 0;
		}
		else if (object instanceof byte[]) {
			return ((byte[]) object).length;
		}
		else if (object instanceof Object[]) {
			return ((Object[]) object).length;
		}
		else if (object instanceof String) {
			return ((String) object).length();
		}
		else if (object instanceof Collection) {
			return ((Collection<?>) object).size();
		}
		else if (object instanceof Map) {
			return ((Map<?, ?>) object).size();
		}
		else if (object instanceof Iterable) {
			int counter = 0;
			for (Object child : (Iterable) object) {
				counter++;
			}
			return counter;
		}
		throw new IllegalArgumentException("Can not get the size of " + object);
	}
	
	@GlueMethod(version = 1)
	public static Collection<?> tuple(Object...objects) {
		return Arrays.asList(objects);
	}
	
	@SuppressWarnings("unchecked")
	public static Map<String, Object> kv(Object...objects) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		if (objects != null) {
			String lastKey = null;
			for (Object object : objects) {
				if (lastKey != null) {
					map.put(lastKey, object);
					lastKey = null;
				}
				else if (object instanceof String) {
					lastKey = (String) object;
				}
				else if (object instanceof Map) {
					map.putAll((Map<String, ?>) object);
				}
				// if it's an object and there is no key available, it has to be an array or list
				else if (object instanceof Object[] || object instanceof Collection) {
					List<Object> elements = object instanceof Object[] ? Arrays.asList((Object[]) object) : new ArrayList<Object>((Collection<Object>) object);
					if (elements.isEmpty()) {
						continue;
					}
					String key = elements.get(0) == null ? "null" : elements.get(0).toString();
					if (elements.size() == 2) {
						map.put(key, elements.get(1));
					}
					else {
						elements.remove(0);
						map.put(key, elements);
					}
				}
				else {
					throw new IllegalArgumentException("Incorrect object at this location: " + object);
				}
			}
		}
		return map;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@GlueMethod(version = 1)
	public static Object map(Object...objects) {
		// this will merge arrays etc
//		objects = array(objects);
		Set<String> keys = new LinkedHashSet<String>();
		List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();
		for (Object object : objects) {
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
	
	@GlueMethod(description = "Returns a globally unique id (type 4)")
	public static String uuid(@GlueParam(name = "formatted", description = "Whether or not the uuid should be formatted using '-'", defaultValue = "false") Boolean formatted) {
		String uuid = UUID.randomUUID().toString();
		return formatted != null && formatted ? uuid : uuid.replace("-", "");
	}

	@GlueMethod(version = 1)
	public static Object first(Object...array) {
		return array == null || array.length == 0 ? null : array[0];
	}
	
	@GlueMethod(version = 1)
	public static Object index(int index, Object...array) {
		if (array == null || array.length == 0) {
			return null;
		}
		else if (array.length == 1 && array[0] instanceof List) {
			return ((List<?>) array[0]).get(index);
		}
		else {
			return array[index];
		}
	}
	
	@GlueMethod(version = 1)
	public static Object last(Object...array) {
		if (array.length == 1 && array[0] instanceof ExecutionContext) {
			List<Object> arrayList = new ArrayList<Object>(((ExecutionContext) array[0]).getPipeline().values());
			return arrayList.isEmpty() ? null : arrayList.get(arrayList.size() - 1);
		}
		else {
			return array.length == 0 ? null : array[array.length - 1];
		}
	}
	
	/**
	 * Makes sure all elements are unique within the array
	 * @param objects
	 * @return
	 */
	@GlueMethod(version = 1)
	public static Object [] unique(Object...objects) {
		Set<Object> results = new LinkedHashSet<Object>();
		Class<?> componentType = null;
		for (Object object : objects) {
			if (componentType == null && object != null) {
				componentType = object.getClass();
			}
			results.add(object);
		}
		if (componentType == null) {
			componentType = Object.class;
		}
		return results.toArray((Object[]) Array.newInstance(componentType, results.size()));
	}
	
	/**
	 * Loads a resource as inputstream
	 */
	@GlueMethod(version = 1)
	public static InputStream resource(@GlueParam(name = "name") String name, @GlueParam(name = "script") String script) throws IOException, ParseException {
		if (script != null) {
			return ScriptUtils.getRoot(ScriptRuntime.getRuntime().getScript().getRepository()).getScript(script).getResource(name);
		}
		return getInputStream(name);
	}
	
	@GlueMethod(version = 1)
	public static String [] resources(@GlueParam(name = "script") String scriptName) throws IOException, ParseException {
		Script script = ScriptRuntime.getRuntime().getScript();
		if (scriptName != null) {
			script = ScriptUtils.getRoot(ScriptRuntime.getRuntime().getScript().getRepository()).getScript(scriptName);
		}
		List<String> resources = new ArrayList<String>();
		for (String resource : script) {
			resources.add(resource);
		}
		return resources.toArray(new String[resources.size()]);
	}
	
	@SuppressWarnings("rawtypes")
	@GlueMethod(version = 1)
	public static Object template(String template, Object...values) throws EvaluationException {
		if (values == null || values.length == 0) {
			return ScriptRuntime.getRuntime().getSubstituter().substitute(template, ScriptRuntime.getRuntime().getExecutionContext(), true);
		}
		List<String> templated = new ArrayList<String>();
		if (values.length == 1 && values[0] instanceof Iterable) {
			if (!(values[0] instanceof Collection)) {
				List<Object> objects = new ArrayList<Object>();
				for (Object single : GlueUtils.resolve((Iterable) values[0])) {
					objects.add(single);
				}
				values[0] = objects;
			}
			values = ((Collection) values[0]).toArray();
		}
		ExecutionContext parentContext = ScriptRuntime.getRuntime().getExecutionContext();
		for (int i = 0; i < values.length; i++) {
			Map<String, Object> pipeline = toPipeline(values[i]);
			ForkedExecutionContext fork = new ForkedExecutionContext(parentContext, pipeline);
			templated.add(ScriptRuntime.getRuntime().getSubstituter().substitute(template, fork, true));
		}
		ScriptRuntime.getRuntime().setExecutionContext(parentContext);
		return values.length == 1 ? templated.get(0) : templated.toArray(new String[0]);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Map<String, Object> toPipeline(Object value) throws EvaluationException {
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
	/**
	 * Will stringify the object
	 */
	@GlueMethod(version = 1)
	public static String string(Object object, Boolean substitute) throws IOException {
		if (substitute == null) {
			substitute = GlueUtils.getVersion().contains(1.0);
		}
		if (object == null) {
			return null;
		}
		byte [] bytes = bytes(object);
		String result = bytes == null ? null : new String(bytes, ScriptRuntime.getRuntime().getScript().getCharset());
		return substitute 
			? ScriptRuntime.getRuntime().getSubstituter().substitute(result, ScriptRuntime.getRuntime().getExecutionContext(), false) 
			: result;
	}
	
	@GlueMethod(version = 1)
	public static String bytesToString(byte [] bytes, String encoding) throws UnsupportedEncodingException {
		return new String(bytes, encoding);
	}
	
	@GlueMethod(version = 1)
	public static byte [] bytes(Object object) throws IOException {
		if (object == null) {
			return null;
		}
		else if (object instanceof byte[]) {
			return (byte[]) object;
		}
		else if (object instanceof String && ((String) object).matches("^[^\n<>]+$")) {
			try {
				InputStream data = getInputStream((String) object);
				if (data != null) {
					object = data;
				}
			}
			catch (Exception e) {
				// ignore it
			}
		}
		return toBytesAndClose(toStream(object));
	}
	
	@GlueMethod(version = 1)
	public static boolean contains(Object object, Object...array) {
		if (object instanceof String && array != null && array.length == 1 && array[0] instanceof String) {
			return ((String) array[0]).contains((String) object);
		}
		else {
			return Arrays.asList(array(array)).contains(object);
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
	
	static Object file(String name) throws IOException {
		int index = name.lastIndexOf('.');
		if (index > 0) {
			String extension = name.substring(index + 1).toLowerCase();
			byte [] bytes = bytes(name);
			return extensions.contains(extension) ? string(bytes, null) : bytes;
		}
		// assume a hidden text file
		else if (index == 0) {
			return string(name, null);
		}
		// assume binary blob
		else {
			return bytes(name);
		}
	}
	
	private static InputStream getInputStream(String name) throws IOException {
		InputStream input = ScriptRuntime.getRuntime().getExecutionContext().getContent(name.replace('\\', '/'));
		if (input == null) {
			// not found in resources, check file system
			input = FileMethods.read(name);
		}
		return input;
	}
	
	private static byte [] toBytesAndClose(InputStream input) throws IOException {
		if (input == null) {
			return null;
		}
		byte [] buffer = new byte[4096];
		int read = 0;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			while ((read = input.read(buffer)) != -1) {
				output.write(buffer, 0, read);
			}
			return output.toByteArray();
		}
		finally {
			close(input);
		}
	}
	
	@GlueMethod(version = 1)
	public static Object value(Object existing, Object defaultValue) {
		return existing == null ? defaultValue : existing;
	}
	
	@SuppressWarnings("unchecked")
	@GlueMethod(version = 1)
	public static Object valueOf(String enumName, String value) throws ClassNotFoundException {
		Class<? extends Enum<?>> enumeration = (Class<? extends Enum<?>>) Thread.currentThread().getContextClassLoader().loadClass(enumName);
		if (!enumeration.isEnum()) {
			throw new IllegalArgumentException("The class " + enumName + " does not point to an enum");
		}
		for (Enum<?> enumValue : enumeration.getEnumConstants()) {
			if (enumValue.name().equalsIgnoreCase(value)) {
				return enumValue;
			}
		}
		throw new IllegalArgumentException("The enumeration " + enumName + " does not have the value: " + value);
	}
	
	@GlueMethod(description = "Closes any object that implements the java.io.Closeable interface")
	public static void close(@GlueParam(name = "closeable", description = "The closeable object") Closeable closeable) throws IOException {
		closeable.close();
		// don't re-close it
		ScriptRuntime.getRuntime().removeTransactionable(new TransactionalCloseable(closeable));
	}
	
	@GlueMethod(description = "Allows you to retain only a certain part of the given string(s) based on the given regex", version = 1)
	public static Object retain(@GlueParam(name = "needle", description = "The regex to match") Object needle, @GlueParam(name = "haystack", description = "The string(s) to filter") Object...haystack) {
		List<Object> result = new ArrayList<Object>();
		if (haystack != null && haystack.length > 0) {
			if (needle instanceof String) {
				for (Object object : haystack) {
					String string = ConverterFactory.getInstance().getConverter().convert(object, String.class);
					if (string != null && string.matches((String) needle)) {
						result.add(string);
					}
				}
			}
			else {
				List<Object> needles = listify(needle);
				for (Object object : haystack) {
					if (needles.contains(object)) {
						result.add(object);
					}
				}
			}
		}
		if (result.isEmpty()) {
			return null;
		}
		else {
			return haystack.length == 1 ? result.get(0) : array(result.toArray());
		}
	}

	@SuppressWarnings("unchecked")
	private static List<Object> listify(Object needle) {
		List<Object> needles;
		if (needle instanceof Collection) {
			needles = new ArrayList<Object>((Collection<Object>) needle);
		}
		else if (needle instanceof Object[]) {
			needles = Arrays.asList((Object[]) needle);
		}
		else {
			needles = Arrays.asList(needle);
		}
		return needles;
	}
	
	@GlueMethod(description = "Removes the string(s) matching the given regex", version = 1)
	public static Object remove(@GlueParam(name = "needle", description = "The regex to match in order to remove the string(s)") Object needle, @GlueParam(name = "haystack", description = "The string(s) to filter") Object...haystack) {
		List<Object> result = new ArrayList<Object>();
		if (haystack != null && haystack.length > 0) {
			if (needle instanceof String) {
				for (Object object : haystack) {
					String string = ConverterFactory.getInstance().getConverter().convert(object, String.class);
					if (!string.matches((String) needle)) {
						result.add(string);
					}
				}
			}
			else {
				List<Object> needles = listify(needle);
				for (Object object : haystack) {
					if (!needles.contains(object)) {
						result.add(object);
					}
				}
			}
		}
		if (result.isEmpty()) {
			return null;
		}
		else {
			return haystack.length == 1 ? result.get(0) : array(result.toArray());
		}
	}
	

	@SuppressWarnings("unchecked")
	@GlueMethod(description = "Generates a sequential number guaranteed to be unique for the given named sequence during a single script run")
	public static long sequence(@GlueParam(name = "name", description = "You can have multiple sequences at runtime", defaultValue = "default") String name) {
		Map<String, Object> context = ScriptRuntime.getRuntime().getContext();
		if (!context.containsKey(SEQUENCES)) {
			context.put(SEQUENCES, new HashMap<String, Integer>());
		}
		Map<String, Integer> sequences = (Map<String, Integer>) context.get(SEQUENCES);
		synchronized(sequences) {
			Integer sequence = sequences.containsKey(name) ? sequences.get(name) + 1 : 1;
			sequences.put(name, sequence);
			return sequence;
		}
	}
	
	@GlueMethod(version = 1)
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Object [] keys(Object object) {
		if (object == null) {
			return new String[0];
		}
		ContextAccessor accessor = ContextAccessorFactory.getInstance().getAccessor(object.getClass());
		if (accessor instanceof ListableContextAccessor) {
			return ((ListableContextAccessor) accessor).list(object).toArray();
		}
		return new String[0];
	}
}
