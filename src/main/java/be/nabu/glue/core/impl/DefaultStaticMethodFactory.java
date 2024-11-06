/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.glue.core.impl;

import java.util.ArrayList;
import java.util.List;

import be.nabu.glue.core.api.StaticMethodFactory;
import be.nabu.glue.core.impl.methods.FileMethods;
import be.nabu.glue.core.impl.methods.HTTPMethods;
import be.nabu.glue.core.impl.methods.MathMethods;
import be.nabu.glue.core.impl.methods.ReflectionMethods;
import be.nabu.glue.core.impl.methods.ScriptMethods;
import be.nabu.glue.core.impl.methods.StringMethods;
import be.nabu.glue.core.impl.methods.SystemMethods;
import be.nabu.glue.core.impl.methods.TestMethods;
import be.nabu.glue.core.impl.providers.DateMethods;

public class DefaultStaticMethodFactory implements StaticMethodFactory {

	@Override
	public List<Class<?>> getStaticMethodClasses() {
		List<Class<?>> classes = new ArrayList<Class<?>>();
		classes.add(ScriptMethods.class);
		classes.add(ReflectionMethods.class);
		classes.add(FileMethods.class);
		classes.add(TestMethods.class);
		classes.add(StringMethods.class);
		classes.add(SystemMethods.class);
		classes.add(DateMethods.class);
		classes.add(MathMethods.class);
		classes.add(HTTPMethods.class);
		// v2
		classes.add(be.nabu.glue.core.impl.methods.v2.StringMethods.class);
		classes.add(be.nabu.glue.core.impl.methods.v2.ScriptMethods.class);
		classes.add(be.nabu.glue.core.impl.methods.v2.SeriesMethods.class);
		classes.add(be.nabu.glue.core.impl.methods.v2.DateMethods.class);
		classes.add(be.nabu.glue.core.impl.methods.v2.BucketMethods.class);
		classes.add(be.nabu.glue.core.impl.methods.v2.ParallelMethods.class);
		classes.add(be.nabu.glue.core.impl.methods.v2.MathMethods.class);
		classes.add(be.nabu.glue.core.impl.methods.v2.HTTPMethods.class);
		classes.add(be.nabu.glue.core.impl.methods.v2.HashMethods.class);
		classes.add(be.nabu.glue.core.impl.methods.v2.EncryptionMethods.class);
		classes.add(be.nabu.glue.core.impl.methods.v2.ContextMethods.class);
		classes.add(be.nabu.glue.core.impl.methods.v2.FileMethods.class);
		return classes;
	}
}
