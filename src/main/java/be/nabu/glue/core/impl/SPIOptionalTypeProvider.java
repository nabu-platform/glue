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

package be.nabu.glue.core.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import be.nabu.glue.core.api.OptionalTypeConverter;
import be.nabu.glue.core.api.OptionalTypeProvider;

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
