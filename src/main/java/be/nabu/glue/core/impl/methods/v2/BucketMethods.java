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
