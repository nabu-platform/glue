/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.glue.core.impl.methods;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.api.InputProvider;
import be.nabu.glue.core.impl.providers.SystemMethodProvider;
import be.nabu.glue.impl.StandardInputProvider;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.utils.io.IOUtils;

@MethodProviderClass(namespace = "system")
public class SystemMethods {
	
	public static SystemProperty newProperty(String key, String value) {
		return new SystemProperty(key, value);
	}
	
	@GlueMethod(restricted = true)
	public static String bash(String command) throws IOException, InterruptedException {
		Process process = Runtime.getRuntime().exec(new String[] { "/bin/sh", "-c", command }, null, new File(SystemMethodProvider.getDirectory()));
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
	
	@GlueMethod(restricted = true)
	public static String exec(@GlueParam(name = "directory") String directory, @GlueParam(name = "commands") String...commands) throws IOException, InterruptedException {
		// apparently if you do something like "mvn dependency:tree" in one string, it will fail but if you do "mvn" and "dependency:tree" it fails
		// this is however annoying to enforce on the user, so do a preliminary split
		// note that this apparently does auto-quoting, no need to quote spaces etc, this could also explain the above stuff
		List<String> splittedCommands = new ArrayList<String>();
		if (commands == null || commands.length == 0) {
			commands = new String[] { directory };
			directory = null;
		}
		for (String command : commands) {
			splittedCommands.addAll(Arrays.asList(command.split("(?<!\\\\)[\\s]+")));
		}
		if (directory == null) {
			directory = SystemMethodProvider.getDirectory();
		}
		Process process = Runtime.getRuntime().exec(splittedCommands.toArray(new String[0]), null, directory == null ? null : new File(directory));
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

	@GlueMethod(restricted = true)
	public static String input(String message, Boolean secret, String defaultAnswer) throws IOException {
		ScriptRuntime runtime = ScriptRuntime.getRuntime();
		InputProvider inputProvider = runtime == null ? new StandardInputProvider() : runtime.getInputProvider();
		return inputProvider.input(message, secret != null && secret, defaultAnswer);
	}

	public static boolean linux() {
		return System.getProperty("os.name", "generic").contains("nux");
	}
	
	public static class SystemProperty {
		private String key, value;

		
		public SystemProperty() {
			// auto construct
		}
		
		public SystemProperty(String key, String value) {
			this.key = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
		@Override
		public String toString() {
			return key + "=" + value;
		}
	}
}
