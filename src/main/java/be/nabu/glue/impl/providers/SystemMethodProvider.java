package be.nabu.glue.impl.providers;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.MethodProvider;
import be.nabu.glue.impl.methods.ScriptMethods;
import be.nabu.glue.impl.methods.ShellMethods;
import be.nabu.glue.impl.methods.StringMethods;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseMethodOperation;
import be.nabu.libs.resources.URIUtils;
import be.nabu.utils.io.IOUtils;

public class SystemMethodProvider implements MethodProvider {

	public static final String CLI_DIRECTORY = "cli.directory";

	private static List<String> predefined = Arrays.asList("system.exec", "system.linux", "system.input");
	
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
			List<byte []> inputBytes = new ArrayList<byte[]>();
			for (int i = 1; i < getParts().size(); i++) {
				Operation<ExecutionContext> argumentOperation = (Operation<ExecutionContext>) getParts().get(i).getContent();
				// if you have a greater then, you can redirect
				if (argumentOperation.getType() == OperationType.CLASSIC && argumentOperation.getParts().size() == 3 && argumentOperation.getParts().get(1).getType() == Type.GREATER) {
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
				else {
					Object evaluated = argumentOperation.evaluate(context);
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
					return exec(directory, arguments.toArray(new String[arguments.size()]), inputBytes).trim();
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
	
	private static String exec(String directory, String [] commands, List<byte[]> inputContents) throws IOException, InterruptedException {
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
		Process process = Runtime.getRuntime().exec(commands, null, dir);
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
		process.waitFor();
		InputStream input = new BufferedInputStream(process.getInputStream());
		try {
			byte [] bytes = IOUtils.toBytes(IOUtils.wrap(input));
			return bytes == null ? null : new String(bytes);
		}
		finally {
			input.close();
		}
	}
}
