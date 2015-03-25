package be.nabu.glue.impl.methods;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.runs.AssertionException;
import be.nabu.glue.api.runs.CallLocation;
import be.nabu.glue.api.runs.Validation.Level;
import be.nabu.glue.impl.SimpleCallLocation;
import be.nabu.glue.impl.executors.EvaluateExecutor;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.evaluator.api.Operation;

@MethodProviderClass(namespace = "test")
public class TestMethods {
	
	public static final String VALIDATION = "$validation";
	
	public static boolean confirmNotNull(String message, Object result) throws IOException {
		return check(message, result != null, stringify(result), true);
	}
	
	public static boolean confirmNull(String message, Object result) throws IOException {
		return check(message, result == null, stringify(result), true);
	}
	
	public static boolean confirmTrue(String message, Boolean result) throws IOException {
		return check(message, result, getCheck(), true);
	}
	
	public static boolean confirmFalse(String message, Boolean result) throws IOException {
		return check(message, !result, getCheck(), true);
	}
	
	public static boolean confirmMatches(String message, String regex, Object actual) throws IOException {
		return checkMatches(message, regex, actual, true);
	}

	private static boolean checkNotMatches(String message, String regex, Object actual, boolean fail) {
		if (actual == null) {
			return check(message, false, "null ~ " + regex, true);
		}
		else {
			String converted = ConverterFactory.getInstance().getConverter().convert(actual, String.class);
			if (converted == null) {
				return check(message, false, stringify(actual) + " ~ " + regex + " (incompatible types)", fail);
			}
			else {
				return check(message, !converted.matches(regex), converted + " !~ " + regex, fail);
			}
		}
	}
	
	private static boolean checkMatches(String message, String regex, Object actual, boolean fail) {
		if (actual == null) {
			return check(message, false, "null !~ " + regex, true);
		}
		else {
			String converted = ConverterFactory.getInstance().getConverter().convert(actual, String.class);
			if (converted == null) {
				return check(message, false, stringify(actual) + " !~ " + regex + " (incompatible types)", fail);
			}
			else {
				return check(message, converted.matches(regex), converted + " ~ " + regex, fail);
			}
		}
	}
	
	public static boolean confirmEquals(String message, Object expected, Object actual) throws IOException {
		return checkEquals(message, expected, actual, true);
	}

	private static boolean checkEquals(String message, Object expected, Object actual, boolean fail) {
		if (expected instanceof Object[]) {
			expected = Arrays.asList((Object[]) expected);
		}
		if (actual instanceof Object[]) {
			actual = Arrays.asList((Object[]) actual);
		}
		boolean checked = false;
		boolean result = false;
		// need to make sure they are of the same type
		if (expected != null && actual != null) {
			Object converted = ConverterFactory.getInstance().getConverter().convert(actual, expected.getClass());
			if (converted == null) {
				result = check(message, false, stringify(expected) + " != " + stringify(actual) + " (incompatible types)", fail);
				checked = true;
			}
			else {
				actual = converted;
			}
		}
		if (!checked) {
			result = (expected == null && actual == null) || (expected != null && expected.equals(actual));
			check(message, result, result ? stringify(expected) : stringify(expected) + " != " + stringify(actual), fail);
		}
		return result;
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
	
	public static boolean validateNotNull(String message, Object result) throws IOException {
		return check(message, result != null, stringify(result), false);
	}
	
	public static boolean validateNull(String message, Object result) throws IOException {
		return check(message, result == null, stringify(result), false);
	}
	
	public static boolean validateTrue(String message, Boolean result) throws IOException {
		return check(message, result, getCheck(), false);
	}
	
	public static boolean validateFalse(String message, Boolean result) throws IOException {
		return check(message, !result, getCheck(), false);
	}
	
	public static boolean validateEquals(String message, Object expected, Object actual) throws IOException {
		return checkEquals(message, expected, actual, false);
	}
	
	public static boolean validateMatches(String message, String regex, Object actual) throws IOException {
		return checkMatches(message, regex, actual, false);
	}
	
	public static boolean validateNotMatches(String message, String regex, Object actual) throws IOException {
		return checkNotMatches(message, regex, actual, false);
	}
	
	public static boolean confirmNotMatches(String message, String regex, Object actual) throws IOException {
		return checkNotMatches(message, regex, actual, true);
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
	public static boolean check(String message, Boolean result, String check, boolean fail) {
		ScriptRuntime runtime = ScriptRuntime.getRuntime();
		
		// build callstack
		List<CallLocation> callStack = new ArrayList<CallLocation>();
		ScriptRuntime current = runtime;
		while (current != null) {
			callStack.add(new SimpleCallLocation(current.getScript(), current.getExecutionContext().getCurrent()));
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
			throw new AssertionException(validation.toString());
		}
		else {
			runtime.getFormatter().validated(validation);
		}
		return result != null && result;
	}
}
