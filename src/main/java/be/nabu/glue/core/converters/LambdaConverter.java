package be.nabu.glue.core.converters;

import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.impl.operations.LambdaProxy;
import be.nabu.libs.converter.api.Converter;

public class LambdaConverter implements Converter {

	@Override
	public <T> T convert(Object instance, Class<T> targetClass) {
		// we try to convert it to the lambda interface itself!
		return instance instanceof Lambda && targetClass.isInterface() && !targetClass.isAssignableFrom(instance.getClass()) ? LambdaProxy.newInstance(targetClass, (Lambda) instance) : null;
	}

	@Override
	public boolean canConvert(Class<?> instanceClass, Class<?> targetClass) {
		return Lambda.class.isAssignableFrom(instanceClass) && targetClass.isInterface();
	}

}
