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

package be.nabu.glue.core.impl.methods.v2;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "bucket")
public class BucketMethods {
	// can cluster this in the future?
	private static Map<String, Object> buckets = Collections.synchronizedMap(new HashMap<String, Object>());
	
	public static void store(String name, Object bucket) {
		buckets.put(name, bucket);
	}
	
	public static Object retrieve(String name) {
		return buckets.get(name);
	}
}
