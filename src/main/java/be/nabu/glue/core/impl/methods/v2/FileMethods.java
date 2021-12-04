package be.nabu.glue.core.impl.methods.v2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.providers.SystemMethodProvider;
import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.api.ListableContextAccessor;
import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.ResourceReadableContainer;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.utils.io.IOUtils;

@MethodProviderClass(namespace = "file")
public class FileMethods {
	
//	@GlueMethod(description = "Lists the files matching the given regex in the given directory", version = 2)
//	public static List<String> list(
//			@GlueParam(name = "target", description = "The directory to search in or the object to list from") Object target, 
//			@GlueParam(name = "file", description = "The file regex to match. If they are matched, they are added to the result list. Pass in null if you are not interested in files") String fileRegex, 
//			@GlueParam(name = "directory", description = "The directory regex to match. If they are matched, they are added to the result list. Pass in null if you are not interested in directories") String directoryRegex, 
//			@GlueParam(name = "recursive", description = "Whether or not to look recursively", defaultValue = "false") Boolean recursive) throws IOException {
//		if (target == null) {
//			target = SystemMethodProvider.getDirectory();
//		}
//		if (fileRegex == null && directoryRegex == null) {
//			fileRegex = ".*";
//		}
//		if (recursive == null) {
//			recursive = false;
//		}
//		return null;
//	}
	
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@GlueMethod(description = "This method will zip all the given files", returns = "The bytes representing the zip file", version = 2, restricted = true)
	public static byte [] zip(Object...original) throws IOException, EvaluationException {
		if (original == null || original.length == 0) {
			return null;
		}
		final Iterable<?> entries = GlueUtils.resolve(GlueUtils.toSeries(original));
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ZipOutputStream zip = new ZipOutputStream(output);
		for (Object entry : entries) {
			// if you pass in a string, we assume it is a filepath
			// since the filepath should probably not be fully copied to the zip, we take only the filename
			if (entry instanceof String) {
				Resource resolve = resolve((String) entry);
				// you have passed in a folder, recursively zip
				if (resolve instanceof ResourceContainer) {
					List<String> listInternal = listInternal((ResourceContainer<?>) resolve, ".*", null, true, null, false, "");
					for (String file : listInternal) {
						Resource child = ResourceUtils.resolve(resolve, file);
						if (child instanceof ReadableResource) {
							ZipEntry zipEntry = new ZipEntry(file);
							zip.putNextEntry(zipEntry);
							ResourceReadableContainer container = new ResourceReadableContainer((ReadableResource) child);
							try {
								zip.write(IOUtils.toBytes(container));
							}
							finally {
								container.close();
							}
						}
					}
				}
				else {
					InputStream input = be.nabu.glue.core.impl.methods.FileMethods.read((String) entry);
					try {
						ZipEntry zipEntry = new ZipEntry(((String) entry).replaceAll("^.*/([^/]+)$", "$1"));
						zip.putNextEntry(zipEntry);
						zip.write(IOUtils.toBytes(IOUtils.wrap(input)));
					}
					finally {
						input.close();
					}
				}
			}
			else {
				ContextAccessor accessor = ContextAccessorFactory.getInstance().getAccessor(entry.getClass());
				if (!(accessor instanceof ListableContextAccessor)) {
					throw new IllegalArgumentException("Could not find listable accessor for: " + entry);
				}
				for (String key : (Collection<String>) ((ListableContextAccessor) accessor).list(entry)) {
					Object object = accessor.get(entry, key);
					// if we pass in a map, it is presumably from another zip file that we want to rezip
					if (object instanceof Map) {
						for (Map.Entry<String, Object> single : ((Map<String, Object>) object).entrySet()) {
							byte [] content = single.getValue() instanceof byte[] ? (byte[]) single.getValue() : GlueUtils.convert(single.getValue(), byte[].class);
							if (content != null) {
								ZipEntry zipEntry = new ZipEntry(key + "/" + single.getKey().replaceFirst("^[/]+", ""));
								zip.putNextEntry(zipEntry);
								zip.write(content);
							}
						}
					}
					else if (object instanceof InputStream) {
						ZipEntry zipEntry = new ZipEntry(key);
						zip.putNextEntry(zipEntry);
						IOUtils.copyBytes(IOUtils.wrap((InputStream) object), IOUtils.wrap(zip));
						((InputStream) object).close();
					}
					else if (object != null) {
						byte [] content = object instanceof byte[] ? (byte[]) object : GlueUtils.convert(object, byte[].class);
						ZipEntry zipEntry = new ZipEntry(key);
						zip.putNextEntry(zipEntry);
						zip.write(content);
					}
				}
			}
		}
		zip.close();
		return output.toByteArray();
	}
	
	@GlueMethod(description = "Lists the files matching the given regex in the given directory", version = 2, restricted = true)
	public static List<String> list(
			@GlueParam(name = "target", description = "The directory to search in or the object to list from") Object target, 
			@GlueParam(name = "fileRegex", description = "The file regex to match. If they are matched, they are added to the result list. Pass in null if you are not interested in files") String fileRegex, 
			@GlueParam(name = "directoryRegex", description = "The directory regex to match. If they are matched, they are added to the result list. Pass in null if you are not interested in directories") String directoryRegex, 
			@GlueParam(name = "recursive", description = "Whether or not to look recursively", defaultValue = "false") Boolean recursive,
			@GlueParam(name = "absolute", description = "Whether or not to look recursively", defaultValue = "false") Boolean absolute) throws IOException {
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
				return new ArrayList<String>();
			}
			String fullPath = ResourceUtils.getURI(resource).getPath();
			return listInternal((ResourceContainer<?>) resource, fileRegex, directoryRegex, recursive, null, absolute != null && absolute, fullPath);
		}
		// we assume it's a zip for now
		else {
			List<String> files = new ArrayList<String>();
			ZipInputStream zip = new ZipInputStream(ScriptMethods.toStream(be.nabu.glue.core.impl.methods.ScriptMethods.bytes(target)));
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
			return files;
		}
	}
	
	static List<String> listInternal(ResourceContainer<?> file, String fileRegex, String directoryRegex, boolean recursive, String path, boolean absolute, String rootPath) {
		List<String> results = new ArrayList<String>();
		for (Resource child : file) {
			String childPath = path == null ? child.getName() : path + "/" + child.getName();
			String fullPath = (rootPath + "/" + childPath).replaceAll("[/]{2,}", "/");
			if (fileRegex != null && child instanceof ReadableResource && child.getName().matches(fileRegex)) {
				results.add(absolute ? fullPath : childPath);
			}
			if (directoryRegex != null && child instanceof ResourceContainer && child.getName().matches(directoryRegex)) {
				results.add(absolute ? fullPath : childPath);
			}
			if (recursive && child instanceof ResourceContainer) {
				results.addAll(listInternal((ResourceContainer<?>) child, fileRegex, directoryRegex, recursive, childPath, absolute, rootPath));
			}
		}
		return results;
	}
	
	private static Resource resolve(String fileName) throws IOException {
		try {
			URI uri = be.nabu.glue.core.impl.methods.FileMethods.uri(fileName);
			if (ResourceFactory.getInstance().getResolver(uri.getScheme()) == null) {
				return null;
			}
			return ResourceFactory.getInstance().resolve(uri, null);
		}
		catch (RuntimeException e) {
			return null;
		}
	}
}
