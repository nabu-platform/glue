package be.nabu.glue.impl.methods;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.runs.Validation.Level;
import be.nabu.glue.impl.executors.EvaluateExecutor;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.api.Operation;

public class TestMethods {
	
	public static final String VALIDATION = "$validation";
	
	public static void confirmNotNull(String message, Object result) throws IOException {
		check(message, result != null, stringify(result), true);
	}
	
	public static void confirmNull(String message, Object result) throws IOException {
		check(message, result == null, stringify(result), true);
	}
	
	public static void confirmTrue(String message, Boolean result) throws IOException {
		check(message, result, getCheck(), true);
	}
	
	public static void confirmFalse(String message, Boolean result) throws IOException {
		check(message, !result, getCheck(), true);
	}
	
	public static void confirmMatches(String message, String regex, Object actual) throws IOException {
		checkMatches(message, regex, actual, true);
	}

	private static void checkMatches(String message, String regex, Object actual, boolean fail) {
		if (actual == null) {
			check(message, false, "null !~ " + regex, true);
		}
		else {
			String converted = ConverterFactory.getInstance().getConverter().convert(actual, String.class);
			if (converted == null) {
				check(message, false, stringify(actual) + " !~ " + regex + " (incompatible types)", fail);
			}
			else {
				check(message, converted.matches(regex), converted + " ~ " + regex, fail);
			}
		}
	}
	
	public static void confirmEquals(String message, Object expected, Object actual) throws IOException {
		checkEquals(message, expected, actual, true);
	}

	private static void checkEquals(String message, Object expected, Object actual, boolean fail) {
		if (expected instanceof Object[]) {
			expected = Arrays.asList((Object[]) expected);
		}
		if (actual instanceof Object[]) {
			actual = Arrays.asList((Object[]) actual);
		}
		boolean checked = false;
		// need to make sure they are of the same type
		if (expected != null && actual != null) {
			Object converted = ConverterFactory.getInstance().getConverter().convert(actual, expected.getClass());
			if (converted == null) {
				check(message, false, stringify(expected) + " != " + stringify(actual) + " (incompatible types)", fail);
				checked = true;
			}
			else {
				actual = converted;
			}
		}
		if (!checked) {
			boolean result = (expected == null && actual == null) || (expected != null && expected.equals(actual));
			check(message, result, result ? stringify(expected) : stringify(expected) + " != " + stringify(actual), fail);
		}
	}
	
	private static String stringify(Object object) {
		if (object instanceof Object[]) {
			List<Object> list = new ArrayList<Object>(Arrays.asList((Object[]) object));
			// check for arrays in the array
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i) instanceof Object[]) {
					list.set(i, Arrays.asList((Object[]) list.get(i)));
				}
			}
			return list.toString();
		}
		else {
			return object == null ? "null" : object.toString();
		}
	}
	
	public static void validateNotNull(String message, Object result) throws IOException {
		check(message, result != null, stringify(result), false);
	}
	
	public static void validateNull(String message, Object result) throws IOException {
		check(message, result == null, stringify(result), false);
	}
	
	public static void validateTrue(String message, Boolean result) throws IOException {
		check(message, result, getCheck(), false);
	}
	
	public static void validateFalse(String message, Boolean result) throws IOException {
		check(message, !result, getCheck(), false);
	}
	
	public static void validateEquals(String message, Object expected, Object actual) throws IOException {
		checkEquals(message, expected, actual, false);
	}
	
	public static void validateMatches(String message, String regex, Object actual) throws IOException {
		checkMatches(message, regex, actual, false);
	}
	
	public static boolean not(Boolean value) {
		return value == null || !value ? true : false;
	}
	
	@SuppressWarnings("unchecked")
	public static ValidationImpl[] report() {
		List<ValidationImpl> messages = (List<ValidationImpl>) ScriptRuntime.getRuntime().getContext().get(VALIDATION);
		return messages == null || messages.isEmpty() ? null : messages.toArray(new ValidationImpl[0]);
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
	public static void check(String message, Boolean result, String check, boolean fail) {
		ScriptRuntime runtime = ScriptRuntime.getRuntime();
		
		// build callstack
		List<Script> callStack = new ArrayList<Script>();
		ScriptRuntime current = runtime;
		while (current != null) {
			callStack.add(current.getScript());
			current = current.getParent();
		}
		if (!runtime.getContext().containsKey(VALIDATION)) {
			runtime.getContext().put(VALIDATION, new ArrayList<ValidationImpl>());
		}
		List<ValidationImpl> messages = (List<ValidationImpl>) runtime.getContext().get(VALIDATION);
		
		Level level = result == null || !result ? Level.ERROR : Level.INFO;
		
		// add the message
		ValidationImpl validation = new ValidationImpl(level, check, message, callStack, runtime.getExecutionContext().getCurrent());
		messages.add(validation);
		
		// stop if necessary
		if (level == Level.ERROR && fail) {
			throw new AssertionError(validation.toString());
		}
		else {
			runtime.getFormatter().validated(validation);
		}
	}
}
