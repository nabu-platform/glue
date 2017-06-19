package be.nabu.glue.core.impl.providers;

import java.io.BufferedInputStream;
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

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.core.api.MethodProvider;
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

public class SystemMethodProvider implements MethodProvider {

	public static final String CLI_DIRECTORY = "cli.directory";

	private static List<String> predefined = Arrays.asList("system.exec", "system.linux", "system.input", "system.newProperty");
	
	@Override
	public Operation<ExecutionContext> resolve(String name) {
		if (!predefined.contains(name) && name.matches("^system\\.[\\w]+$")) {
			return new CLIOperation(name.substring("system.".length()));
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
					// distinguish between relative & absolute
					directory = arguments.get(0).startsWith("/") ? URIUtils.normalize(arguments.get(0)) : URIUtils.normalize(URIUtils.getChild(new URI(URIUtils.encodeURI(directory)), arguments.get(0)).getPath());
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
				arguments.add(0, command);
				try {
					return exec(directory, arguments.toArray(new String[arguments.size()]), inputBytes, systemProperties, redirectIO, includeError).trim();
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
	
	public static String exec(String directory, String [] commands, List<byte[]> inputContents, List<SystemProperty> systemProperties, boolean redirectIO, boolean includeError) throws IOException, InterruptedException {
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
		
		Process process;
		if (redirectIO) {
//			processBuilder.inheritIO();
			processBuilder.redirectInput(inputContents != null && !inputContents.isEmpty() ? Redirect.PIPE : Redirect.INHERIT);
			processBuilder.redirectError(Redirect.INHERIT);
			processBuilder.redirectOutput(Redirect.INHERIT);
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
		CopyStream inputStream = null, errorStream = null;
		ByteArrayOutputStream inputResult = null, errorResult = null;
		Thread inputThread = null, errorThread = null;
		try {
			// if we are not redirecting I/O to emulate system behavior, capture the content in a separate thread
			if (!redirectIO) {
				errorResult = new ByteArrayOutputStream();
				errorStream = new CopyStream(process.getErrorStream(), errorResult);
				errorThread = new Thread(errorStream);
				errorThread.start();
				
				inputResult = new ByteArrayOutputStream();
				inputStream = new CopyStream(process.getInputStream(), inputResult);
				inputThread = new Thread(inputStream);
				inputThread.start();
			}
			process.waitFor();
		}
		catch (InterruptedException e) {
			// do nothing
		}
		
		if (redirectIO) {
			return Integer.toString(process.exitValue());
		}
		else {

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
	
	public static class CopyStream implements Runnable, Closeable {
		private InputStream input;
		private OutputStream output;
		private boolean closed;
		private BufferedInputStream buffered;
		public CopyStream(InputStream input, OutputStream output) {
			this.input = input;
			this.output = output;
			this.buffered = new BufferedInputStream(input);
		}
		@Override
		public void run() {
			byte [] buffer = new byte[4096];
			int read = 0;
			try {
				while((read = buffered.read(buffer)) > 0) {
					output.write(buffer, 0, read);
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
	}
}
