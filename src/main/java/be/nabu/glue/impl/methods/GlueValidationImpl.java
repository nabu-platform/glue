package be.nabu.glue.impl.methods;

import java.util.Date;
import java.util.List;

import be.nabu.glue.api.Executor;
import be.nabu.glue.api.runs.CallLocation;
import be.nabu.glue.api.runs.GlueValidation;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

public class GlueValidationImpl implements GlueValidation {
	
	/**
	 * The level of this validation
	 */
	private Severity severity;
	/**
	 * The actual operation that was evaluated
	 */
	private String message;
	/**
	 * The message that was passed along
	 */
	private String description;
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
	
	public GlueValidationImpl(Severity severity, String message, String description, List<CallLocation> callStack, Executor executor) {
		this.severity = severity;
		this.message = message;
		this.description = description;
		this.callStack = callStack;
		this.executor = executor;
	}

	@Override
	public Severity getSeverity() {
		return severity;
	}

	@Override
	public String getMessage() {
		return message;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public List<CallLocation> getContext() {
		return callStack;
	}

	@Override
	public String toString() {
		return "[" + getSeverity() + "] " + getDescription() + ": " + getMessage();
	}

	@Override
	public Executor getExecutor() {
		return executor;
	}

	@Override
	public Date getTimestamp() {
		return timestamp;
	}

	@Override
	public Integer getCode() {
		return executor != null && executor.getContext() != null ? executor.getContext().getLineNumber() : 0;
	}
}
