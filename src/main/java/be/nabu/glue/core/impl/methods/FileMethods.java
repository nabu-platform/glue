package be.nabu.glue.core.impl.methods;

import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.core.impl.providers.SystemMethodProvider;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.TimestampedResource;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;

@MethodProviderClass(namespace = "file")
public class FileMethods {

	@GlueMethod(restricted = true)
	public static Date modified(@GlueParam(name = "fileName", description = "The name of the file to read") String fileName) throws IOException {
		Resource resource = resolve(fileName);
		return resource instanceof TimestampedResource ? ((TimestampedResource) resource).getLastModified() : null;
	}
	
	@GlueMethod(description = "Returns the contents of the given file", restricted = true)
	public static InputStream read(@GlueParam(name = "fileName", description = "The name of the file to read") String fileName) throws IOException {
		return read(fileName, true);
	}
	
	@GlueMethod(description = "Returns the contents of the given file", restricted = true)
	public static InputStream read(@GlueParam(name = "fileName", description = "The name of the file to read") String fileName, @GlueParam(name = "tryURL", description = "Whether or not to try default URL logic") boolean tryURL) throws IOException {
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
			URLConnection connection = proxy == null ? url.openConnection() : url.openConnection(proxy);
			if (url.getUserInfo() != null) {
				connection.addRequestProperty("Authorization", "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(url.getUserInfo().getBytes()));
				connection.addRequestProperty("Connection", "close");
			}
			return connection.getInputStream();
		}
		else if (!(resource instanceof ReadableResource)) {
			throw new IOException("Can not read from: " + fileName);
		}
		return IOUtils.toInputStream(((ReadableResource) resource).getReadable());
	}
	
	@GlueMethod(description = "Lists the files matching the given regex in the given directory", version = 1, restricted = true)
	public static String [] list(
			@GlueParam(name = "target", description = "The directory to search in or the object to list from") Object target, 
			@GlueParam(name = "fileRegex", description = "The file regex to match. If they are matched, they are added to the result list. Pass in null if you are not interested in files") String fileRegex, 
			@GlueParam(name = "directoryRegex", description = "The directory regex to match. If they are matched, they are added to the result list. Pass in null if you are not interested in directories") String directoryRegex, 
			@GlueParam(name = "recursive", description = "Whether or not to look recursively", defaultValue = "false") Boolean recursive) throws IOException {
		if (target == null) {
			target = SystemMethodProvider.getDirectory();
		}
		if (fileRegex == null && directoryRegex == null) {
			fileRegex = ".*";
		}
		if (recursive == null) {
			recursive = false;
		}
		if (target instanceof String) {
			Resource resource = resolve((String) target);
			if (resource == null) {
				return new String[0];
			}
			return list((ResourceContainer<?>) resource, fileRegex, directoryRegex, recursive, null).toArray(new String[0]);
		}
		// we assume it's a zip for now
		else {
			List<String> files = new ArrayList<String>();
			ZipInputStream zip = new ZipInputStream(ScriptMethods.toStream(ScriptMethods.bytes(target)));
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
	
	static List<String> list(ResourceContainer<?> file, String fileRegex, String directoryRegex, boolean recursive, String path) {
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
	@GlueMethod(version = 1, restricted = true)
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
	
	public static boolean writable(@GlueParam(name = "target", description = "The file to write to") Object target) throws IOException {
		if (target instanceof WritableContainer) {
			return true;
		}
		else if (target instanceof String) {
			Resource resource = resolve((String) target);
			// if the resource exists, check that we can write to it
			if (resource != null) {
				return resource instanceof WritableResource;	
			}
			URI uri = uri((String) target);
			while (resource == null && !"/".equals(uri.getPath())) {
				// otherwise, check that the parent is manageable
				uri = URIUtils.getParent(uri);
				resource = ResourceFactory.getInstance().resolve(uri, null);
				// if the resource exists and it can be managed, it is OK
				if (resource instanceof ManageableContainer) {
					return true;
				}
			}
			return false;
		}
		else if (target instanceof OutputStream) {
			return true;
		}
		else {
			throw new IllegalArgumentException("Can not write to target: " + target);
		}
	}
	
	// copy from the writable, maybe too quick & dirty?
	public static boolean readable(@GlueParam(name = "target", description = "The file to read from") Object target) throws IOException {
		if (target instanceof ReadableContainer) {
			return true;
		}
		else if (target instanceof String) {
			Resource resource = resolve((String) target);
			return resource instanceof ReadableResource;
		}
		else if (target instanceof InputStream) {
			return true;
		}
		else {
			throw new IllegalArgumentException("Can not write to target: " + target);
		}
	}
	
	/**
	 * Write the content to a given file
	 * @param fileName
	 * @param content
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	@GlueMethod(description = "Writes the content to the given file", restricted = true)
	public static void write(@GlueParam(name = "target", description = "The file to write to") Object target, @GlueParam(name = "content", description = "The content to write to the file") Object content) throws IOException {
		if (content != null) {
			InputStream input = ScriptMethods.toStream(content);
			try {
				WritableContainer<ByteBuffer> output;
				boolean autoClose = true;
				if (target instanceof WritableContainer) {
					output = (WritableContainer<ByteBuffer>) target;
					autoClose = false;
				}
				else if (target instanceof String) {
					Resource resource = resolve((String) target);
					if (resource == null) {
						resource = ResourceUtils.touch(uri((String) target), null);
					}
					if (!(resource instanceof WritableResource)) {
						throw new IOException("Can not write to: " + resource + " (" + target + ")");
					}
					output = ((WritableResource) resource).getWritable();
				}
				else if (target instanceof OutputStream) {
					output = IOUtils.wrap((OutputStream) target);
					autoClose = false;
				}
				else {
					throw new IllegalArgumentException("Can not write to target: " + target);
				}
				try {
					IOUtils.copyBytes(IOUtils.wrap(input), output);
				}
				finally {
					if (autoClose) {
						output.close();
					}
				}
			}
			finally {
				input.close();
			}
		}
	}
	
	@GlueMethod(restricted = true)
	public static WritableContainer<ByteBuffer> output(@GlueParam(name = "fileName") String fileName) throws IOException {
		Resource target = resolve(fileName);
		if (target == null) {
			target = ResourceUtils.touch(uri(fileName), null);
		}
		if (!(target instanceof WritableResource)) {
			throw new IOException("Can not write to: " + fileName);
		}
		return ((WritableResource) target).getWritable();
	}
	
	@GlueMethod(restricted = true)
	public static boolean exists(String target) throws IOException {
		return resolve(target) != null;
	}
	
	/**
	 * Delete a file, this will delete recursively if its a directory
	 * @throws IOException 
	 */
	@GlueMethod(restricted = true)
	public static void delete(String...fileNames) throws IOException {
		if (fileNames != null && fileNames.length > 0) {
			for (String fileName : fileNames) {
				Resource resource = resolve(fileName);
				if (resource != null) {
					if (!(resource.getParent() instanceof ManageableContainer)) {
						throw new IOException("Can not delete: " + fileName);
					}
					((ManageableContainer<?>) resource.getParent()).delete(resource.getName());
				}
			}
		}
	}

	private static Resource resolve(String fileName) throws IOException {
		try {
			URI uri = uri(fileName);
			if (ResourceFactory.getInstance().getResolver(uri.getScheme()) == null) {
				return null;
			}
			return ResourceFactory.getInstance().resolve(uri, null);
		}
		catch (RuntimeException e) {
			return null;
		}
	}

	@GlueMethod(restricted = true)
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
	@GlueMethod(description = "This method will zip all the given files", returns = "The bytes representing the zip file", version = 1, restricted = true)
	public static byte [] zip(@GlueParam(name = "fileNames", description = "You can pass in actual filenames e.g. 'test.txt' or mapped filenames e.g. 'other.txt=test.txt' or mapped string content e.g. 'something.txt=this is the text that goes in here!'") String...fileNames) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ZipOutputStream zip = new ZipOutputStream(output);
		for (String fileName : fileNames) {
			if (fileName == null) {
				continue;
			}
			int index = fileName.indexOf('=');
			if (index >= 0) {
				String fileContent = fileName.substring(index + 1);
				fileName = fileName.substring(0, index);
				Object content = ScriptRuntime.getRuntime().getExecutionContext().getPipeline().get(fileContent);
				if (content == null) {
					Resource resolve = resolve(fileContent);
					if (resolve == null) {
						content = ScriptMethods.bytes(fileContent);
						ZipEntry entry = new ZipEntry(fileName);
						zip.putNextEntry(entry);
						zip.write(ScriptMethods.bytes(content));
					}
					else {
						ResourceUtils.zip(resolve, zip, false);
					}
				}
				else {
					ZipEntry entry = new ZipEntry(fileName);
					zip.putNextEntry(entry);
					zip.write(ScriptMethods.bytes(content));
				}
			}
			else {
				Object content = ScriptRuntime.getRuntime().getExecutionContext().getPipeline().get(fileName);
				if (content != null) {
					ZipEntry entry = new ZipEntry(fileName);
					zip.putNextEntry(entry);
					zip.write(ScriptMethods.bytes(content));
				}
				else {
					Resource resolve = resolve(fileName);
					if (resolve == null) {
						content = ScriptMethods.bytes(fileName);
						ZipEntry entry = new ZipEntry(fileName);
						zip.putNextEntry(entry);
						zip.write(ScriptMethods.bytes(content));
					}
					else {
						ResourceUtils.zip(resolve, zip, false);
					}
				}
			}
		}
		zip.close();
		return output.toByteArray();
	}

	@GlueMethod(description = "Retrieves a specific file from a zip", returns = "The content of the file in bytes", restricted = true)
	public static Object unzip(@GlueParam(name = "zipContent", description = "The content of the zip file") Object content, @GlueParam(name = "fileName", description = "The filename to find") String fileName) throws IOException {
		if (fileName == null) {
			Map<String, byte[]> entries = new HashMap<String, byte[]>();
			ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(ScriptMethods.bytes(content)));
			try {
				ZipEntry entry = null;
				while ((entry = zip.getNextEntry()) != null) {
					entries.put(entry.getName(), IOUtils.toBytes(IOUtils.wrap(zip)));
				}
				return entries;
			}
			finally {
				zip.close();
			}
		}
		else {
			fileName = fileName.replaceAll("^[/]+", "");
			ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(ScriptMethods.bytes(content)));
			try {
				ZipEntry entry = null;
				while ((entry = zip.getNextEntry()) != null) {
					String entryName = entry.getName().replaceAll("^[/]+", "");
					if (entryName.equals(fileName) || entryName.matches(fileName)) {
						return IOUtils.toBytes(IOUtils.wrap(zip));
					}
				}
				return null;
			}
			catch (Exception e) {
				if (content instanceof String) {
					throw new RuntimeException("Could not read zip file: " + content, e);
				}
				else {
					throw new RuntimeException("Could not unzip content", e);
				}
			}
			finally {
				zip.close();
			}
		}
	}
	
	@GlueMethod(restricted = true)
	public static byte [] gunzip(@GlueParam(name = "zipContent") Object content) throws IOException {
		return ScriptMethods.bytes(new GZIPInputStream(ScriptMethods.toStream(ScriptMethods.bytes(content))));
	}
	
	@GlueMethod(restricted = true)
	public static void open(String name) throws IOException {
		Desktop.getDesktop().browse(uri(name));
	}
}
