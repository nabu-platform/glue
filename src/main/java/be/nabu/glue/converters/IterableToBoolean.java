package be.nabu.glue.converters;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.ConverterProvider;

@SuppressWarnings("rawtypes")
public class IterableToBoolean implements ConverterProvider<Iterable, Boolean> {

	@Override
	public Boolean convert(Iterable instance) {
		boolean result = true;
		for (Object entry : instance) {
			if (entry == null) {
				result &= false;
			}
			else {
				Boolean bool = entry instanceof Boolean ? (Boolean) entry : ConverterFactory.getInstance().getConverter().convert(entry, Boolean.class);
				if (bool == null) {
					throw new RuntimeException("Can not convert entry in iterable to boolean: " + entry);
				}
				result &= bool;
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
