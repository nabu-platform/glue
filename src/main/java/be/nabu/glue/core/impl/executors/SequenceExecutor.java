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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.evaluator.api.Operation;

public class SequenceExecutor extends BaseExecutor implements ExecutorGroup {

	private List<Executor> children = new ArrayList<Executor>();
	private boolean ignoreFailure = false;
	
	public SequenceExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition, Executor...children) {
		super(parent, context, condition);
		this.children.addAll(Arrays.asList(children));
	}
	
	@Override
	public void execute(ExecutionContext context) throws ExecutionException {
		String timeout = context.getExecutionEnvironment().getParameters().get("timeout");
		try {
			for (Executor child : children) {
				if (child instanceof CatchExecutor) {
					continue;
				}
				else if (child instanceof FinallyExecutor) {
					continue;
				}
				context.setCurrent(child);
				if (context.isTrace() && context.getBreakpoints() != null && context.getBreakpoints().contains(child.getId())) {
					synchronized(Thread.currentThread()) {
						try {
							Thread.sleep(timeout == null ? Long.MAX_VALUE : new Long(timeout));
						}
						catch (InterruptedException e) {
							// continue;
						}
					}
				}
				if (ScriptRuntime.getRuntime().isAborted()) {
					break;
				}
				else if (context.getBreakCount() > 0) {
					break;
				}
				else if (child.shouldExecute(context)) {
					ScriptRuntime.getRuntime().getFormatter().before(child);
					child.execute(context);
					ScriptRuntime.getRuntime().getFormatter().after(child);
				}
			}
		}
		catch (Exception e) {
			context.getPipeline().put("$exception", e);
			boolean handled = false;
			for (Executor child : children) {
				if (child instanceof CatchExecutor) {
					((CatchExecutor) child).execute(context);
					handled = true;
				}
			}
			context.getPipeline().remove("$exception");
			if (!handled && !ignoreFailure) {
				throw new ExecutionException(e);
			}
		}
		finally {
			for (Executor child : children) {
				if (child instanceof FinallyExecutor) {
					((FinallyExecutor) child).execute(context);
				}
			}
		}
	}

	public boolean isIgnoreFailure() {
		return ignoreFailure;
	}

	public void setIgnoreFailure(boolean ignoreFailure) {
		this.ignoreFailure = ignoreFailure;
	}

	@Override
	public List<Executor> getChildren() {
		return children;
	}
}
