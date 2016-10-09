package be.nabu.glue.core.impl.executors;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.libs.evaluator.api.Operation;

public class FinallyExecutor extends SequenceExecutor {

	public FinallyExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition, Executor...children) {
		super(parent, context, condition, children);
	}

}
