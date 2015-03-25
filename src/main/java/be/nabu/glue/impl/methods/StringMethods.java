package be.nabu.glue.impl.methods;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "string")
public class StringMethods {
	
	@GlueMethod(description = "Adds the given pad to the given string(s) on the right until they reach the required length", returns = "The padded string(s)")
	public static Object padRight(@GlueParam(name = "pad", description = "The string used to pad") String pad, @GlueParam(name = "length", description = "The length of the resulting string") int length, @GlueParam(name = "strings", description = "The string(s) to be padded") String...original) {
		return pad(pad, length, true, original);
	}
	
	@GlueMethod(description = "Adds the given pad to the given string(s) on the left until they reach the required length", returns = "The padded string(s)")
	public static Object padLeft(@GlueParam(name = "pad", description = "The string used to pad") String pad, @GlueParam(name = "length", description = "The length of the resulting string") int length, @GlueParam(name = "strings", description = "The string(s) to be padded") String...original) {
		return pad(pad, length, false, original);
	}

	public static Object pad(String pad, int length, boolean leftAlign, String...original) {
		if (original == null || original.length == 0) {
			return original;
		}
		if (pad == null || pad.isEmpty()) {
			pad = " ";
		}
		String [] result = new String[original.length];
		for (int i = 0; i < original.length; i++) {
			String value = original[i];
			if (value != null) {
				while (value.length() < length) {
					if (leftAlign) {
						value += pad;
					}
					else {
						value = pad + value;
					}
				}
				if (value.length() > length) {
					value = value.substring(0, length);
				}
				result[i] = value;
			}
		}
		return result.length == 1 ? result[0] : result; 
	}
	
	public static Object upper(Object...original) {
		if (original == null || original.length == 0) {
			return original;
		}
		String [] result = new String[original.length];
		for (int i = 0; i < original.length; i++) {
			String value = original[i] == null ? null : original[i].toString();
			result[i] = value.toUpperCase();
		}
		return result.length == 1 ? result[0] : result;
	}

	public static Object lower(Object...original) {
		if (original == null || original.length == 0) {
			return original;
		}
		String [] result = new String[original.length];
		for (int i = 0; i < original.length; i++) {
			String value = original[i] == null ? null : original[i].toString();
			result[i] = value.toLowerCase();
		}
		return result.length == 1 ? result[0] : result;
	}
	
	public static String substring(String original, int start) {
		return original.substring(start);
	}
	
	public static String substring(String original, int start, int stop) {
		return original.substring(start, stop);
	}
	
	public static Object retain(String regex, String...original) {
		List<String> result = new ArrayList<String>();
		for (String string : original) {
			if (string.matches(regex)) {
				result.add(string);
			}
		}
		return original.length == 1 ? result.get(0) : result.toArray(new String[result.size()]);
	}
	
	public static Object remove(String regex, String...original) {
		List<String> result = new ArrayList<String>();
		for (String string : original) {
			if (!string.matches(regex)) {
				result.add(string);
			}
		}
		return original.length == 1 ? result.get(0) : result.toArray(new String[result.size()]);
	}
	
	public static Object replace(String regex, String replacement, String...original) {
		String [] result = new String[original.length];
		for (int i = 0; i < original.length; i++) {
			result[i] = original[i] == null ? null : original[i].replaceAll(regex, replacement);
		}
		return result.length == 1 ? result[0] : result;
	}
	
	public static String [] find(String regex, String...original) {
		List<String> matches = new ArrayList<String>();
		Pattern pattern = Pattern.compile(regex);
		for (String single : original) {
			if (single == null) {
				continue;
			}
			Matcher matcher = pattern.matcher(single);
			while (matcher.find()) {
				if (matcher.groupCount() > 0) {
					matches.add(matcher.group(1));
				}
				else {
					matches.add(matcher.group());
				}
			}
		}
		return matches.toArray(new String[0]);
	}
	
	public static String [] lines(String...original) {
		return original == null || original.length == 0 ? null : split("[\\r\\n]+", original);
	}
	
	public static String [] columns(String original) {
		return original == null ? null : split("[\\s]+", original.trim());
	}
	
	public static String join(String separator, String...strings) {
		if (strings == null || strings.length == 0) {
			return null;
		}
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < strings.length; i++) {
			if (strings[i] == null) {
				continue;
			}
			if (!builder.toString().isEmpty()) {
				builder.append(separator);
			}
			builder.append(strings[i]);
		}
		return builder.toString();
	}
	
	public static String [] split(String regex, String...strings) {
		List<String> results = new ArrayList<String>();
		for (String string : strings) {
			if (string == null) {
				continue;
			}
			results.addAll(Arrays.asList(string.split(regex)));
		}
		return results.toArray(new String[0]);
	}
}
