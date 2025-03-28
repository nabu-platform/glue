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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.GlueUtils.ObjectHandler;
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
				// if we have only numbers, we assume it is a timestamp
				if (((String) single).matches("^[0-9]+$")) {
					return new Date(Long.parseLong((String) single));
				}
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
				String dateToParse = (String) single;
				String formatToUse = format;
				if (formatToUse == null) {
					if (dateToParse.matches("[0-9]{4}")) {
						formatToUse = "yyyy";
					}
					else if (dateToParse.matches("[0-9]{4}-[0-9]{2}")) {
						formatToUse = "yyyy-MM";
					}
					else if (dateToParse.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
						formatToUse = "yyyy-MM-dd";
					}
					else {
						formatToUse = "yyyy-MM-dd'T'HH:mm:ss.SSS";
					}
				}
				SimpleDateFormat formatter = new SimpleDateFormat(
						formatToUse, 
					language == null ? Locale.getDefault() : new Locale(language)
				);
				formatter.setTimeZone(timezone == null ? TimeZone.getDefault() : timezone);
				try {
					return formatter.parse(dateToParse);
				}
				catch (ParseException e) {
					throw new RuntimeException(e);
				}
			}
		}, String.class), false, original);
	}
}
