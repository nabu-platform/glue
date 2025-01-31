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

package be.nabu.glue.core.impl.providers;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.StreamProvider;
import be.nabu.glue.core.api.SandboxableMethodProvider;
import be.nabu.glue.core.impl.methods.ScriptMethods;
import be.nabu.glue.core.impl.methods.ShellMethods;
import be.nabu.glue.core.impl.methods.StringMethods;
import be.nabu.glue.core.impl.methods.SystemMethods.SystemProperty;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseMethodOperation;
import be.nabu.libs.resources.URIUtils;

public class SystemMethodProvider implements SandboxableMethodProvider {

	public static final String CLI_DIRECTORY = "cli.directory";

	private static List<String> predefined = Arrays.asList("system.exec", "system.linux", "system.newProperty", "system.input"); 
	
	private boolean sandboxed;
	private boolean allowCatchAll = false;
	
	@Override
	public Operation<ExecutionContext> resolve(String name) {
		// no system interaction at all during sandbox mode, too many ways that can go sideways
		if (sandboxed) {
			return null;
		}
		if (!predefined.contains(name) && name.matches("^system\\.[\\w]+$")) {
			return new CLIOperation(name.substring("system.".length()));
		}
		else if (allowCatchAll && !name.contains(".")) {
			return new CLIOperation(name);
		}
		return null;
	}

	@Override
	public List<MethodDescription> getAvailableMethods() {
		return new ArrayList<MethodDescription>();
	}

	private static class CLIOperation extends BaseMethodOperation<ExecutionContext> {

		private Converter converter;
		private String command;

		public CLIOperation(String command) {
			this.command = command;
			converter = ConverterFactory.getInstance().getConverter();
		}
		
		@Override
		public void finish() throws ParseException {
			// nothing
		}
		
		@SuppressWarnings({ "unchecked" })
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			String directory = ShellMethods.pwd();
			// if there is a runtime, we can hold some state with regards to the directory
			if (ScriptRuntime.getRuntime() != null) {
				directory = getDirectory();
			}
			List<String> arguments = new ArrayList<String>();
			List<SystemProperty> systemProperties = new ArrayList<SystemProperty>();
			List<byte []> inputBytes = new ArrayList<byte[]>();
			boolean redirectIO = false;
			boolean includeError = false;
			boolean explicitlyReturnOutput = false;
			for (int i = 1; i < getParts().size(); i++) {
				Operation<ExecutionContext> argumentOperation = (Operation<ExecutionContext>) getParts().get(i).getContent();
				// if you have a lesser then (e.g. 1<), you can redirect an input to it
				if (argumentOperation.getType() == OperationType.CLASSIC && argumentOperation.getParts().size() == 3 && argumentOperation.getParts().get(1).getType() == Type.LESSER) {
					Object evaluated = argumentOperation.getParts().get(2).getContent() instanceof Operation
						? ((Operation<ExecutionContext>) argumentOperation.getParts().get(2).getContent()).evaluate(context)
						: argumentOperation.getParts().get(2).getContent();
					if (evaluated != null) {
						if (evaluated instanceof String[]) {
							evaluated = StringMethods.join(System.getProperty("line.separator", "\n"), (String[]) evaluated);
						}
						try {
							inputBytes.add(ScriptMethods.bytes(evaluated));
						}
						catch (IOException e) {
							throw new EvaluationException(e);
						}
					}
				}
				// if you have a greater then (e.g. 1>) you can redirect the output
				else if (argumentOperation.getType() == OperationType.CLASSIC && argumentOperation.getParts().size() == 3 && argumentOperation.getParts().get(1).getType() == Type.GREATER) {
					Object output = argumentOperation.getParts().get(2).getContent().toString();
					if (argumentOperation.getParts().get(0).getContent().toString().equals("1")) {
						redirectIO = "system".equals(output);
						explicitlyReturnOutput = "output".equals(output);
					}
					else if (argumentOperation.getParts().get(0).getContent().toString().equals("2")) {
						includeError = "stream".equals(output);
					}
				}
				else {
					Object evaluated = argumentOperation.evaluate(context);
					if (evaluated instanceof SystemProperty) {
						systemProperties.add((SystemProperty) evaluated);
					}
					else if (evaluated instanceof SystemProperty[]) {
						systemProperties.addAll(Arrays.asList((SystemProperty[]) evaluated));
					}
					else {
						String value = evaluated instanceof String[] 
							? StringMethods.join(System.getProperty("line.separator", "\n"), (String[]) evaluated)
							: converter.convert(evaluated, String.class);
						if (evaluated != null && value == null) {
							throw new EvaluationException("Can not convert " + evaluated + " to string");
						}
						else if (value == null) {
							throw new EvaluationException("Null values are currently not allowed for CLI methods");
						}
						arguments.add(value);
					}
				}
			}
			if (command.equalsIgnoreCase("cd")) {
				if (arguments.size() != 1) {
					throw new EvaluationException("Expecting exactly one argument for the 'cd' method");
				}
				try {
					if (arguments.get(0).equals("~")) {
						directory = System.getProperty("user.home");
					}
					else {
						// distinguish between relative & absolute
						directory = arguments.get(0).startsWith("/") ? URIUtils.normalize(arguments.get(0)) : URIUtils.normalize(URIUtils.getChild(new URI(URIUtils.encodeURI(directory)), arguments.get(0)).getPath());
					}
					if (ScriptRuntime.getRuntime() != null) {
						ScriptRuntime.getRuntime().getContext().put(CLI_DIRECTORY, directory);	
					}
					return directory;
				}
				catch (URISyntaxException e) {
					throw new EvaluationException(e);
				}
			}
			else if (command.equalsIgnoreCase("pwd")) {
				return directory;
			}
			else {
				// dashes are often used in system commands but not supported by the glue parser
				// to allow arbitrary stuff, you can use the eval command where the first parameter is assumed to be the actual system command
				// for example system.eval("apt-get", "update")
				if (command.equals("eval")) {
					command = arguments.remove(0);
				}
				arguments.add(0, command);
				// we still split the commands, either because you only passed in a regular string or want to do something like "system.mvn('clean install')"
				// we only split the FIRST command because you do want to be able to pass in strings that do not have quotes as parameters, e.g.
				// system.git("commit", "-a", "-m", myMessageWithWhitespace)
				List<String> splittedCommands = new ArrayList<String>();
				if (!arguments.isEmpty()) {
					Pattern pattern = Pattern.compile("'[^\']+'|\"[^\"]+\"");
					for (String argument : arguments) {
						Matcher matcher = pattern.matcher(argument);
						int last = 0;
						while (matcher.find()) {
							splittedCommands.addAll(Arrays.asList(argument.substring(last, matcher.start()).split("[\\s]+")));
							splittedCommands.add(argument.substring(matcher.start() + 1, matcher.end() - 1));
							last = matcher.end() + 1;
						}
						if (last < argument.length()) {
							splittedCommands.addAll(Arrays.asList(argument.substring(last).split("[\\s]+")));
						}
						break;
					}
					if (arguments.size() >= 2) {
						splittedCommands.addAll(arguments.subList(1, arguments.size()));
					}
				}
				try {
					return exec(directory, splittedCommands.toArray(new String[splittedCommands.size()]), inputBytes, systemProperties, redirectIO, includeError, explicitlyReturnOutput).trim();
				}
				catch (IOException e) {
					throw new EvaluationException(e);
				}
				catch (InterruptedException e) {
					throw new EvaluationException(e);
				}
			}
		}
	}

	public static String getDirectory() {
		if (ScriptRuntime.getRuntime() == null) {
			return ShellMethods.pwd();
		}
		else {
			if (!ScriptRuntime.getRuntime().getContext().containsKey(CLI_DIRECTORY)) {
				ScriptRuntime.getRuntime().getContext().put(CLI_DIRECTORY, ShellMethods.pwd());
			}
			return (String) ScriptRuntime.getRuntime().getContext().get(CLI_DIRECTORY);
		}
	}
	
	public static String exec(String directory, String [] commands, List<byte[]> inputContents, List<SystemProperty> systemProperties, boolean redirectIO, boolean includeError, boolean explicitlyReturnOutput) throws IOException, InterruptedException {
		if (!directory.endsWith("/")) {
			directory += "/";
		}
		File dir = new File(directory);
		if (!dir.exists()) {
			throw new FileNotFoundException("Can not find directory: " + directory);
		}
		else if (!dir.isDirectory()) {
			throw new IOException("The file is not a directory: " + directory);
		}
		String [] env = null;
		ProcessBuilder processBuilder = new ProcessBuilder(commands);
		processBuilder.directory(dir);
		if (systemProperties != null && !systemProperties.isEmpty()) {
			List<SystemProperty> allProperties = new ArrayList<SystemProperty>();
			// get the current environment properties, if you pass in _any_ properties, it will not inherit the ones from the current environment
			Map<String, String> systemEnv = System.getenv();
			for (String key : systemEnv.keySet()) {
				allProperties.add(new SystemProperty(key, systemEnv.get(key)));
			}
			allProperties.addAll(systemProperties);
			env = new String[allProperties.size()];
			for (int i = 0; i < allProperties.size(); i++) {
				env[i] = allProperties.get(i).getKey() + "=" + allProperties.get(i).getValue();
				processBuilder.environment().put(allProperties.get(i).getKey(), allProperties.get(i).getValue());
			}
		}
		
		final StreamProvider streamProvider = explicitlyReturnOutput ? null : ScriptRuntime.getRuntime().getStreamProvider();
		Process process;
		if (redirectIO) {
//			processBuilder.inheritIO();
			processBuilder.redirectInput(inputContents != null && !inputContents.isEmpty() ? Redirect.PIPE : Redirect.INHERIT);
			processBuilder.redirectError(Redirect.INHERIT);
			processBuilder.redirectOutput(Redirect.INHERIT);
			process = processBuilder.start();
		}
		else if (streamProvider != null) {
			processBuilder.redirectInput(Redirect.PIPE);
			processBuilder.redirectOutput(Redirect.PIPE);
			processBuilder.redirectError(Redirect.PIPE);
			process = processBuilder.start();
		}
		else {
			process = Runtime.getRuntime().exec(commands, env, dir);
		}
		if (inputContents != null && !inputContents.isEmpty()) {
			OutputStream output = new BufferedOutputStream(process.getOutputStream());
			try {
				for (byte [] content : inputContents) {
					output.write(content);
				}
			}
			finally {
				output.close();
			}
		}
		CopyStream inputStream = null, errorStream = null, outputStream = null;
		ByteArrayOutputStream inputResult = null, errorResult = null;
		Thread inputThread = null, errorThread = null, outputThread = null;
		try {
			// if we are not redirecting I/O to emulate system behavior, capture the content in a separate thread
			if (!redirectIO) {
				errorResult = new ByteArrayOutputStream();
				errorStream = new CopyStream(process.getErrorStream(), streamProvider == null ? errorResult : streamProvider.getErrorStream());
				errorThread = new Thread(errorStream);
				errorThread.start();
				
				inputResult = new ByteArrayOutputStream();
				inputStream = new CopyStream(process.getInputStream(), streamProvider == null ? inputResult : streamProvider.getOutputStream());
				inputThread = new Thread(inputStream);
				inputThread.start();

				// there is currently no way (with blocking I/O) to cleanly stop this without killing the input stream
				// if coming from for instance a socket, this...sucks
				if (streamProvider != null) {
					outputStream = streamProvider.isBlocking() 
						? new NonBlockingCopyStream(streamProvider.getInputStream(), process.getOutputStream()) 
						: new CopyStream(streamProvider.getInputStream(), process.getOutputStream());
					outputStream.setProcess(process);
					outputThread = new Thread(outputStream);
					outputThread.start();
				}
			}
			process.waitFor();
		}
		catch (InterruptedException e) {
			// do nothing
		}

		// the process ended, let's quit it!
		if (outputStream != null) {
			outputStream.close();
		}
		
		if (redirectIO) {
			return Integer.toString(process.exitValue());
		}
		else {
			// we wait for the process to complete?
			inputThread.join();
			errorThread.join();
			
			// not necessary?
			inputStream.close();
			errorStream.close();

			String error = errorResult == null ? null : new String(errorResult.toByteArray());
			String result = inputResult == null ? null : new String(inputResult.toByteArray());
			if (error != null && !error.isEmpty()) {
				if (includeError) {
					if (result == null) {
						result = error;
					}
					else {
						result += "\n" + error;
					}
				}
				else {
					System.err.println(error);
				}
			}
			return result;
		}
	}
	
	public static class NonBlockingCopyStream extends CopyStream {
		private boolean closed;
		public NonBlockingCopyStream(InputStream input, OutputStream output) {
			super(input, output);
		}
		@Override
		public void run() {
			byte [] buffer = new byte[4096];
			int read = 0;
			try {
				String quitSignal = "^SIGINT";
				int length = quitSignal.length();
				while (!closed) {
					read = input.read(buffer);
					if (read < 0) {
						break;
					}
					if (process != null && read >= length) {
						try {
							if (new String(buffer, 0, read, "ASCII").trim().endsWith(quitSignal)) {
								process.destroyForcibly();
								break;
							}
						}
						catch (Exception e) {
							// do nothing
						}
					}
					output.write(buffer, 0, read);
				}
				// if the input was closed and we have a process, stop it
				if (process != null) {
					process.destroyForcibly();
				}
				closed = true;
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		@Override
		public void close() throws IOException {
			// unset process so it doesn't get forcibly killed
			process = null;
			closed = true;
			super.close();
		}
	}
	
	public static class CopyStream implements Runnable, Closeable {
		protected InputStream input;
		protected OutputStream output;
		protected boolean closed;
		protected Process process;
		public CopyStream(InputStream input, OutputStream output) {
			this.input = input;
			this.output = output;
		}
		@Override
		public void run() {
			byte [] buffer = new byte[4096];
			int read = 0;
			try {
				String quitSignal = "^SIGINT";
				int length = quitSignal.length();
				while((read = input.read(buffer)) > 0) {
					if (process != null && read >= length) {
						try {
							if (new String(buffer, 0, read, "ASCII").trim().endsWith(quitSignal)) {
								process.destroyForcibly();
								break;
							}
						}
						catch (Exception e) {
							// do nothing
						}
					}
					output.write(buffer, 0, read);
				}
				// if the input was closed and we have a process, stop it
				if (process != null) {
					process.destroyForcibly();
				}
				closed = true;
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		@Override
		public void close() throws IOException {
			closed = true;
			input.close();
		}
		public Process getProcess() {
			return process;
		}
		public void setProcess(Process process) {
			this.process = process;
		}
	}

	public boolean isAllowCatchAll() {
		return allowCatchAll;
	}

	public void setAllowCatchAll(boolean allowCatchAll) {
		this.allowCatchAll = allowCatchAll;
	}

	@Override
	public boolean isSandboxed() {
		return sandboxed;
	}
	@Override
	public void setSandboxed(boolean sandboxed) {
		this.sandboxed = sandboxed;
	}
	
}
