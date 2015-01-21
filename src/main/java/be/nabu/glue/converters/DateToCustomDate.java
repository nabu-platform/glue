package be.nabu.glue.converters;

import be.nabu.libs.converter.api.ConverterProvider;
import be.nabu.libs.evaluator.date.CustomDate;

import java.util.Date;

public class DateToCustomDate implements ConverterProvider<Date, CustomDate> {

	@Override
	public CustomDate convert(Date date) {
		return date == null ? null : new CustomDate(date);
	}

	@Override
	public Class<Date> getSourceClass() {
		return Date.class;
	}

	@Override
	public Class<CustomDate> getTargetClass() {
		return CustomDate.class;
	}
}
