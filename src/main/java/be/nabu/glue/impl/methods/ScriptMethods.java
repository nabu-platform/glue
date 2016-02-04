package be.nabu.glue.impl.methods;

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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.ScriptRuntimeException;
import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.impl.ForkedExecutionContext;
import be.nabu.glue.impl.TransactionalCloseable;
import be.nabu.glue.impl.executors.EvaluateExecutor;
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
	public static void echo(Object...messages) {
		ScriptRuntime.getRuntime().getFormatter().print(messages);
	}
	
	public static void console(Object...messages) {
		if (messages != null && messages.length > 0) {
			for (Object message : messages) {
				System.out.println(message);
			}
		}
	}
	
	public static void debug(Object...messages) {
		if (ScriptRuntime.getRuntime().getExecutionContext().isDebug()) {
			echo(messages);
		}
	}

	@SuppressWarnings("unchecked")
	public static Object eval(String evaluation, Object context) throws IOException, ParseException, ExecutionException, EvaluationException {
		ExecutorGroup parsed = ScriptRuntime.getRuntime().getScript().getParser().parse(new StringReader(evaluation));
		if (parsed.getChildren().size() > 1) {
			throw new ParseException("Only single lines of code are allowed for eval", 0);
		}
		if (!(parsed.getChildren().get(0) instanceof EvaluateExecutor)) {
			throw new ParseException("Invalid evaluation string: " + evaluation, 0);
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
		return ((EvaluateExecutor) parsed.getChildren().get(0)).getOperation().evaluate(executionContext);
	}
	
	public static Object eval(String evaluation) throws IOException, ParseException, ExecutionException, EvaluationException {
		return eval(evaluation, ScriptRuntime.getRuntime().getExecutionContext());
	}
	
	public static void inject(Object object) {
		inject(object, false);
	}
	
	@SuppressWarnings("unchecked")
	public static void inject(Object object, boolean overwriteExisting) {
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
				throw new IllegalArgumentException("Can not inject: " + object);
			}
			for (String key : pipeline.keySet()) {
				if (overwriteExisting || !current.containsKey(key)) {
					current.put(key, pipeline.get(key));
				}
			}
		}
	}
	
	public static Map<String, Object> scope(Integer offset) {
		// take the current scope
		if (offset == null) {
			offset = 0;
		}
		ScriptRuntime runtime = ScriptRuntime.getRuntime();
		for (int i = 0; i < offset; i++) {
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
	
	public static void fail(String message) {
		throw new ScriptRuntimeException(ScriptRuntime.getRuntime(), message);
	}

	public static Object [] flatten(int column, Object...objects) {
		List<Object> flattened = new ArrayList<Object>();
		for (Object object : objects) {
			Object [] values = object instanceof Object [] ? (Object[]) object : Arrays.asList((Collection<?>) object).toArray();
			flattened.add(column < values.length ? values[column] : null);
		}
		return flattened.toArray();
	}
	
	public static Object [] slice(@GlueParam(name = "start", defaultValue = "0") Integer start, @GlueParam(name = "stop", defaultValue = "End of the list") Integer stop, @GlueParam(name = "objects") Object...objects) {
		Object[] array = array(objects);
		return array(Arrays.asList(array).subList(start == null ? 0 : start, stop == null ? array.length : Math.min(stop, array.length)).toArray());
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object [] sort(@GlueParam(name = "objects") Object...objects) {
		List<? extends Comparable> list = new ArrayList(Arrays.asList(array(objects)));
		Collections.sort(list);
		return array(list.toArray());
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object [] reverse(@GlueParam(name = "objects") Object...objects) {
		List<? extends Comparable> list = new ArrayList(Arrays.asList(array(objects)));
		Collections.reverse(list);
		return array(list.toArray());
	}
	
	/**
	 * Creates an array of objects. If the objects themselves contain arrays, they are merged
	 */
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
		throw new IllegalArgumentException("Can not get the size of " + object);
	}
	
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
	
	@SuppressWarnings("unchecked")
	public static Map<String, Object>[] map(Object...objects) {
		// this will merge arrays etc
		objects = array(objects);
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
			else if (object instanceof Object[] || object instanceof Collection) {
				if (keys.isEmpty()) {
					throw new IllegalArgumentException("The map has no defined keys");
				}
				List<Object> elements = object instanceof Object[] ? Arrays.asList((Object[]) object) : new ArrayList<Object>((Collection<Object>) object);
				// use linked hashmaps to retain key order
				Map<String, Object> result = new LinkedHashMap<String, Object>();
				if (elements.size() > keys.size()) {
					throw new IllegalArgumentException("There are " + elements.size() + " objects but only " + keys.size() + " keys");
				}
				int i = 0;
				for (String key : keys) {
					result.put(key, elements.size() > i ? elements.get(i++) : null);
				}
				maps.add(result);
			}
			else {
				throw new IllegalArgumentException("Invalid object for a map: " + object);
			}
		}
		return maps.toArray(new Map[0]);
	}
	
	@GlueMethod(description = "Returns a globally unique id (type 4)")
	public static String uuid(@GlueParam(name = "formatted", description = "Whether or not the uuid should be formatted using '-'", defaultValue = "false") Boolean formatted) {
		String uuid = UUID.randomUUID().toString();
		return formatted != null && formatted ? uuid : uuid.replace("-", "");
	}

	public static Object first(Object...array) {
		return array == null || array.length == 0 ? null : array[0];
	}
	
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
	public static Object [] unique(Object...objects) {
		List<Object> results = new ArrayList<Object>();
		Class<?> componentType = null;
		for (Object object : array(objects)) {
			if (componentType == null && object != null) {
				componentType = object.getClass();
			}
			if (!results.contains(object)) {
				results.add(object);
			}
		}
		if (componentType == null) {
			componentType = Object.class;
		}
		return results.toArray((Object[]) Array.newInstance(componentType, results.size()));
	}
	
	/**
	 * Loads a resource as inputstream
	 */
	public static InputStream resource(String name) throws IOException {
		return getInputStream(name);
	}
	
	public static String [] resources() {
		List<String> resources = new ArrayList<String>();
		for (String resource : ScriptRuntime.getRuntime().getScript()) {
			resources.add(resource);
		}
		return resources.toArray(new String[resources.size()]);
	}
	
	public static String string(Object object) throws IOException {
		return string(object, true);
	}
	
	/**
	 * Will stringify the object
	 */
	public static String string(Object object, boolean substitute) throws IOException {
		if (object == null) {
			return null;
		}
		byte [] bytes = bytes(object);
		String result = bytes == null ? null : new String(bytes, ScriptRuntime.getRuntime().getScript().getCharset());
		return substitute 
			? ScriptRuntime.getRuntime().getSubstituter().substitute(result, ScriptRuntime.getRuntime().getExecutionContext(), false) 
			: result;
	}
	
	public static String bytesToString(byte [] bytes, String encoding) throws UnsupportedEncodingException {
		return new String(bytes, encoding);
	}
	
	public static byte [] bytes(Object object) throws IOException {
		if (object == null) {
			return null;
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
			return extensions.contains(extension) ? string(bytes) : bytes;
		}
		// assume a hidden text file
		else if (index == 0) {
			return string(name);
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
	
	public static Object value(Object existing, Object defaultValue) {
		return existing == null ? defaultValue : existing;
	}
	
	@SuppressWarnings("unchecked")
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
	
	
	@GlueMethod(description = "Allows you to retain only a certain part of the given string(s) based on the given regex")
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
	
	@GlueMethod(description = "Removes the string(s) matching the given regex")
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
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static String [] keys(Object object) {
		if (object == null) {
			return new String[0];
		}
		ContextAccessor accessor = ContextAccessorFactory.getInstance().getAccessor(object.getClass());
		if (accessor instanceof ListableContextAccessor) {
			return (String[]) ((ListableContextAccessor) accessor).list(object).toArray(new String[0]);
		}
		return new String[0];
	}
}
