package be.nabu.glue.impl.methods;

import java.util.ArrayList;
import java.util.List;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.Script;
import be.nabu.glue.impl.executors.EvaluateExecutor;
import be.nabu.glue.impl.methods.Validation.Level;
import be.nabu.libs.evaluator.api.Operation;

public class TestMethods {
	
	public static final String VALIDATION = "$validation";
	
	public static void confirm(String message, Boolean result) {
		check(message, result, true);
	}
	
	public static void validate(String message, Boolean result) {
		check(message, result, false);
	}	
	
	public static boolean not(Boolean value) {
		return value == null || !value ? true : false;
	}
	
	@SuppressWarnings("unchecked")
	public static void check(String message, Boolean result, boolean fail) {
		ScriptRuntime runtime = ScriptRuntime.getRuntime();
		
		// build callstack
		List<Script> callStack = new ArrayList<Script>();
		ScriptRuntime current = runtime;
		while (current != null) {
			callStack.add(current.getScript());
			current = current.getParent();
		}
		
		// get the validation that was performed
		// the operation should be a method operation
		Operation<ExecutionContext> operation = ((EvaluateExecutor) runtime.getExecutionContext().getCurrent()).getOperation();
		// 0 is the method name
		// 1 is the the string message
		// 2 is the operation to be executed
		String validation = operation.getParts().size() >= 3 ? operation.getParts().get(2).toString() : null;
		
		if (!runtime.getContext().containsKey(VALIDATION)) {
			runtime.getContext().put(VALIDATION, new ArrayList<Validation>());
		}
		List<Validation> messages = (List<Validation>) runtime.getContext().get(VALIDATION);
		
		Level level = null;
		if (result == null || !result) {
			level = fail ? Level.ERROR : Level.WARNING;
		}
		else {
			level = Level.INFO;
		}
		
		// add the message
		messages.add(new Validation(level, validation, message, callStack, runtime.getExecutionContext().getCurrent().getContext()));
		
		// stop if necessary
		if (level == Level.ERROR) {
			throw new AssertionError(message);
		}
	}
}
