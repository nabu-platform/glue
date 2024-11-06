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

package be.nabu.glue.core.converters;

import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.impl.operations.LambdaProxy;
import be.nabu.libs.converter.api.Converter;

public class LambdaConverter implements Converter {

	@Override
	public <T> T convert(Object instance, Class<T> targetClass) {
		// we try to convert it to the lambda interface itself!
		return instance instanceof Lambda && targetClass.isInterface() && !targetClass.isAssignableFrom(instance.getClass()) ? LambdaProxy.newInstance(targetClass, (Lambda) instance) : null;
	}

	@Override
	public boolean canConvert(Class<?> instanceClass, Class<?> targetClass) {
		return Lambda.class.isAssignableFrom(instanceClass) && targetClass.isInterface();
	}

}
