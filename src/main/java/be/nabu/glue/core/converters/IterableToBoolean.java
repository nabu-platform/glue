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
