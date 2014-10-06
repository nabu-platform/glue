package be.nabu.glue.impl.methods;

public class TestMethods {
	
	public static void validate(Boolean assertion, String text) {
		if (assertion == null || !assertion) {
			throw new AssertionError(text);
		}
	}
	
	public static boolean not(Boolean assertion) {
		return assertion == null || !assertion ? true : false;
	}
}
