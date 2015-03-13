package be.nabu.glue.impl.methods;

import java.util.Date;
import java.util.List;

import be.nabu.glue.api.Executor;
import be.nabu.glue.api.runs.CallLocation;
import be.nabu.glue.api.runs.Validation;

public class ValidationImpl implements Validation {
	
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
	private List<CallLocation> callStack;
	/**
	 * When the validation occurred
	 */
	private Date timestamp = new Date();
	/**
	 * The executor
	 */
	private Executor executor;
	
	public ValidationImpl(Level level, String validation, String message, List<CallLocation> callStack, Executor executor) {
		this.level = level;
		this.validation = validation;
		this.message = message;
		this.callStack = callStack;
		this.executor = executor;
	}

	@Override
	public Level getLevel() {
		return level;
	}

	@Override
	public String getValidation() {
		return validation;
	}

	@Override
	public String getMessage() {
		return message;
	}

	@Override
	public List<CallLocation> getCallStack() {
		return callStack;
	}

	@Override
	public String toString() {
		return "[" + getLevel() + "] " + getMessage() + ": " + getValidation();
	}

	@Override
	public Executor getExecutor() {
		return executor;
	}

	@Override
	public Date getTimestamp() {
		return timestamp;
	}
}
