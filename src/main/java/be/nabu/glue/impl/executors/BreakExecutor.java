package be.nabu.glue.impl.executors;

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
