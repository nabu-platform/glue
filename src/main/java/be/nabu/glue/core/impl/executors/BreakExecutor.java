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

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.libs.evaluator.api.Operation;

public class BreakExecutor extends BaseExecutor {

	private int breakCount;

	public BreakExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition, int breakCount) {
		super(parent, context, condition);
		this.breakCount = breakCount;
	}

	@Override
	public void execute(ExecutionContext context) throws ExecutionException {
		context.incrementBreakCount(breakCount);
	}

	public int getBreakCount() {
		return breakCount;
	}

	public void setBreakCount(int breakCount) {
		this.breakCount = breakCount;
	}
}
