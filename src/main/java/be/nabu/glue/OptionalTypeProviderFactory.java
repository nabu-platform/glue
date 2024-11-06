/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.glue;

import java.util.ArrayList;
import java.util.List;

import be.nabu.glue.core.api.OptionalTypeProvider;
import be.nabu.glue.core.impl.FallbackOptionalTypeProvider;
import be.nabu.glue.core.impl.MultipleOptionalTypeProvider;
import be.nabu.glue.core.impl.SPIOptionalTypeProvider;

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
			providers.add(new FallbackOptionalTypeProvider());
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
