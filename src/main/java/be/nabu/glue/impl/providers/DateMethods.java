package be.nabu.glue.impl.providers;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.libs.evaluator.QueryPart.Type;
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
	
	@Deprecated // check date()
	@GlueMethod(description = "Generates a date object that points to the current time")
	public static CustomDate now() {
		return new CustomDate();
	}

	/**
	 * This method tries to deduce the format from the value
	 */
	@GlueMethod(description = "Tries to automatically parse the given value into a correct date object")
	public static CustomDate date(@GlueParam(name = "date", description = "The value pointing to the date you want to parse", defaultValue = "The current date") String value) throws ParseException {
		if (value == null) {
			return new CustomDate();
		}
		else {
			for (String regex : dateFormats.keySet()) {
				if (value.matches(regex)) {
					return parse(value, dateFormats.get(regex), null, null);
				}
			}
			throw new ParseException("The date has an unknown format: " + value, 0);
		}
	}
	
	@GlueMethod(description = "Formats the given date to the given format")
	public static String format(
			@GlueParam(name = "date", description = "The date that you want to format", defaultValue = "The current date") CustomDate date, 
			@GlueParam(name = "format", description = "The format you want to use", defaultValue = "The XML Schema dateTime format") String format, 
			@GlueParam(name = "timezone", description = "The timezone you want to use", defaultValue = "The default timezone for the JVM") TimeZone timezone, 
			@GlueParam(name = "language", description = "The language you want to use, this will impact things like printing out the full day name", defaultValue = "The default language of the JVM") String language) {
		SimpleDateFormat formatter = new SimpleDateFormat(
			format == null ? "yyyy-MM-dd'T'HH:mm:ss.SSS" : format, 
			language == null ? Locale.getDefault() : new Locale(language)
		);
		formatter.setTimeZone(timezone == null ? TimeZone.getDefault() : timezone);
		return formatter.format(date == null ? new Date() : date.getDate());	
	}
	
	@GlueMethod(description = "Parses a string into a date using the given format")
	public static CustomDate parse(
			@GlueParam(name = "date", description = "The date that you want to format") String date, 
			@GlueParam(name = "format", description = "The format you want to use", defaultValue = "The XML Schema dateTime format") String format, 
			@GlueParam(name = "timezone", description = "The timezone you want to use", defaultValue = "The default timezone for the JVM") TimeZone timezone, 
			@GlueParam(name = "language", description = "The language you want to use, this will impact things like printing out the full day name", defaultValue = "The default language of the JVM") String language) throws ParseException {
		if (date == null) {
			return null;
		}
		SimpleDateFormat formatter = new SimpleDateFormat(
			format == null ? "yyyy-MM-dd'T'HH:mm:ss.SSS" : format, 
			language == null ? Locale.getDefault() : new Locale(language)
		);
		formatter.setTimeZone(timezone == null ? TimeZone.getDefault() : timezone);
		return new CustomDate(formatter.parse(date));
	}
	
	@GlueMethod(description = "Generates a range of dates between the from and to the date (both inclusive) using the given increment")
	public static CustomDate [] range(
			@GlueParam(name = "from", description = "The date to start from", defaultValue = "The current time") CustomDate from, 
			@GlueParam(name = "to", description = "The end date of the range", defaultValue = "The current time") CustomDate to, 
			@GlueParam(name = "increment", description = "The increment to use, this follows general date rules", defaultValue = "1day") String increment) {
		if (from == null) {
			from = new CustomDate();
		}
		if (to == null) {
			to = new CustomDate();
		}
		if (increment == null) {
			increment = "1day";
		}
		List<CustomDate> dates = new ArrayList<CustomDate>();
		while (!from.getDate().after(to.getDate())) {
			dates.add(from);
			from = CustomDate.increment(from, increment, Type.ADD);
		}
		return dates.toArray(new CustomDate[dates.size()]);
	}

	@GlueMethod(description = "Increments the given date with the given amount")
	public static CustomDate increment(
			@GlueParam(name = "amount", description = "The amount to increment", defaultValue = "1") Integer amount, 
			@GlueParam(name = "type", description = "The type of increase, e.g. 'day'", defaultValue = "day") String type, 
			@GlueParam(name = "date", description = "The date to increment", defaultValue = "The current date") CustomDate date) {
		if (amount == null) {
			amount = 1;
		}
		if (type == null) {
			type = "day";
		}
		if (date == null) {
			date = new CustomDate();
		}
		return CustomDate.increment(date, "" + Math.abs(amount) + type, amount < 0 ? Type.SUBSTRACT : Type.ADD);
	}
	
	@GlueMethod(description = "Decrements the given date with the given amount")
	public static CustomDate decrement(
			@GlueParam(name = "amount", description = "The amount to decrement", defaultValue = "-1") Integer amount,
			@GlueParam(name = "type", description = "The type of decrease, e.g. 'day'", defaultValue = "day") String type, 
			@GlueParam(name = "date", description = "The date to decrement", defaultValue = "The current date") CustomDate date) {
		if (amount == null) {
			amount = -1;
		}
		if (type == null) {
			type = "day";
		}
		if (date == null) {
			date = new CustomDate();
		}	
		return CustomDate.increment(date, "" + Math.abs(amount) + type, amount < 0 ? Type.ADD : Type.SUBSTRACT);
	}
	
	@GlueMethod(description = "Generates a numeric timestamp for the given date")
	public static long timestamp(@GlueParam(name = "date", description = "The date to generate the timestamp for", defaultValue = "The current date") CustomDate date) {
		return date == null ? new Date().getTime() : date.getDate().getTime();
	}
}
