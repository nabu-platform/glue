package be.nabu.glue.impl.executors;

import java.util.UUID;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.Operation;

abstract public class BaseExecutor implements Executor {

	private ExecutorContext context;
	private Operation<ExecutionContext> condition;
	private UUID uuid;
	private ExecutorGroup parent;

	public BaseExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition) {
		this.parent = parent;
		this.context = context;
		this.condition = condition;
	}

	@Override
	public ExecutorContext getContext() {
		return context;
	}
	
	public String getId() {
		if (uuid == null) {
			uuid = UUID.randomUUID();
		}
		return uuid.toString();
	}

	@Override
	public boolean shouldExecute(ExecutionContext context) throws ExecutionException {
		if (getContext().getLabel() != null) {
			return context.getLabelEvaluator() != null
				? context.getLabelEvaluator().shouldExecute(getContext().getLabel(), context.getExecutionEnvironment())
				: getContext().getLabel().equalsIgnoreCase(context.getExecutionEnvironment().getName());
		}
		else if (condition != null) {
			try {
				return (Boolean) condition.evaluate(context);
			}
			catch (EvaluationException e) {
				throw new ExecutionException(e);
			}
		}
		else {
			return true;
		}
	}

	@Override
	public ExecutorGroup getParent() {
		return parent;
	}
}
