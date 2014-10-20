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
	
	/**
	 * Reads a file from the file system
	 */
	public static InputStream read(String fileName) throws FileNotFoundException {
		return new FileInputStream(resolve(fileName));
	}

	/**
	 * Lists all files that match the given regex in the given directory
	 * @param path
	 * @param fileRegex
	 * @param directoryRegex
	 * @param recursive
	 * @return
	 */
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
	
	/**
	 * Allows you to merge all files from one directory to another
	 * @param fromDirectory
	 * @param toDirectory
	 * @param recursive
	 * @param overwriteIfExists
	 * @throws IOException
	 */
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
	
	/**
	 * Write the content to a given file
	 * @param fileName
	 * @param content
	 * @throws IOException
	 */
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
	
	/**
	 * Delete a file, this will delete recursively if its a directory
	 */
	public static void delete(String fileName) {
		File file = resolve(fileName);
		if (file.exists()) {
			delete(file);
		}
	}
	
	private static void delete(File file) {
		if (file.isDirectory()) {
			for (File child : file.listFiles()) {
				delete(child);
			}
		}
		file.delete();
	}
	
	/**
	 * Allow for some kind of traversal system (start in user.home)
	 */
	private static File resolve(String fileName) {
		return new File(fileName);
	}

	/**
	 * This allows you to create zip files
	 * You can pass in the names of the files that you want to add to the zip, alternatively you can map the files to new ones or add strings by using the syntax:
	 * 		<filename>=<filename>
	 * 		<filename>=<content>
	 * They are differentiated by a best effort
	 */
	public static byte [] zip(String...fileNames) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ZipOutputStream zip = new ZipOutputStream(output);
		for (String fileName : fileNames) {
			Object content = null;
			int index = fileName.indexOf('=');
			if (index >= 0) {
				String fileContent = fileName.substring(index + 1);
				fileName = fileName.substring(0, index);
				content = ScriptMethods.file(fileContent);
				if (content == null) {
					content = fileContent;
				}
			}
			else {
				content = ScriptMethods.file(fileName);
				// remove path (if any)
				fileName = fileName.replaceAll(".*/", "");
			}
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
