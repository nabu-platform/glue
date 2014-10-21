package be.nabu.glue.impl.methods;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringMethods {
	
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
	
	public static String join(String separator, String...strings) {
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