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

package be.nabu.glue.core.impl.methods;

import java.util.Random;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "math")
public class MathMethods {
	
	private static Random random = new Random();
	
	@GlueMethod(description = "Generates a random integer")
	public static int randomInteger(@GlueParam(name = "max", description = "The maximum value of the integer", defaultValue = "The maximum value of integers") Integer max) {
		return max == null ? random.nextInt() : random.nextInt(max);
	}
	
	@GlueMethod(description = "Generates a random decimal")
	public static double randomDecimal() {
		return random.nextDouble();
	}
	
	@GlueMethod(description = "Returns the absolute value of the object")
	public static Object absolute(@GlueParam(name = "number", description = "The number to return the absolute value of") Object number) {
		if (number instanceof Double) {
			return Math.abs((Double) number);
		}
		else if (number instanceof Long) {
			return Math.abs((Long) number);
		}
		else if (number instanceof Float) {
			return Math.abs((Float) number);
		}
		else if (number instanceof Integer) {
			return Math.abs((Integer) number);
		}
		// if it is a string without a decimal separator, use long
		else if (number instanceof String && ((String) number).matches("[0-9]+")) {
			return Math.abs(ConverterFactory.getInstance().getConverter().convert(number, Long.class));
		}
		// otherwise just try to convert to double
		else {
			return Math.abs(ConverterFactory.getInstance().getConverter().convert(number, Double.class));
		}
	}
}
