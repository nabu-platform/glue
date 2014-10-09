package be.nabu.glue.impl.methods;

import java.util.List;

import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.Script;

public class Validation {
	
	public enum Level {
		INFO,
		WARNING,
		ERROR
	}
	
	/**
	 * The level of this validation
	 */
	private Level level;
	/**
	 * The actual operation that was evaluated
	 */
	private String validation;
	/**
	 * The message that was passed along
	 */
	private String message;
	/**
	 * The callstack at the time of the validation
	 */
	private List<Script> callStack;
	/**
	 * The context of the executor (which line etc)
	 */
	private ExecutorContext context;
	
	public Validation(Level level, String validation, String message, List<Script> callStack, ExecutorContext context) {
		this.level = level;
		this.validation = validation;
		this.message = message;
		this.callStack = callStack;
		this.context = context;
	}

	public Level getLevel() {
		return level;
	}

	public String getValidation() {
		return validation;
	}

	public String getMessage() {
		return message;
	}

	public List<Script> getCallStack() {
		return callStack;
	}

	public ExecutorContext getContext() {
		return context;
	}
}
