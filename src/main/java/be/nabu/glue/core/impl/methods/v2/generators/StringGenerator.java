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

package be.nabu.glue.core.impl.methods.v2.generators;

import java.util.Iterator;

import be.nabu.glue.core.api.CollectionIterable;
import be.nabu.glue.core.impl.methods.v2.SeriesGenerator;

public class StringGenerator implements SeriesGenerator<String> {

	private String string;

	public StringGenerator(String string) {
		this.string = string;
	}
	
	@Override
	public Iterable<String> newSeries() {
		return new CollectionIterable<String>() {
			@Override
			public Iterator<String> iterator() {
				return new Iterator<String>() {
					
					private int index = 0;
					
					@Override
					public boolean hasNext() {
						return index < string.length();
					}
					
					@Override
					public String next() {
						return string.substring(index, ++index);
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}

	@Override
	public Class<String> getSeriesClass() {
		return String.class;
	}

}
