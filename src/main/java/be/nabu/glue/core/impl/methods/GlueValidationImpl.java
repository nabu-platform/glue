/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.glue.core.impl.methods;

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
	public String getCode() {
		return Integer.toString(executor != null && executor.getContext() != null ? executor.getContext().getLineNumber() : 0);
	}

	@Override
	public Date getCreated() {
		return timestamp;
	}
}
