package be.nabu.glue.core.impl.methods;

import java.io.File;

public class ShellMethods {

	public static String pwd() {
		// remove the scheme
		return new File("").toURI().getPath();
	}
	
}
