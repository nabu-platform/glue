package be.nabu.glue.core.converters;

import be.nabu.libs.converter.api.ConverterProvider;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateToString implements ConverterProvider<Date, String> {

	private static ThreadLocal<SimpleDateFormat> formatter = new ThreadLocal<SimpleDateFormat>();
	
	@Override
	public String convert(Date date) {
		return date == null ? null : getFormatter().format(date);
	}

	@Override
	public Class<Date> getSourceClass() {
		return Date.class;
	}

	@Override
	public Class<String> getTargetClass() {
		return String.class;
	}

	public static SimpleDateFormat getFormatter() {
		if (formatter.get() == null) {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
			formatter.set(simpleDateFormat);
		}
		return formatter.get();
	}
}
