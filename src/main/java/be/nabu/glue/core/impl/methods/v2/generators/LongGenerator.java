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
