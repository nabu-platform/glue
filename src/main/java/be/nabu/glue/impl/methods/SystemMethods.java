package be.nabu.glue.impl.methods;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.utils.io.IOUtils;

@MethodProviderClass(namespace = "system")
public class SystemMethods {
	
	public static String exec(String directory, String...commands) throws IOException, InterruptedException {
		// apparently if you do something like "mvn dependency:tree" in one string, it will fail but if you do "mvn" and "dependency:tree" it fails
		// this is however annoying to enforce on the user, so do a preliminary split
		// note that this apparently does auto-quoting, no need to quote spaces etc, this could also explain the above stuff
		List<String> splittedCommands = new ArrayList<String>();
		for (String command : commands) {
			splittedCommands.addAll(Arrays.asList(command.split("(?<!\\\\)[\\s]+")));
		}
		Process process = Runtime.getRuntime().exec(splittedCommands.toArray(new String[0]), null, new File(directory));
		process.waitFor();
		InputStream input = new BufferedInputStream(process.getInputStream());
		try {
			byte [] bytes = IOUtils.toBytes(IOUtils.wrap(input));
			return new String(bytes);
		}
		finally {
			input.close();
		}
	}
	
}
