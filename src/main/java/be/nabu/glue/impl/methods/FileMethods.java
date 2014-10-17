package be.nabu.glue.impl.methods;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import be.nabu.glue.ScriptRuntime;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.Container;

public class FileMethods {
	
	public static InputStream read(String fileName) throws FileNotFoundException {
		return new FileInputStream(resolve(fileName));
	}
	
	public static String [] list(String path, String fileRegex, String directoryRegex, boolean recursive) {
		return list(new File(path), fileRegex, directoryRegex, recursive, null).toArray(new String[0]);
	}
	
	private static List<String> list(File file, String fileRegex, String directoryRegex, boolean recursive, String path) {
		List<String> results = new ArrayList<String>();
		File[] children = file.listFiles();
		if (children != null) {
			for (File child : children) {
				String childPath = path == null ? child.getName() : path + "/" + child.getName();
				if (fileRegex != null && child.isFile() && child.getName().matches(fileRegex)) {
					results.add(childPath);
				}
				else if (directoryRegex != null && child.isDirectory() && child.getName().matches(directoryRegex)) {
					results.add(childPath);
				}
				if (recursive && child.isDirectory()) {
					results.addAll(list(child, fileRegex, directoryRegex, recursive, childPath));
				}
			}
		}
		return results;
	}
	
	public static void merge(String fromDirectory, String toDirectory, boolean recursive, boolean overwriteIfExists) throws IOException {
		File from = new File(fromDirectory);
		File to = new File(toDirectory);
		if (!from.exists()) {
			throw new FileNotFoundException("Could not find directory " + fromDirectory);
		}
		if (!to.exists()) {
			to.mkdirs();
		}
		merge(from, to, recursive, overwriteIfExists);
	}
	
	private static void merge(File fromDirectory, File toDirectory, boolean recursive, boolean overwriteIfExists) throws IOException {
		for (File child : fromDirectory.listFiles()) {
			File target = new File(toDirectory, child.getName());
			if (child.isFile()) {
				if (target.exists() && !overwriteIfExists) {
					throw new IOException("Could not overwrite existing file " + target);
				}
				Container<ByteBuffer> output = IOUtils.wrap(target);
				try {
					Container<ByteBuffer> input = IOUtils.wrap(child);
					try {
						IOUtils.copyBytes(input, output);
					}
					finally {
						input.close();
					}
				}
				finally {
					output.close();
				}
			}
			else if (child.isDirectory() && recursive) {
				if (!target.exists()) {
					target.mkdir();
				}
				merge(child, target, recursive, overwriteIfExists);
			}
		}
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
