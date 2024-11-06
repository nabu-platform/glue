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
