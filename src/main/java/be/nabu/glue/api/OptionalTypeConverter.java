package be.nabu.glue.api;

public interface OptionalTypeConverter {
	public Object convert(Object object);
	public Class<?> getComponentType();
}
