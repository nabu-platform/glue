package be.nabu.glue.converters;

import be.nabu.libs.converter.api.ConverterProvider;
import be.nabu.libs.evaluator.date.CustomDate;

import java.util.Date;

public class CustomDateToDate implements ConverterProvider<CustomDate, Date> {

	@Override
	public Date convert(CustomDate date) {
		return date == null ? null : date.getDate();
	}

	@Override
	public Class<CustomDate> getSourceClass() {
		return CustomDate.class;
	}

	@Override
	public Class<Date> getTargetClass() {
		return Date.class;
	}
}