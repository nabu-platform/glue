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

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.util.Date;
import java.util.UUID;

import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.api.OptionalTypeConverter;
import be.nabu.glue.core.api.OptionalTypeProvider;
import be.nabu.glue.core.impl.providers.ScriptMethodProvider;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;

public class DefaultOptionalTypeProvider implements OptionalTypeProvider {

	private Converter converter = ConverterFactory.getInstance().getConverter();
	
	public static Class<?> wrapDefault(String optionalType) {
		Class<?> targetClass = null;
		if (optionalType.equalsIgnoreCase("integer")) {
			targetClass = Long.class;
		}
		else if (optionalType.equalsIgnoreCase("bigInteger")) {
			targetClass = BigInteger.class;
		}
		else if (optionalType.equalsIgnoreCase("decimal")) {
			targetClass = Double.class;
		}
		else if (optionalType.equalsIgnoreCase("bigDecimal")) {
			targetClass = BigDecimal.class;
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
		else if (optionalType.equalsIgnoreCase("stream")) {
			targetClass = InputStream.class;
		}
		else if (optionalType.equalsIgnoreCase("uuid")) {
			targetClass = UUID.class;
		}
		else if (optionalType.equalsIgnoreCase("uri")) {
			targetClass = URI.class;
		}
		else if (ScriptMethodProvider.ALLOW_LAMBDAS && optionalType.equalsIgnoreCase("lambda")) {
			targetClass = Lambda.class;
		}
		return targetClass;
	}
	
	@Override
	public OptionalTypeConverter getConverter(String optionalType) {
		Class<?> targetClass = wrapDefault(optionalType);
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
			if (object == null) {
				return null;
			}
			else if (targetClass.isAssignableFrom(object.getClass())) {
				return object;
			}
			else {
				return converter.convert(object, targetClass);
			}
		}

		@Override
		public Class<?> getComponentType() {
			return targetClass;
		}
		
	}
}
