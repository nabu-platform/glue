package be.nabu.glue.impl.methods;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.Script;
import be.nabu.glue.impl.executors.EvaluateExecutor;
import be.nabu.glue.impl.methods.Validation.Level;
import be.nabu.libs.evaluator.api.Operation;

public class TestMethods {
	
	public static final String VALIDATION = "$validation";
	
	public static void must(String message, Object result) throws IOException {
		confirm(message, result);
	}
	
	public static void confirm(String message, Object result) throws IOException {
		if (result instanceof Boolean) {
			check(message, (Boolean) result, getCheck(), true);
		}
		else {
			check(message, result != null, result == null ? "null" : result.toString(), true);
		}
	}
	
	public static void must(String message, Object expected, Object actual) throws IOException {
		confirm(message, expected, actual);
	}
	
	public static void confirm(String message, Object expected, Object actual) throws IOException {
		if (expected instanceof Object[]) {
			expected = Arrays.asList((Object[]) expected);
		}
		if (actual instanceof Object[]) {
			actual = Arrays.asList((Object[]) actual);
		}
		boolean result = (expected == null && actual == null) || (expected != null && expected.equals(actual));
		check(message, result, result ? (expected == null ? "null" : expected.toString()) : expected + " != " + actual, true);
	}
	
	public static void should(String message, Boolean result) throws IOException {
		
	}
		
	public static void validate(String message, Boolean result) throws IOException {
		check(message, result, getCheck(), false);
	}
	
	public static void should(String message, Object expected, Object actual) throws IOException {
		validate(message, expected, actual);
	}
	
	public static void validate(String message, Object expected, Object actual) throws IOException {
		if (expected instanceof Object[]) {
			expected = Arrays.asList((Object[]) expected);
		}
		if (actual instanceof Object[]) {
			actual = Arrays.asList((Object[]) actual);
		}
		boolean result = (expected == null && actual == null) || (expected != null && expected.equals(actual));
		check(message, result, result ? (expected == null ? "null" : expected.toString()) : expected + " != " + actual, false);
	}
	
	public static boolean not(Boolean value) {
		return value == null || !value ? true : false;
	}
	
	@SuppressWarnings("unchecked")
	public static Validation[] report() {
		List<Validation> messages = (List<Validation>) ScriptRuntime.getRuntime().getContext().get(VALIDATION);
		return messages == null || messages.isEmpty() ? null : messages.toArray(new Validation[0]);
	}
	
	private static String getCheck() {
		// get the validation that was performed
		// the operation should be a method operation
		Operation<ExecutionContext> operation = ((EvaluateExecutor) ScriptRuntime.getRuntime().getExecutionContext().getCurrent()).getOperation();
		// 0 is the method name
		// 1 is the the string message
		// 2 is the operation to be executed
		return operation.getParts().size() >= 3 ? operation.getParts().get(2).getContent().toString() : null;
	}
	
	@SuppressWarnings("unchecked")
	public static void check(String message, Boolean result, String check, boolean fail) throws IOException {
		ScriptRuntime runtime = ScriptRuntime.getRuntime();
		
		// build callstack
		List<Script> callStack = new ArrayList<Script>();
		ScriptRuntime current = runtime;
		while (current != null) {
			callStack.add(current.getScript());
			current = current.getParent();
		}
		if (!runtime.getContext().containsKey(VALIDATION)) {
			runtime.getContext().put(VALIDATION, new ArrayList<Validation>());
		}
		List<Validation> messages = (List<Validation>) runtime.getContext().get(VALIDATION);
		
		Level level = result == null || !result ? Level.ERROR : Level.INFO;
		
		// add the message
		Validation validation = new Validation(level, check, message, callStack, runtime.getExecutionContext().getCurrent().getContext());
		messages.add(validation);
		
		// stop if necessary
		if (level == Level.ERROR && fail) {
			throw new AssertionError(validation.toString());
		}
		else {
			ScriptMethods.echo(validation);
		}
	}
}
