package be.nabu.glue.impl.methods;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.runs.AssertionException;
import be.nabu.glue.api.runs.CallLocation;
import be.nabu.glue.api.runs.GlueValidation;
import be.nabu.glue.impl.SimpleCallLocation;
import be.nabu.glue.impl.executors.EvaluateExecutor;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

@MethodProviderClass(namespace = "test")
public class TestMethods {
	
	public static final String VALIDATION = "$validation";
	
	public static final int ELLIPSIS = Integer.parseInt(System.getProperty("test.ellipsis", "250"));
	
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
	
	public static boolean confirmContains(String message, String regex, Object actual) throws IOException {
		return checkMatches(message, "(?i)(?s).*" + regex + ".*", actual, true);
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
				return check(message, !converted.matches(regex), !converted.matches(regex) ? regex : converted + " !~ " + regex, fail);
			}
		}
	}
	
	private static boolean checkMatches(String message, String regex, Object actual, boolean fail) {
		if (actual == null) {
			return check(message, false, "null !~ " + regex, fail);
		}
		else {
			String converted = ConverterFactory.getInstance().getConverter().convert(actual, String.class);
			if (converted == null) {
				return check(message, false, stringify(actual) + " !~ " + regex + " (incompatible types)", fail);
			}
			else {
				return check(message, converted.matches(regex), converted.matches(regex) ? regex : converted + " ~ " + regex, fail);
			}
		}
	}
	
	public static boolean confirmEquals(String message, Object expected, Object actual) throws IOException {
		return checkEquals(message, expected, actual, true, false);
	}
	
	public static boolean confirmNotEquals(String message, Object expected, Object actual) throws IOException {
		return checkEquals(message, expected, actual, true, true);
	}

	private static boolean checkEquals(String message, Object expected, Object actual, boolean fail, boolean invert) {
		if (expected instanceof String) {
			expected = preprocess((String) expected);
		}
		else if (expected instanceof Object[]) {
			expected = Arrays.asList((Object[]) expected);
		}
		if (actual instanceof String) {
			actual = preprocess((String) actual);
		}
		else if (actual instanceof Object[]) {
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
			if (invert) {
				result = !result;
			}
			check(message, result, result ? stringify(expected) : stringify(expected) + (invert ? " = " : " != ") + stringify(actual), fail);
		}
		return result;
	}
	
	private static String preprocess(String actual) {
		// remove carriage returns, they are nasty cross-system
		return actual == null ? null : actual.replace("\r", "");
	}

	private static String stringify(Object object) {
		String content;
		if (object instanceof Object[]) {
			List<Object> list = new ArrayList<Object>(Arrays.asList((Object[]) object));
			// check for arrays in the array
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i) instanceof Object[]) {
					list.set(i, Arrays.asList((Object[]) list.get(i)));
				}
			}
			content = list.toString();
		}
		else {
			content = object == null ? "null" : object.toString();
		}
		if (ELLIPSIS > 0 && content.length() > ELLIPSIS && !ScriptRuntime.getRuntime().getExecutionContext().isDebug()) {
			content = content.substring(0, ELLIPSIS) + "...";
		}
		return content;
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
	
	@GlueMethod(description = "Checks that the actual value equals the expected value")
	public static boolean validateEquals(
			@GlueParam(name = "message", description = "The validation message") String message, 
			@GlueParam(name = "expected", description = "The expected value") Object expected, 
			@GlueParam(name = "actual", description = "The actual value") Object actual) throws IOException {
		return checkEquals(message, expected, actual, false, false);
	}
	
	@GlueMethod(description = "Checks that the actual value does not equal the expected value")
	public static boolean validateNotEquals(
			@GlueParam(name = "message", description = "The validation message") String message, 
			@GlueParam(name = "expected", description = "The expected value") Object expected, 
			@GlueParam(name = "actual", description = "The actual value") Object actual) throws IOException {
		return checkEquals(message, expected, actual, false, true);
	}
	
	public static boolean validateMatches(String message, String regex, Object actual) throws IOException {
		return checkMatches(message, regex, actual, false);
	}
	
	public static boolean validateContains(String message, String regex, Object actual) throws IOException {
		return checkMatches(message, "(?i)(?s).*" + regex + ".*", actual, false);
	}
	
	public static boolean validateNotMatches(String message, String regex, Object actual) throws IOException {
		return checkNotMatches(message, regex, actual, false);
	}
	
	public static boolean validateNotContains(String message, String regex, Object actual) throws IOException {
		return checkNotMatches(message, "(?i)(?s).*" + regex + ".*", actual, false);
	}
	
	public static boolean confirmNotMatches(String message, String regex, Object actual) throws IOException {
		return checkNotMatches(message, regex, actual, true);
	}
	
	public static boolean confirmNotContains(String message, String regex, Object actual) throws IOException {
		return checkNotMatches(message, "(?i)(?s).*" + regex + ".*", actual, true);
	}
	
	public static boolean not(Boolean value) {
		return value == null || !value ? true : false;
	}
	
	@SuppressWarnings("unchecked")
	public static GlueValidation[] report() {
		List<GlueValidation> messages = (List<GlueValidation>) ScriptRuntime.getRuntime().getContext().get(VALIDATION);
		return messages == null || messages.isEmpty() ? null : messages.toArray(new GlueValidationImpl[0]);
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
	
	public static boolean check(String message, Boolean result, String check, boolean fail) {
		Severity level = result == null || !result ? Severity.ERROR : Severity.INFO;
		addValidation(level, check, message, fail);
		return result != null && result;
	}
	
	@SuppressWarnings("unchecked")
	public static void addValidation(Severity severity, String message, String description, boolean fail) {
		ScriptRuntime runtime = ScriptRuntime.getRuntime();
		
		// build callstack
		List<CallLocation> callStack = new ArrayList<CallLocation>();
		ScriptRuntime current = runtime;
		while (current != null) {
			callStack.add(new SimpleCallLocation(current.getScript(), current.getExecutionContext().getCurrent()));
			current = current.getParent();
		}
		if (!runtime.getContext().containsKey(VALIDATION)) {
			runtime.getContext().put(VALIDATION, new ArrayList<GlueValidation>());
		}
		List<GlueValidation> messages = (List<GlueValidation>) runtime.getContext().get(VALIDATION);
		
		
		// add the message
		GlueValidation validation = new GlueValidationImpl(severity, message, description, callStack, runtime.getExecutionContext().getCurrent());
		messages.add(validation);
		
		runtime.getFormatter().validated(validation);
		// stop if necessary
		if (severity == Severity.ERROR && fail) {
			throw new AssertionException(validation.toString());
		}
	}
}
