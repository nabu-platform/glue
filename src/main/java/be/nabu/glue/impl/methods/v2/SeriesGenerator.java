package be.nabu.glue.impl.methods.v2;

public interface SeriesGenerator<T> {
	public Iterable<T> newSeries();
	public Class<T> getSeriesClass();
}
