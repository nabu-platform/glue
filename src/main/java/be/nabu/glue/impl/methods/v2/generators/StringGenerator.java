package be.nabu.glue.impl.methods.v2.generators;

import java.util.Iterator;

import be.nabu.glue.impl.methods.v2.SeriesGenerator;

public class StringGenerator implements SeriesGenerator<String> {

	private String string;

	public StringGenerator(String string) {
		this.string = string;
	}
	
	@Override
	public Iterable<String> newSeries() {
		return new Iterable<String>() {
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
