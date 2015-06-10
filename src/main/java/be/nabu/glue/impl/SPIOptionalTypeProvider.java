package be.nabu.glue.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import be.nabu.glue.api.OptionalTypeConverter;
import be.nabu.glue.api.OptionalTypeProvider;

public class SPIOptionalTypeProvider implements OptionalTypeProvider {

	private List<OptionalTypeProvider> providers;
	
	@Override
	public OptionalTypeConverter getConverter(String type) {
		OptionalTypeConverter converter = null;
		for (OptionalTypeProvider provider : getProviders()) {
			converter = provider.getConverter(type);
			if (converter != null) {
				break;
			}
		}
		return converter;
	}
	
	private List<OptionalTypeProvider> getProviders() {
		if (providers == null) {
			synchronized(this) {
				if (providers == null) {
					List<OptionalTypeProvider> providers = new ArrayList<OptionalTypeProvider>();
					for (OptionalTypeProvider provider : ServiceLoader.load(OptionalTypeProvider.class)) {
						providers.add(provider);
					}
					this.providers = providers;
				}
			}
		}
		return providers;
	}

}
