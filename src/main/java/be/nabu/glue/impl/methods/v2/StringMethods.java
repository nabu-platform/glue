package be.nabu.glue.impl.methods.v2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.impl.GlueUtils;
import be.nabu.glue.impl.GlueUtils.ObjectHandler;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "string")
public class StringMethods {
	
	@GlueMethod(description = "Adds the given pad to the given string(s) on the right until they reach the required length", returns = "The padded string(s)", version = 2)
	public static Object padRight(
			@GlueParam(name = "pad", description = "The string used to pad") String pad, 
			@GlueParam(name = "length", description = "The length of the resulting string") int length, 
			@GlueParam(name = "content", description = "The string(s) to be padded") Object...original) {
		return pad(pad, length, true, original);
	}
	
	@GlueMethod(description = "Adds the given pad to the given string(s) on the left until they reach the required length", returns = "The padded string(s)", version = 2)
	public static Object padLeft(
			@GlueParam(name = "pad", description = "The string used to pad") String pad, 
			@GlueParam(name = "length", description = "The length of the resulting string") int length, 
			@GlueParam(name = "content", description = "The string(s) to be padded") Object...original) {
		return pad(pad, length, false, original);
	}

	private static String padSingle(String pad, int length, boolean leftAlign, Object original) {
		if (original == null) {
			original = "";
		}
		if (pad == null || pad.isEmpty()) {
			pad = " ";
		}
		String value = (String) original;
		while (value.length() < length) {
			int padLength = Math.min(pad.length(), value.length() - length);
			if (padLength < pad.length()) {
				if (leftAlign) {
					pad = pad.substring(0, padLength);
				}
				else {
					pad = pad.substring(pad.length() - padLength);
				}
			}
			if (leftAlign) {
				value += pad;
			}
			else {
				value = pad + value;
			}
		}
		return value;
	}
	
	@GlueMethod(description = "Pads string(s) to a given length using the given pad", version = 2)
	public static Object pad(
			@GlueParam(name = "pad", description = "The string to pad with") final String pad, 
			@GlueParam(name = "length", description = "The length the resulting string(s) should be") final int length, 
			@GlueParam(name = "leftAlign", description = "Whether to left align the original string(s)") final boolean leftAlign, 
			@GlueParam(name = "content", description = "The string(s) to pad") Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return padSingle(pad, length, leftAlign, single);
			}
		}, String.class), true, original);
	}

	@GlueMethod(description = "Uppercases the string(s)", version = 2)
	public static Object upper(@GlueParam(name = "content", description = "One or more strings") Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return ((String) single).toUpperCase();
			}
		}, String.class), false, original);
	}

	@GlueMethod(description = "Lowercases the string(s)", version = 2)
	public static Object lower(@GlueParam(name = "content", description = "One or more strings") Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return ((String) single).toLowerCase();
			}
		}, String.class), false, original);
	}
	
	@GlueMethod(description = "Retrieves a substring of the given string", version = 2)
	public static Object substring(
			@GlueParam(name = "start", description = "The start position") final Integer start,
			@GlueParam(name = "stop", description = "The stop position", defaultValue = "To the end of the string") final Integer stop,
			@GlueParam(name = "content", description = "One or more strings") Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				Integer localStart = start;
				Integer localStop = stop;
				if (localStart == null) {
					localStart = 0;
				}
				if (localStop == null) {
					localStop = ((String) single).length();
				}
				else {
					localStop = Math.min(localStop, ((String) single).length());
				}
				return ((String) single).substring(localStart, localStop);
			}
		}, String.class), false, original);
	}
	
	@GlueMethod(description = "Replaces the given regex with the replacement in the given string(s)", version = 2)
	public static Object replace(
			@GlueParam(name = "regex", description = "The regex to match") final String regex, 
			@GlueParam(name = "replacement", description = "The replacement to replace it with") final String replacement, 
			@GlueParam(name = "content", description = "The string(s) to perform the replacement on") Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return ((String) single).replaceAll(regex, replacement);
			}
		}, String.class), false, original);
	}
	
	@GlueMethod(description = "Finds all the results matching the regex in the given string(s)", version = 2)
	public static Object find(@GlueParam(name = "regex", description = "The regex to match") String regex, @GlueParam(name = "content", description = "The string(s) to perform the find on") Object...original) {
		final Pattern pattern = Pattern.compile(regex);
		return GlueUtils.explode(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				List<String> matches = new ArrayList<String>();
				Matcher matcher = pattern.matcher((String) single);
				while (matcher.find()) {
					if (matcher.groupCount() > 0) {
						matches.add(matcher.group(1));
					}
					else {
						matches.add(matcher.group());
					}
				}
				return matches;
			}
		}, String.class), false, original);
	}
	
	@GlueMethod(description = "Returns all the lines found in the given string(s). Supports all combinations of linefeed and carriage return.", version = 2)
	public static Object lines(@GlueParam(name = "content", description = "The string(s) to split into lines") Object...original) {
		return GlueUtils.explode(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Arrays.asList(((String) single).split("[\\r\\n]+"));
			}
		}, String.class), false, original);
	}
	
	@GlueMethod(description = "Splits the given string into columns, if you want more control over the separators, use split()", version = 2)
	public static Object columns(@GlueParam(name = "content", description = "The string to split into columns") Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Arrays.asList(((String) single).split("[\t,;]+"));
			}
		}, String.class), false, original);
	}
	
	@GlueMethod(description = "Removes any leading and trailing whitespace from the given string(s)", version = 2)
	public static Object trim(@GlueParam(name = "content", description = "One or more strings") Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return ((String) single).trim();
			}
		}, String.class), false, original);
	}
	
	@GlueMethod(description = "Combines the given strings into a single string adding the seperator in between each string. For example join(',', 'a', 'b') returns 'a,b'", version = 2)
	public static String join(@GlueParam(name = "separator") String separator, @GlueParam(name = "content") Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		Iterable<?> series = GlueUtils.toSeries(original);
		StringBuilder builder = new StringBuilder();
		boolean first = true;
		for (Object object : series) {
			if (first) {
				first = false;
			}
			else {
				builder.append(separator);
			}
			builder.append(object == null ? "null" : GlueUtils.convert(object, String.class));
		}
		return builder.toString();
	}
	
	@GlueMethod(description = "Splits the given string(s) into parts using the given regex", version = 2)
	public static Object split(@GlueParam(name = "regex", description = "The regex to use to split the string(s)") final String regex, @GlueParam(name = "content", description = "The string(s) to split into parts") Object...original) {
		return GlueUtils.explode(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Arrays.asList(((String) single).split(regex));
			}
		}, String.class), false, original);
	}
}
