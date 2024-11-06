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

package be.nabu.glue.core.impl.executors;

import java.text.ParseException;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.Operation;

public class WhileExecutor extends SequenceExecutor {

	private Operation<ExecutionContext> whileOperation, rewritten;
	private Converter converter = ConverterFactory.getInstance().getConverter();

	public WhileExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition, Operation<ExecutionContext> whileOperation, Executor...children) {
		super(parent, context, condition, children);
		this.whileOperation = whileOperation;
	}

	private Operation<ExecutionContext> getRewrittenWhile() throws ExecutionException {
		// can only rewrite operations if we have a glue operation provider that can give us static method descriptions
		if (rewritten == null && whileOperation != null) {
			synchronized(this) {
				if (rewritten == null) {
					try {
						rewritten = rewrite(whileOperation);
					}
					catch (ParseException e) {
						throw new ExecutionException(e);
					}
				}
			}
		}
		return rewritten;
	}
	
	public void execute(ExecutionContext context) throws ExecutionException {
		try {
			Boolean result = converter.convert(getRewrittenWhile().evaluate(context), Boolean.class);
			while (result != null && result && !ScriptRuntime.getRuntime().isAborted()) {
				super.execute(context);
				if (context.getBreakCount() > 0) {
					context.incrementBreakCount(-1);
					break;
				}
				// execute again
				result = converter.convert(getRewrittenWhile().evaluate(context), Boolean.class);	
			}
		}
		catch (EvaluationException e) {
			throw new ExecutionException(e);
		}
	}
	
	public Operation<ExecutionContext> getWhile() {
		return whileOperation;
	}

	public void setWhile(Operation<ExecutionContext> whileOperation) {
		this.whileOperation = whileOperation;
	}
}
