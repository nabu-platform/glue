package be.nabu.glue.core.api;

public interface OptionalTypeProvider {
	public OptionalTypeConverter getConverter(String type);
}