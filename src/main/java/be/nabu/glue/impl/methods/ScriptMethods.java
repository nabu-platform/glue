package be.nabu.glue.impl.methods;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
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
import be.nabu.libs.converter.ConverterFactory;

public class ScriptMethods {
	
	public static List<String> extensions = Arrays.asList(new String [] { "xml", "json", "txt", "ini", "properties", "sql", "csv", "html", "htm", "glue", "py", "c++", "cpp", "c", "php", "js", "java" });

	/**
	 * All the objects passed into this method are logged one after another to the runtime's output
	 */
	public static void echo(Object...messages) throws IOException {
		for (Object message : messages) {
			ScriptRuntime.getRuntime().log(message == null ? "null" : message.toString());
		}
	}
	
	/**
	 * Returns the value of an environment variable
	 */
	public static String environment(String name) {
		return ScriptRuntime.getRuntime().getExecutionContext().getExecutionEnvironment().getParameters().containsKey(name) 
			? ScriptRuntime.getRuntime().getExecutionContext().getExecutionEnvironment().getParameters().get(name)
			: System.getProperty(name);
	}
	
	public static void fail(String message) {
		throw new ScriptRuntimeException(ScriptRuntime.getRuntime(), message);
	}

	/**
	 * Creates an array of objects. If the objects themselves contain arrays, they are merged
	 */
	public static Object[] array(Object...objects) {
		if (objects.length == 0) {
			return objects;
		}
		Class<?> componentType = null;
		List<Object> results = new ArrayList<Object>();
		boolean componentTypeAccurate = true;
		for (int i = 0; i < objects.length; i++) {
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
		return componentTypeAccurate ? results.toArray((Object[]) Array.newInstance(componentType, results.size())) : results.toArray();
	}
	
	public static Collection<?> tuple(Object...objects) {
		return Arrays.asList(objects);
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
					result.put(key, elements.get(i++));
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
		return array.length == 0 ? null : array[array.length - 1];
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
	
	/**
	 * Will stringify the object
	 */
	public static String string(Object object) throws IOException {
		byte [] bytes = bytes(object);
		return bytes == null ? null : new String(bytes, ScriptRuntime.getRuntime().getScript().getCharset());
	}
	
	public static byte [] bytes(Object object) throws IOException {
		if (object instanceof String) {
			InputStream data = getInputStream((String) object);
			if (data != null) {
				object = data;
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
}
