package be.nabu.glue.impl.methods;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

import be.nabu.glue.ScriptRuntime;
import be.nabu.libs.converter.ConverterFactory;

public class ScriptMethods {
	
	private static List<String> extensions = Arrays.asList(new String [] { "xml", "json", "txt", "ini", "properties", "sql", "csv", "html", "htm", "glue", "py", "c++", "cpp", "c", "php", "js", "java" });
	
	public static void echo(Object message) throws IOException {
		if (message != null) {
			ScriptRuntime.getRuntime().log(message.toString());
		}
	}
	
	public static String environment(String name) {
		return ScriptRuntime.getRuntime().getExecutionContext().getExecutionEnvironment().getParameters().get(name);
	}

	public static Object[] array(Object...objects) {
		if (objects.length == 0) {
			return objects;
		}
		Class<?> componentType = objects[0].getClass();
		Object[] result = (Object[]) Array.newInstance(componentType, objects.length);
		for (int i = 0; i < objects.length; i++) {
			result[i] = ConverterFactory.getInstance().getConverter().convert(objects[i], componentType);
		}
		return result;
	}
	
	public static Object file(String name) throws IOException {
		int index = name.lastIndexOf('.');
		if (index > 0) {
			String extension = name.substring(index + 1).toLowerCase();
			if (extensions.contains(extension)) {
				return string(name);
			}
			else {
				return bytes(name);
			}
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
	
	public static String typeof(Object object) {
		return object == null ? "null" : object.getClass().getName();
	}

	public static String string(Object object) throws IOException {
		byte [] bytes = bytes(object);
		return bytes == null ? null : new String(bytes, ScriptRuntime.getRuntime().getScript().getCharset());
	}
	
	public static InputStream resource(String name) throws IOException {
		return getInputStream(name);
	}
	
	public static InputStream toStream(Object content) throws IOException {
		if (content instanceof String) {
			return new ByteArrayInputStream(((String) content).getBytes(ScriptRuntime.getRuntime().getScript().getCharset())); 
		}
		else if (content instanceof byte[]) {
			return new ByteArrayInputStream((byte []) content);
		}
		else if (content instanceof InputStream) {
			return (InputStream) content;
		}
		throw new IOException("Can not convert " + content.getClass() + " to input stream");
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
	
	private static InputStream getInputStream(String name) throws IOException {
		return ScriptRuntime.getRuntime().getExecutionContext().getContent(name);
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
