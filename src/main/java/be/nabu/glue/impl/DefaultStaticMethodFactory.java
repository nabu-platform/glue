package be.nabu.glue.impl;

import java.util.ArrayList;
import java.util.List;

import be.nabu.glue.api.StaticMethodFactory;
import be.nabu.glue.impl.methods.FileMethods;
import be.nabu.glue.impl.methods.ScriptMethods;
import be.nabu.glue.impl.methods.StringMethods;
import be.nabu.glue.impl.methods.SystemMethods;
import be.nabu.glue.impl.methods.TestMethods;

public class DefaultStaticMethodFactory implements StaticMethodFactory {

	@Override
	public List<Class<?>> getStaticMethodClasses() {
		List<Class<?>> classes = new ArrayList<Class<?>>();
		classes.add(ScriptMethods.class);
		classes.add(FileMethods.class);
		classes.add(TestMethods.class);
		classes.add(StringMethods.class);
		classes.add(SystemMethods.class);
		return classes;
	}
}
