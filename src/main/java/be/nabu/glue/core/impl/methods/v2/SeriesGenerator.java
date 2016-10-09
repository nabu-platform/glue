package be.nabu.glue.core.impl.methods.v2;

public interface SeriesGenerator<T> {
	public Iterable<T> newSeries();
	public Class<T> getSeriesClass();
}
