package be.nabu.glue.core.impl.methods.v2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.api.ListableContextAccessor;
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
	@GlueMethod(description = "This method will zip all the given files", returns = "The bytes representing the zip file", version = 2)
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
							ZipEntry zipEntry = new ZipEntry(key + "/" + single.getKey().replaceFirst("^[/]+", ""));
							zip.putNextEntry(zipEntry);
							zip.write(content);
						}
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
}
