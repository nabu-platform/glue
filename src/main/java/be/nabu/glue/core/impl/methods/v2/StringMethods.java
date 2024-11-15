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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.GlueUtils.ObjectHandler;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "string")
public class StringMethods {
	
	@GlueMethod(description = "Adds the given pad to the given string(s) on the right until they reach the required length", returns = "The padded string(s)", version = 2)
	public static Object padRight(
			@GlueParam(name = "pad", description = "The string used to pad") String pad, 
			@GlueParam(name = "length", description = "The length of the resulting string") int length, 
			@GlueParam(name = "string", description = "The string(s) to be padded") Object...original) {
		return pad(pad, length, true, original);
	}
	
	@GlueMethod(description = "Adds the given pad to the given string(s) on the left until they reach the required length", returns = "The padded string(s)", version = 2)
	public static Object padLeft(
			@GlueParam(name = "pad", description = "The string used to pad") String pad, 
			@GlueParam(name = "length", description = "The length of the resulting string") int length, 
			@GlueParam(name = "string", description = "The string(s) to be padded") Object...original) {
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
			int padLength = Math.min(pad.length(), length - value.length());
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
	private static Object pad(
			@GlueParam(name = "pad", description = "The string to pad with") final String pad, 
			@GlueParam(name = "length", description = "The length the resulting string(s) should be") final int length, 
			@GlueParam(name = "leftAlign", description = "Whether to left align the original string(s)") final boolean leftAlign, 
			@GlueParam(name = "string", description = "The string(s) to pad") Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return padSingle(pad, length, leftAlign, single);
			}
		}, String.class), true, original);
	}

	@GlueMethod(description = "Uppercases the string(s)", version = 2)
	public static Object upper(@GlueParam(name = "string", description = "One or more strings") Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return ((String) single).toUpperCase();
			}
		}, String.class), false, original);
	}

	@GlueMethod(description = "Lowercases the string(s)", version = 2)
	public static Object lower(@GlueParam(name = "string", description = "One or more strings") Object...original) {
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
			@GlueParam(name = "string", description = "One or more strings") Object...original) {
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
			@GlueParam(name = "replacement", description = "The replacement to replace it with") final Object replacement, 
			@GlueParam(name = "string", description = "The string(s) to perform the replacement on") Object...original) {
		final Pattern pattern = Pattern.compile(regex);
		final ScriptRuntime runtime = ScriptRuntime.getRuntime();
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				// if it is a lambda, we expect a different replacement for each match (even if they are the exact same match)
				if (replacement instanceof Lambda) {
					String text = (String) single;
					Matcher matcher = pattern.matcher(text);
					StringBuilder builder = new StringBuilder();
					int lastPosition = 0;
					ScriptRuntime current = ScriptRuntime.getRuntime();
					runtime.registerInThread();
					try {
						while (matcher.find()) {
							if (matcher.start() > lastPosition) {
								builder.append(text.substring(lastPosition, matcher.start()));
							}
							builder.append(GlueUtils.calculate((Lambda) replacement, runtime, Arrays.asList(matcher.group())));
							lastPosition = matcher.end();
						}
					}
					finally {
						if (current != null) {
							current.registerInThread();
						}
						else {
							runtime.unregisterInThread();
						}
					}
					if (lastPosition < text.length()) {
						builder.append(text.substring(lastPosition, text.length()));
					}
					return builder.toString();
				}
				// if it is a string, we expect a fixed replacement for all matches
				else {
					return ((String) single).replaceAll(regex, GlueUtils.convert(replacement, String.class));
				}
			}
		}, String.class), false, original);
	}
	
	@GlueMethod(version = 2)
	public static Object quoteRegex(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Pattern.quote(((String) single));
			}
		}, String.class), false, original);
	}
	
	@GlueMethod(version = 2)
	public static Object quoteReplacement(Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Matcher.quoteReplacement(((String) single));
			}
		}, String.class), false, original);
	}
	
	@GlueMethod(description = "Finds all the results matching the regex in the given string(s)", version = 2)
	public static Object find(@GlueParam(name = "regex", description = "The regex to match") String regex, @GlueParam(name = "string", description = "The string(s) to perform the find on") Object...original) {
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
	public static Object lines(@GlueParam(name = "string", description = "The string(s) to split into lines") Object...original) {
		return GlueUtils.explode(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Arrays.asList(((String) single).split("[\\r\\n]+"));
			}
		}, String.class), false, original);
	}
	
	@GlueMethod(description = "Splits the given string into columns, if you want more control over the separators, use split()", version = 2)
	public static Object columns(@GlueParam(name = "string", description = "The string to split into columns") Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Arrays.asList(((String) single).split("[\t,;]+"));
			}
		}, String.class), false, original);
	}
	
	@GlueMethod(description = "Removes any leading and trailing whitespace from the given string(s)", version = 2)
	public static Object trim(@GlueParam(name = "string", description = "One or more strings") Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return ((String) single).trim();
			}
		}, String.class), false, original);
	}
	
	@GlueMethod(description = "Combines the given strings into a single string adding the seperator in between each string. For example join(',', 'a', 'b') returns 'a,b'", version = 2)
	public static String join(@GlueParam(name = "separator") String separator, @GlueParam(name = "string") Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		Iterable<?> series = SeriesMethods.resolve(GlueUtils.toSeries(original));
		StringBuilder builder = new StringBuilder();
		boolean first = true;
		for (Object object : series) {
			if (first) {
				first = false;
			}
			else if (separator != null) {
				builder.append(separator);
			}
			builder.append(object == null ? "null" : GlueUtils.convert(object, String.class));
		}
		return builder.toString();
	}
	
	@GlueMethod(description = "Splits the given string(s) into parts using the given regex", version = 2)
	public static Object split(@GlueParam(name = "regex", description = "The regex to use to split the string(s)") final String regex, @GlueParam(name = "string", description = "The string(s) to split into parts") Object...original) {
		return GlueUtils.explode(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				return Arrays.asList(((String) single).split(regex));
			}
		}, String.class), false, original);
	}
}
