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

import java.text.ParseException;
import java.util.Date;

import be.nabu.libs.converter.api.ConverterProvider;

public class StringToDate implements ConverterProvider<String, Date> {
	
	@Override
	public Date convert(String arg0) {
		try {
			return arg0 == null || arg0.trim().isEmpty() ? null : DateToString.getFormatter().parse(arg0);
		}
		catch (ParseException e) {
			return null;
		}
	}

	@Override
	public Class<String> getSourceClass() {
		return String.class;
	}

	@Override
	public Class<Date> getTargetClass() {
		return Date.class;
	}

}
