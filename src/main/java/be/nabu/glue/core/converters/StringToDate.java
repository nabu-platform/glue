package be.nabu.glue.core.converters;

import java.text.ParseException;
import java.util.Date;

import be.nabu.libs.converter.api.ConverterProvider;

public class StringToDate implements ConverterProvider<String, Date> {
	
	@Override
	public Date convert(String arg0) {
		try {
			return arg0 == null || arg0.trim().isEmpty() ? null : DateToString.getFormatter().parse(arg0);
		}
		catch (ParseException e) {
			return null;
		}
	}

	@Override
	public Class<String> getSourceClass() {
		return String.class;
	}

	@Override
	public Class<Date> getTargetClass() {
		return Date.class;
	}

}
