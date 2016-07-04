package be.nabu.glue.impl.methods.v2;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.impl.GlueUtils;
import be.nabu.glue.impl.GlueUtils.ObjectHandler;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "date")
public class DateMethods {
	
	private static Map<String, String> dateFormats; static {
		dateFormats = new LinkedHashMap<String, String>();
		dateFormats.put("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{1,3}$", "yyyy-MM-dd'T'HH:mm:ss.S");
		dateFormats.put("^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{1,3}$", "yyyy-MM-dd HH:mm:ss.S");
		dateFormats.put("^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}$", "yyyy-MM-dd HH:mm:ss");
		dateFormats.put("^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}$", "yyyy-MM-dd HH:mm");
		dateFormats.put("^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}$", "yyyy-MM-dd HH");
		dateFormats.put("^[0-9]{4}-[0-9]{2}-[0-9]{2}$", "yyyy-MM-dd");
		dateFormats.put("^[0-9]{4}-[0-9]{2}$", "yyyy-MM");
		dateFormats.put("^[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{1,3}$", "HH:mm:ss.S");
		dateFormats.put("^[0-9]{2}:[0-9]{2}:[0-9]{2}$", "HH:mm:ss");
		dateFormats.put("^[0-9]{2}:[0-9]{2}$", "HH:mm");
		dateFormats.put("^[0-9]{2}$", "HH");
		
		dateFormats.put("^[0-9]{4}/[0-9]{2}/[0-9]{2}$", "yyyy/MM/dd");
		dateFormats.put("^[0-9]{4}/[0-9]{2}$", "yyyy/MM");
		dateFormats.put("^[0-9]{2}/[0-9]{2}/[0-9]{4}$", "dd/MM/yyyy");
		dateFormats.put("^[0-9]{2}/[0-9]{4}$", "MM/yyyy");
	}
	
	@GlueMethod(description = "Tries to automatically parse the given value into a correct date object", version = 2)
	public static Object date(@GlueParam(name = "date", description = "The value pointing to the date you want to parse", defaultValue = "The current date") Object...original) {
		if (original == null || original.length == 0) {
			return new Date();
		}
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				for (String regex : dateFormats.keySet()) {
					if (((String) single).matches(regex)) {
						SimpleDateFormat formatter = new SimpleDateFormat(dateFormats.get(regex));
						try {
							return formatter.parseObject((String) single);
						}
						catch (ParseException e) {
							throw new RuntimeException(e);
						}
					}
				}
				throw new RuntimeException("Unknown format: " + single);
			}
		}, String.class), false, original);
	}

	@GlueMethod(description = "Formats the given date to the given format", version = 2)
	public static Object format( 
			@GlueParam(name = "format", description = "The format you want to use", defaultValue = "The XML Schema dateTime format") final String format, 
			@GlueParam(name = "timezone", description = "The timezone you want to use", defaultValue = "The default timezone for the JVM") final TimeZone timezone, 
			@GlueParam(name = "language", description = "The language you want to use, this will impact things like printing out the full day name", defaultValue = "The default language of the JVM") final String language,
			@GlueParam(name = "date", description = "The date that you want to format", defaultValue = "The current date") Object...original) {
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				SimpleDateFormat formatter = new SimpleDateFormat(
					format == null ? "yyyy-MM-dd'T'HH:mm:ss.SSS" : format, 
					language == null ? Locale.getDefault() : new Locale(language)
				);
				formatter.setTimeZone(timezone == null ? TimeZone.getDefault() : timezone);
				return formatter.format((Date) single);	
			}
		}, Date.class), false, original);
	}
	
	@GlueMethod(description = "Parses a string into a date using the given format", version = 2)
	public static Object parse(
			@GlueParam(name = "format", description = "The format you want to use", defaultValue = "The XML Schema dateTime format") final String format, 
			@GlueParam(name = "timezone", description = "The timezone you want to use", defaultValue = "The default timezone for the JVM") final TimeZone timezone, 
			@GlueParam(name = "language", description = "The language you want to use, this will impact things like printing out the full day name", defaultValue = "The default language of the JVM") final String language,
			@GlueParam(name = "date", description = "The date that you want to format") Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		return GlueUtils.wrap(GlueUtils.cast(new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				SimpleDateFormat formatter = new SimpleDateFormat(
					format == null ? "yyyy-MM-dd'T'HH:mm:ss.SSS" : format, 
					language == null ? Locale.getDefault() : new Locale(language)
				);
				formatter.setTimeZone(timezone == null ? TimeZone.getDefault() : timezone);
				try {
					return formatter.parse((String) single);
				}
				catch (ParseException e) {
					throw new RuntimeException(e);
				}
			}
		}, String.class), false, original);
	}
}
