package be.nabu.glue.core.impl;

import be.nabu.glue.core.api.OptionalTypeConverter;
import be.nabu.glue.core.api.OptionalTypeProvider;
import be.nabu.glue.core.impl.DefaultOptionalTypeProvider.DefaultTypeConverter;
import be.nabu.libs.converter.ConverterFactory;

public class FallbackOptionalTypeProvider implements OptionalTypeProvider {

	@Override
	public OptionalTypeConverter getConverter(String optionalType) {
		try {
			Class<?> targetClass = Thread.currentThread().getContextClassLoader().loadClass(optionalType);
			return targetClass != null ? new DefaultTypeConverter(ConverterFactory.getInstance().getConverter(), targetClass) : null;
		}
		catch (ClassNotFoundException e) {
			return null;
		}
	}

}
