package be.nabu.glue.impl.providers;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.MethodProvider;
import be.nabu.glue.impl.methods.ScriptMethods;
import be.nabu.glue.impl.methods.ShellMethods;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseOperation;
import be.nabu.utils.io.IOUtils;

public class CLIMethodProvider implements MethodProvider {

	@Override
	public Operation<ExecutionContext> resolve(String name) {
		if (name.matches("^cli\\.[\\w]+$")) {
			return new CLIOperation(name.substring("cli.".length()));
		}
		return null;
	}

	@Override
	public List<MethodDescription> getAvailableMethods() {
		return new ArrayList<MethodDescription>();
	}

	private static class CLIOperation extends BaseOperation<ExecutionContext> {

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
			List<String> arguments = new ArrayList<String>();
			List<byte []> inputBytes = new ArrayList<byte[]>();
			for (int i = 1; i < getParts().size(); i++) {
				Operation<ExecutionContext> argumentOperation = (Operation<ExecutionContext>) getParts().get(i).getContent();
				// if you have a greater then, you can redirect
				if (argumentOperation.getType() == OperationType.CLASSIC && argumentOperation.getParts().size() == 3 && argumentOperation.getParts().get(1).getType() == Type.GREATER) {
					Object evaluated = ((Operation<ExecutionContext>) argumentOperation.getParts().get(2).getContent()).evaluate(context);
					if (evaluated != null) {
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
					String value = converter.convert(evaluated, String.class);
					if (evaluated != null && value == null) {
						throw new EvaluationException("Can not convert " + evaluated + " to string");
					}
					else if (value == null) {
						throw new EvaluationException("Null values are currently not allowed for CLI methods");
					}
					arguments.add(value);
				}
			}
			arguments.add(0, command);
			try {
//				return SystemMethods.exec(ShellMethods.pwd(), arguments.toArray(new String[arguments.size()]));
				return exec(ShellMethods.pwd(), arguments.toArray(new String[arguments.size()]), inputBytes);
			}
			catch (IOException e) {
				throw new EvaluationException(e);
			}
			catch (InterruptedException e) {
				throw new EvaluationException(e);
			}
		}

		@Override
		public OperationType getType() {
			return OperationType.METHOD;
		}
	}
	
	private static String exec(String directory, String [] commands, List<byte[]> inputContents) throws IOException, InterruptedException {
		// apparently if you do something like "mvn dependency:tree" in one string, it will fail but if you do "mvn" and "dependency:tree" it fails
		// this is however annoying to enforce on the user, so do a preliminary split
		List<String> splittedCommands = new ArrayList<String>();
		for (String command : commands) {
			splittedCommands.addAll(Arrays.asList(command.split("(?<!\\\\)[\\s]+")));
		}
		Process process = Runtime.getRuntime().exec(splittedCommands.toArray(new String[0]), null, new File(directory));
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
			return new String(bytes);
		}
		finally {
			input.close();
		}
	}
}
