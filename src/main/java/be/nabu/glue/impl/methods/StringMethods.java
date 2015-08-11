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
	public static Object padRight(
			@GlueParam(name = "pad", description = "The string used to pad") String pad, 
			@GlueParam(name = "length", description = "The length of the resulting string") int length, 
			@GlueParam(name = "strings", description = "The string(s) to be padded") String...original) {
		return pad(pad, length, true, original);
	}
	
	@GlueMethod(description = "Adds the given pad to the given string(s) on the left until they reach the required length", returns = "The padded string(s)")
	public static Object padLeft(
			@GlueParam(name = "pad", description = "The string used to pad") String pad, 
			@GlueParam(name = "length", description = "The length of the resulting string") int length, 
			@GlueParam(name = "strings", description = "The string(s) to be padded") String...original) {
		return pad(pad, length, false, original);
	}

	@GlueMethod(description = "Pads string(s) to a given length using the given pad")
	public static Object pad(
			@GlueParam(name = "pad", description = "The string to pad with") String pad, 
			@GlueParam(name = "length", description = "The length the resulting string(s) should be") int length, 
			@GlueParam(name = "leftAlign", description = "Whether to left align the original string(s)") boolean leftAlign, 
			@GlueParam(name = "strings", description = "The string(s) to pad") String...original) {
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

	@GlueMethod(description = "Uppercases the string(s)")
	public static Object upper(@GlueParam(name = "strings", description = "One or more strings") Object...original) {
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

	@GlueMethod(description = "Lowercases the string(s)")
	public static Object lower(@GlueParam(name = "strings", description = "One or more strings") Object...original) {
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
	
	@GlueMethod(description = "Retrieves a substring of the given string")
	public static String substring(
			@GlueParam(name = "string", description = "The original string") String string, 
			@GlueParam(name = "start", description = "The start position") int start,
			@GlueParam(name = "stop", description = "The stop position", defaultValue = "To the end of the string") Integer stop) {
		return stop == null ? string.substring(start) : string.substring(start, stop);
	}
	
	@GlueMethod(description = "Allows you to retain only a certain part of the given string(s) based on the given regex")
	public static Object retain(@GlueParam(name = "regex", description = "The regex to match") String regex, @GlueParam(name = "strings", description = "The string(s) to filter") String...original) {
		List<String> result = new ArrayList<String>();
		for (String string : original) {
			if (string.matches(regex)) {
				result.add(string);
			}
		}
		return original.length == 1 ? result.get(0) : result.toArray(new String[result.size()]);
	}
	
	@GlueMethod(description = "Removes the string(s) matching the given regex")
	public static Object remove(@GlueParam(name = "regex", description = "The regex to match in order to remove the string(s)") String regex, @GlueParam(name = "strings", description = "The string(s) to filter") String...original) {
		List<String> result = new ArrayList<String>();
		if (original != null) {
			for (String string : original) {
				if (!string.matches(regex)) {
					result.add(string);
				}
			}
		}
		if (result.isEmpty()) {
			return original == null || original.length == 1 ? null : new String[0];
		}
		else {
			return original.length == 1 ? result.get(0) : result.toArray(new String[result.size()]);
		}
	}
	
	@GlueMethod(description = "Replaces the given regex with the replacement in the given string(s)")
	public static Object replace(
			@GlueParam(name = "regex", description = "The regex to match") String regex, 
			@GlueParam(name = "replacement", description = "The replacement to replace it with") String replacement, 
			@GlueParam(name = "strings", description = "The string(s) to perform the replacement on") String...original) {
		if (original == null) {
			return null;
		}
		String [] result = new String[original.length];
		for (int i = 0; i < original.length; i++) {
			result[i] = original[i] == null ? null : original[i].replaceAll(regex, replacement);
		}
		return result.length == 1 ? result[0] : result;
	}
	
	@GlueMethod(description = "Finds all the results matching the regex in the given string(s)")
	public static String [] find(@GlueParam(name = "regex", description = "The regex to match") String regex, @GlueParam(name = "strings", description = "The string(s) to perform the find on") String...original) {
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
	
	@GlueMethod(description = "Returns all the lines found in the given string(s). Supports all combinations of linefeed and carriage return.")
	public static String [] lines(@GlueParam(name = "strings", description = "The string(s) to split into lines") String...original) {
		return original == null || original.length == 0 ? null : split("[\\r\\n]+", original);
	}
	
	@GlueMethod(description = "Splits the given string into columns, if you want more control over the separators, use split()")
	public static String [] columns(@GlueParam(name = "string", description = "The string to split into columns") String original) {
		return original == null ? null : split("[\t,;]+", original.trim());
	}
	
	@GlueMethod(description = "Removes any leading and trailing whitespace from the given string(s)")
	public static Object trim(@GlueParam(name = "strings", description = "One or more strings") String...strings) {
		if (strings == null) {
			return null;
		}
		String [] result = new String[strings.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = strings[i].trim();
		}
		return result.length == 1 ? result[0] : result;
	}
	
	@GlueMethod(description = "Combines the given strings into a single string adding the seperator in between each string. For example join(',', 'a', 'b') returns 'a,b'")
	public static String join(@GlueParam(name = "separator") String separator, @GlueParam(name = "strings") String...strings) {
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
	
	@GlueMethod(description = "Splits the given string(s) into parts using the given regex")
	public static String [] split(@GlueParam(name = "regex", description = "The regex to use to split the string(s)") String regex, @GlueParam(name = "strings", description = "The string(s) to split into parts") String...strings) {
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
