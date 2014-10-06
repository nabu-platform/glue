package be.nabu.glue.impl.methods;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileMethods {
	
	public static InputStream read(String fileName) throws FileNotFoundException {
		return new FileInputStream(resolve(fileName));
	}
	
	public static void write(String fileName, Object content) throws IOException {
		if (content != null) {
			InputStream input = ScriptMethods.toStream(content);
			try {
				FileOutputStream output = new FileOutputStream(resolve(fileName));
				try {
					int read = 0;
					byte [] buffer = new byte[4096];
					while ((read = input.read(buffer)) != -1) {
						output.write(buffer, 0, read);
					}
				}
				finally {
					output.close();
				}
			}
			finally {
				input.close();
			}
		}
	}
	
	public static void remove(String fileName) {
		resolve(fileName).delete();
	}
	
	/**
	 * Allow for some kind of traversal system (start in user.home)
	 */
	private static File resolve(String fileName) {
		return new File(fileName);
	}
}
