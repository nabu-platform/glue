package be.nabu.glue.core.impl.methods.v2;

import java.io.IOException;
import java.util.List;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.core.impl.providers.SystemMethodProvider;

public class FileMethods {
	
	@GlueMethod(description = "Lists the files matching the given regex in the given directory", version = 2)
	public static List<String> list(
			@GlueParam(name = "target", description = "The directory to search in or the object to list from") Object target, 
			@GlueParam(name = "file", description = "The file regex to match. If they are matched, they are added to the result list. Pass in null if you are not interested in files") String fileRegex, 
			@GlueParam(name = "directory", description = "The directory regex to match. If they are matched, they are added to the result list. Pass in null if you are not interested in directories") String directoryRegex, 
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
		return null;
	}
}
