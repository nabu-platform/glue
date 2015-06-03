package be.nabu.glue.api;

public interface OptionalTypeProvider {
	public OptionalTypeConverter getConverter(String type);
}