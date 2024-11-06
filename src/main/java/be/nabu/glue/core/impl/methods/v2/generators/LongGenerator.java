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

public class LongGenerator implements SeriesGenerator<Long> {

	private Long start;

	public LongGenerator(Long start) {
		this.start = start;
	}
	
	@Override
	public Iterable<Long> newSeries() {
		return new CollectionIterable<Long>() {
			@Override
			public Iterator<Long> iterator() {
				return new Iterator<Long>() {
					private long current = start == null ? 0 : start;
					
					@Override
					public boolean hasNext() {
						return true;
					}
					
					@Override
					public Long next() {
						return current++;
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
	public Class<Long> getSeriesClass() {
		return Long.class;
	}

}
