package be.nabu.glue.impl;

import java.util.Date;

import be.nabu.glue.api.OptionalTypeProvider;
import be.nabu.glue.api.OptionalTypeConverter;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;

public class DefaultOptionalTypeProvider implements OptionalTypeProvider {

	private Converter converter = ConverterFactory.getInstance().getConverter();
	
	public static Class<?> wrapDefault(String optionalType) {
		Class<?> targetClass = null;
		if (optionalType.equalsIgnoreCase("integer")) {
			targetClass = Long.class;
		}
		else if (optionalType.equalsIgnoreCase("decimal")) {
			targetClass = Double.class;
		}
		else if (optionalType.equalsIgnoreCase("date")) {
			targetClass = Date.class;
		}
		else if (optionalType.equalsIgnoreCase("string")) {
			targetClass = String.class;
		}
		else if (optionalType.equalsIgnoreCase("boolean")) {
			targetClass = Boolean.class;
		}
		else if (optionalType.equalsIgnoreCase("bytes")) {
			targetClass = byte[].class;
		}
		return targetClass;
	}
	
	@Override
	public OptionalTypeConverter getConverter(String optionalType) {
		Class<?> targetClass = wrapDefault(optionalType);
		if (targetClass == null) {
			try {
				targetClass = Thread.currentThread().getContextClassLoader().loadClass(optionalType);
			}
			catch (ClassNotFoundException e) {
				// ignore
			}
		}
		return targetClass != null ? new DefaultTypeConverter(converter, targetClass) : null;
	}

	public static class DefaultTypeConverter implements OptionalTypeConverter {

		private Converter converter;
		private Class<?> targetClass;

		public DefaultTypeConverter(Converter converter, Class<?> targetClass) {
			this.converter = converter;
			this.targetClass = targetClass;
		}
		
		@Override
		public Object convert(Object object) {
			return object == null ? null : converter.convert(object, targetClass);
		}

		@Override
		public Class<?> getComponentType() {
			return targetClass;
		}
		
	}
}
