package be.nabu.glue.core.api;

public interface OptionalTypeConverter {
	public Object convert(Object object);
	public Class<?> getComponentType();
}
