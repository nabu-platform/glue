package be.nabu.glue.core.converters;

import be.nabu.libs.converter.api.ConverterProvider;

@SuppressWarnings("rawtypes")
public class IterableToBoolean implements ConverterProvider<Iterable, Boolean> {

	@Override
	public Boolean convert(Iterable instance) {
		boolean result = false;
		// if there is a non-null entry, it will return true
		for (Object entry : instance) {
			if (entry != null) {
				result = true;
				break;
			}
		}
		return result;
	}

	@Override
	public Class<Iterable> getSourceClass() {
		return Iterable.class;
	}

	@Override
	public Class<Boolean> getTargetClass() {
		return Boolean.class;
	}

}
