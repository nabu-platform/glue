package be.nabu.glue.impl.methods;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.ScriptRuntimeException;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.impl.executors.EvaluateExecutor;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.EvaluationException;

public class ScriptMethods {
	
	public static List<String> extensions = Arrays.asList(new String [] { "xml", "json", "txt", "ini", "properties", "sql", "csv", "html", "htm", "glue", "py", "c++", "cpp", "c", "php", "js", "java" });

	/**
	 * All the objects passed into this method are logged one after another to the runtime's output
	 */
	public static void echo(Object...messages) {
		ScriptRuntime.getRuntime().getFormatter().print(messages);
	}
	
	public static void debug(Object...messages) {
		if (ScriptRuntime.getRuntime().getExecutionContext().isDebug()) {
			echo(messages);
		}
	}

	public static Object eval(String evaluation, ExecutionContext context) throws IOException, ParseException, ExecutionException, EvaluationException {
		ExecutorGroup parsed = ScriptRuntime.getRuntime().getScript().getParser().parse(new StringReader(evaluation));
		if (parsed.getChildren().size() > 1) {
			throw new ParseException("Only single lines of code are allowed for eval", 0);
		}
		if (!(parsed.getChildren().get(0) instanceof EvaluateExecutor)) {
			throw new ParseException("Invalid evaluation string: " + evaluation, 0);
		}
		return ((EvaluateExecutor) parsed.getChildren().get(0)).getOperation().evaluate(context);
	}
	
	public static Object eval(String evaluation) throws IOException, ParseException, ExecutionException, EvaluationException {
		return eval(evaluation, ScriptRuntime.getRuntime().getExecutionContext());
	}
	
	public static void inject(int offset) {
		inject(offset, false);
	}
	
	public static void inject(int offset, boolean overwriteExisting) {
		Map<String, Object> current = ScriptRuntime.getRuntime().getExecutionContext().getPipeline();
		Map<String, Object> pipeline = scope(offset);
		for (String key : pipeline.keySet()) {
			if (overwriteExisting || !current.containsKey(key)) {
				current.put(key, pipeline.get(key));
			}
		}
	}
	
	public static Map<String, Object> scope(int offset) {
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
	
	public static void fail(String message) {
		throw new ScriptRuntimeException(ScriptRuntime.getRuntime(), message);
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

	public static Object first(Object...array) {
		return array.length == 0 ? null : array[0];
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
	 * Returns the type of the given object
	 */
	public static String typeof(Object object) {
		return object == null ? "null" : object.getClass().getName();
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
		byte [] bytes = bytes(object);
		String result = bytes == null ? null : new String(bytes, ScriptRuntime.getRuntime().getScript().getCharset());
		return substitute 
			? ScriptRuntime.getRuntime().getScript().getParser().substitute(result, ScriptRuntime.getRuntime().getExecutionContext(), false) 
			: result;
	}
	
	public static String bytesToString(byte [] bytes, String encoding) throws UnsupportedEncodingException {
		return new String(bytes, encoding);
	}
	
	public static byte [] bytes(Object object) throws IOException {
		if (object instanceof String && ((String) object).matches("^[^\n<>]+$")) {
			try {
				InputStream data = getInputStream((String) object);
				if (data != null) {
					object = data;
				}
			}
			catch (IOException e) {
				// ignore it
			}
		}
		return toBytesAndClose(toStream(object));
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
			input.close();
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
}
