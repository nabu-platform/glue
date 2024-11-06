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

package be.nabu.glue.core.impl;

import java.util.Map;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.core.api.EnclosedLambda;
import be.nabu.libs.evaluator.api.Operation;

public class LambdaImpl implements EnclosedLambda {

	private MethodDescription description;
	private Operation<ExecutionContext> operation;
	private Map<String, Object> context;
	private boolean mutable;
	private Operation<ExecutionContext> originalOperation;

	public LambdaImpl(MethodDescription description, Operation<ExecutionContext> operation, Map<String, Object> context) {
		this(description, operation, context, false);
	}
	
	public LambdaImpl(MethodDescription description, Operation<ExecutionContext> operation, Map<String, Object> context, boolean mutable) {
		this.description = description;
		this.originalOperation = operation;
		this.operation = operation;
		this.context = context;
		this.mutable = mutable;
	}
	
	@Override
	public Operation<ExecutionContext> getOperation() {
		return operation;
	}

	@Override
	public MethodDescription getDescription() {
		return description;
	}

	@Override
	public Map<String, Object> getEnclosedContext() {
		return context;
	}

	@Override
	public String toString() {
		return this.operation.toString();
	}

	@Override
	public boolean isMutable() {
		return mutable;
	}

	public Map<String, Object> getContext() {
		return context;
	}

	public void setContext(Map<String, Object> context) {
		this.context = context;
	}

	public void setDescription(MethodDescription description) {
		this.description = description;
	}

	public void setOperation(Operation<ExecutionContext> operation) {
		this.operation = operation;
	}

	public void setMutable(boolean mutable) {
		this.mutable = mutable;
	}
	
	public Operation<ExecutionContext> getOriginal() {
		return originalOperation;
	}
}
