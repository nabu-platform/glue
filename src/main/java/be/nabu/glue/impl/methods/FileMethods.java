package be.nabu.glue.impl.methods;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.impl.providers.SystemMethodProvider;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;

@MethodProviderClass(namespace = "file")
public class FileMethods {
	
	public static InputStream read(String fileName) throws IOException {
		return read(fileName, true);
	}
	
	/**
	 * Reads a file from the file system
	 * @throws IOException 
	 */
	public static InputStream read(String fileName, boolean tryURL) throws IOException {
		if (fileName == null) {
			return null;
		}
		Resource resource = resolve(fileName);
		if (resource == null) {
			if (!tryURL) {
				return null;
			}
			// first try standard URL technology for a simple file read
			URI uri = uri(fileName);
			URL url = uri.toURL();
			// if we get here, check if we need a proxy
			Proxy proxy = null;
			if (ScriptMethods.environment("proxy.host") != null) {
				if (ScriptMethods.environment("proxy.bypass") == null || !url.getHost().matches(ScriptMethods.environment("proxy.bypass"))) {
					String type = ScriptMethods.environment("proxy.type");
					String host = ScriptMethods.environment("proxy.host");
					String port = ScriptMethods.environment("proxy.port");
					proxy = new Proxy(type != null && type.equalsIgnoreCase("SOCKS") ? Proxy.Type.SOCKS : Proxy.Type.HTTP, new InetSocketAddress(host, port == null ? 8080 : new Integer(port)));
				}
			}
			if (proxy == null) {
				return url.openStream();
			}
			else {
				return url.openConnection(proxy).getInputStream();
			}
		}
		else if (!(resource instanceof ReadableResource)) {
			throw new IOException("Can not read from: " + fileName);
		}
		return IOUtils.toInputStream(((ReadableResource) resource).getReadable());
	}
	
	/**
	 * Lists all files that match the given regex in the given directory
	 * @param path
	 * @param fileRegex
	 * @param directoryRegex
	 * @param recursive
	 * @return
	 * @throws IOException 
	 */
	public static String [] list(String path, String fileRegex, String directoryRegex, boolean recursive) throws IOException {
		Resource resource = resolve(path);
		if (resource == null) {
			return new String[0];
		}
		return list((ResourceContainer<?>) resource, fileRegex, directoryRegex, recursive, null).toArray(new String[0]);
	}
	
	public static String [] list(Object object) throws IOException {
		return list(object, ".*");
	}
	
	public static String [] list(Object object, String fileRegex) throws IOException {
		if (object instanceof String) {
			return list((String) object, fileRegex, null, true);
		}
		else {
			List<String> files = new ArrayList<String>();
			ZipInputStream zip = new ZipInputStream(ScriptMethods.toStream(ScriptMethods.bytes(object)));
			try {
				ZipEntry entry = null;
				while ((entry = zip.getNextEntry()) != null) {
					if (entry.getName().replaceAll(".*/([^/]+)$", "$1").matches(fileRegex)) {
						files.add(entry.getName().replaceAll("^[/]+", ""));
					}
				}
			}
			finally {
				zip.close();
			}
			return files.toArray(new String[files.size()]);
		}
	}
	
	private static List<String> list(ResourceContainer<?> file, String fileRegex, String directoryRegex, boolean recursive, String path) {
		List<String> results = new ArrayList<String>();
		for (Resource child : file) {
			String childPath = path == null ? child.getName() : path + "/" + child.getName();
			if (fileRegex != null && child instanceof ReadableResource && child.getName().matches(fileRegex)) {
				results.add(childPath);
			}
			if (directoryRegex != null && child instanceof ResourceContainer && child.getName().matches(directoryRegex)) {
				results.add(childPath);
			}
			if (recursive && child instanceof ResourceContainer) {
				results.addAll(list((ResourceContainer<?>) child, fileRegex, directoryRegex, recursive, childPath));
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
		Resource from = resolve(fromDirectory);
		Resource to = resolve(toDirectory);
		if (from == null) {
			throw new FileNotFoundException("Could not find directory: " + fromDirectory);
		}
		if (to == null) {
			to = ResourceUtils.mkdir(uri(toDirectory), null);
		}
		merge((ResourceContainer<?>) from, (ManageableContainer<?>) to, recursive, overwriteIfExists);
	}
	
	private static void merge(ResourceContainer<?> fromDirectory, ManageableContainer<?> toDirectory, boolean recursive, boolean overwriteIfExists) throws IOException {
		for (Resource child : fromDirectory) {
			Resource target = toDirectory.getChild(child.getName());
			if (child instanceof ReadableResource) {
				if (target != null && !overwriteIfExists) {
					continue;
				}
				if (target == null) {
					URI childURI = URIUtils.getChild(ResourceUtils.getURI(toDirectory), child.getName());
					target = ResourceUtils.touch(childURI, null);
					if (target == null) {
						throw new IOException("Could not find or create target file: " + childURI);
					}
				}
				if (!(target instanceof WritableResource)) {
					throw new IOException("Can not write to target: " + ResourceUtils.getURI(target));
				}
				ScriptMethods.echo("Merging file " + ResourceUtils.getPath(child));
				WritableContainer<ByteBuffer> output = ((WritableResource) target).getWritable();
				try {
					ReadableContainer<ByteBuffer> input = ((ReadableResource) child).getReadable();
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
			
			if (child instanceof ResourceContainer && recursive) {
				if (target == null) {
					URI childURI = URIUtils.getChild(ResourceUtils.getURI(toDirectory), child.getName());
					target = ResourceUtils.mkdir(childURI, null);
					if (target == null) {
						throw new IOException("Could not find or create target directory: " + childURI);
					}
				}
				merge((ResourceContainer<?>) child, (ManageableContainer<?>) target, recursive, overwriteIfExists);
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
				Resource target = resolve(fileName);
				if (target == null) {
					target = ResourceUtils.touch(uri(fileName), null);
				}
				if (!(target instanceof WritableResource)) {
					throw new IOException("Can not write to: " + fileName);
				}
				WritableContainer<ByteBuffer> output = ((WritableResource) target).getWritable();
				try {
					IOUtils.copyBytes(IOUtils.wrap(input), output);
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
	
	public static boolean exists(String target) throws IOException {
		return resolve(target) != null;
	}
	
	/**
	 * Delete a file, this will delete recursively if its a directory
	 * @throws IOException 
	 */
	public static void delete(String fileName) throws IOException {
		Resource resource = resolve(fileName);
		if (resource != null) {
			if (!(resource.getParent() instanceof ManageableContainer)) {
				throw new IOException("Can not delete: " + fileName);
			}
			((ManageableContainer<?>) resource.getParent()).delete(resource.getName());
		}
	}

	private static Resource resolve(String fileName) throws IOException {
		URI uri = uri(fileName);
		if (ResourceFactory.getInstance().getResolver(uri.getScheme()) == null) {
			return null;
		}
		return ResourceFactory.getInstance().resolve(uri, null);
	}

	public static URI uri(String fileName) throws IOException {
		fileName = fileName.replace('\\', '/');
		// we need a scheme, this should ignore c: etc...
		if (!fileName.matches("^[\\w+]{2,}:.*")) {
			// if it does start with c: or some other drive letter, prefix it with "/"
			if (fileName.matches("^[\\w]{1}:.*")) {
				fileName = "/" + fileName;
			}
			// if it's not absolute, make it so
			else if (!fileName.startsWith("/")) {
				File file = new File(SystemMethodProvider.getDirectory());
				String path = file.getCanonicalPath().replace('\\', '/');
				if (path.matches("^[\\w]{1}:.*")) {
					path = "/" + path;
				}
				if (!path.startsWith("/")) {
					throw new RuntimeException("Could not resolve the absolute path of " + fileName);
				}
				if (!path.endsWith("/")) {
					path += "/";
				}
				fileName = path + fileName;
			}
			fileName = "file:" + fileName;
		}
		try {
			return new URI(URIUtils.encodeURI(fileName));
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * This allows you to create zip files
	 * You can pass in the names of the files that you want to add to the zip, alternatively you can map the files to new ones or add strings by using the syntax:
	 * 		<filename>=<filename>
	 * 		<filename>=<content>
	 * They are differentiated by a best effort
	 */
	@GlueMethod(description = "This method will zip all the given files", returns = "The bytes representing the zip file")
	public static byte [] zip(@GlueParam(name = "fileNames", description = "You can pass in actual filenames e.g. 'test.txt' or mapped filenames e.g. 'other.txt=test.txt' or mapped string content e.g. 'something.txt=this is the text that goes in here!'") String...fileNames) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ZipOutputStream zip = new ZipOutputStream(output);
		for (String fileName : fileNames) {
			if (fileName == null) {
				continue;
			}
			Object content = null;
			int index = fileName.indexOf('=');
			if (index >= 0) {
				String fileContent = fileName.substring(index + 1);
				fileName = fileName.substring(0, index);
				if (fileContent.matches("[\\w./-]+")) {
					content = ScriptMethods.file(fileContent);
				}
				else {
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
				content = ScriptRuntime.getRuntime().getScript().getParser().substitute((String) content, ScriptRuntime.getRuntime().getExecutionContext(), false).getBytes();
			}
			ZipEntry entry = new ZipEntry(fileName);
			zip.putNextEntry(entry);
			zip.write((byte[]) content);
		}
		zip.close();
		return output.toByteArray();
	}

	@GlueMethod(description = "Retrieves a specific file from a zip", returns = "The content of the file in bytes")
	public static byte [] unzip(@GlueParam(name = "zipContent", description = "The content of the zip file") Object content, @GlueParam(name = "fileName", description = "The filename to find") String fileName) throws IOException {
		fileName = fileName.replaceAll("^[/]+", "");
		ZipInputStream zip = new ZipInputStream(ScriptMethods.toStream(ScriptMethods.bytes(content)));
		try {
			ZipEntry entry = null;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.getName().replaceAll("^[/]+", "").equals(fileName)) {
					return ScriptMethods.bytes(zip);
				}
			}
			return null;
		}
		finally {
			zip.close();
		}
	}
}
