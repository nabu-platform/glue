package be.nabu.glue.impl.methods;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringMethods {
	public static String find(String original, String regex) {
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(original);
		if (matcher.find()) {
			if (matcher.groupCount() > 0) {
				return matcher.group(1);
			}
			else {
				return matcher.group();
			}
		}
		return null;
	}
	
	public static String replace(String original, String regex, String replacement) {
		return original.replaceAll(regex, replacement);
	}
}
