package be.nabu.glue;

import java.util.ArrayList;
import java.util.List;

import be.nabu.glue.api.OptionalTypeProvider;
import be.nabu.glue.impl.MultipleOptionalTypeProvider;
import be.nabu.glue.impl.SPIOptionalTypeProvider;

public class OptionalTypeProviderFactory {
	
	private static OptionalTypeProviderFactory instance;
	
	public static OptionalTypeProviderFactory getInstance() {
		if (instance == null) {
			synchronized(OptionalTypeProviderFactory.class) {
				if (instance == null) {
					instance = new OptionalTypeProviderFactory();
				}
			}
		}
		return instance;
	}
	
	private OptionalTypeProvider mainProvider;
	private List<OptionalTypeProvider> providers = new ArrayList<OptionalTypeProvider>();

	public OptionalTypeProvider getProvider() {
		if (mainProvider == null) {
			synchronized(this) {
				if (mainProvider == null) {
					mainProvider = new MultipleOptionalTypeProvider(getProviders());
				}
			}
		}
		return mainProvider;
	}

	public void setProvider(OptionalTypeProvider provider) {
		this.mainProvider = provider;
	}
	
	public List<OptionalTypeProvider> getProviders() {
		if (providers.isEmpty()) {
			providers.add(new SPIOptionalTypeProvider());
		}
		return providers;
	}
	
	public void addProvider(OptionalTypeProvider provider) {
		providers.add(provider);
		mainProvider = null;
	}
	
 	@SuppressWarnings("unused")
	private void activate() {
		instance = this;
	}
	@SuppressWarnings("unused")
	private void deactivate() {
		instance = null;
	}
}
