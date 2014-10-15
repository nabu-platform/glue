package be.nabu.glue.impl.methods;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import be.nabu.glue.ScriptRuntime;

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
	
	public static byte [] zip(String...fileNames) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ZipOutputStream zip = new ZipOutputStream(output);
		for (String fileName : fileNames) {
			Object content = ScriptMethods.file(fileName);
			if (content == null) {
				throw new FileNotFoundException("Could not find file " + fileName);
			}
			if (content instanceof String) {
				content = ScriptRuntime.getRuntime().getScript().getParser().substitute((String) content, ScriptRuntime.getRuntime().getExecutionContext()).getBytes();
			}
			ZipEntry entry = new ZipEntry(fileName);
			zip.putNextEntry(entry);
			zip.write((byte[]) content);
		}
		zip.close();
		return output.toByteArray();
	}
}
