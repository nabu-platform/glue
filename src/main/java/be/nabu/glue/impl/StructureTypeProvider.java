package be.nabu.glue.impl;

import java.io.IOException;
import java.text.ParseException;

import be.nabu.glue.api.OptionalTypeConverter;
import be.nabu.glue.api.OptionalTypeProvider;
import be.nabu.glue.api.Script;
import be.nabu.glue.impl.methods.ReflectionMethods;

public class StructureTypeProvider implements OptionalTypeProvider {

	@Override
	public OptionalTypeConverter getConverter(String type) {
		try {
			Script script = ReflectionMethods.getRepository().getScript(type);
			if (script != null) {
				return new StructureTypeConverter(script);
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		catch (ParseException e) {
			// ignore
		}
		return null;
	}

	public static class StructureTypeConverter implements OptionalTypeConverter {

		private Script script;

		public StructureTypeConverter(Script script) {
			this.script = script;
		}
		
		@Override
		public Object convert(Object object) {
			return null;
		}
		
	}
}
