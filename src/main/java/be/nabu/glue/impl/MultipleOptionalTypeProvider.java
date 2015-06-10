package be.nabu.glue.impl;

import java.util.List;

import be.nabu.glue.api.OptionalTypeConverter;
import be.nabu.glue.api.OptionalTypeProvider;

public class MultipleOptionalTypeProvider implements OptionalTypeProvider {

	private List<OptionalTypeProvider> providers;
	
	public MultipleOptionalTypeProvider(List<OptionalTypeProvider> providers) {
		this.providers = providers;
	}

	@Override
	public OptionalTypeConverter getConverter(String type) {
		OptionalTypeConverter converter = null;
		for (OptionalTypeProvider provider : providers) {
			converter = provider.getConverter(type);
			if (converter != null) {
				break;
			}
		}
		return converter;
	}

}
