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
		if (getContext().getAnnotations().containsKey("disabled")) {
			return false;
		}
		boolean shouldExecute = true;
		if (getContext().getLabel() != null) {
			shouldExecute = context.getLabelEvaluator() != null
				? context.getLabelEvaluator().shouldExecute(getContext().getLabel(), context.getExecutionEnvironment())
				: getContext().getLabel().equalsIgnoreCase(context.getExecutionEnvironment().getName());
		}
		if (shouldExecute && condition != null) {
			try {
				shouldExecute = (Boolean) condition.evaluate(context);
			}
			catch (EvaluationException e) {
				throw new ExecutionException(e);
			}
		}
		return shouldExecute;
	}

	@Override
	public ExecutorGroup getParent() {
		return parent;
	}

	public Operation<ExecutionContext> getCondition() {
		return condition;
	}

	public void setParent(ExecutorGroup parent) {
		this.parent = parent;
	}

	public void setCondition(Operation<ExecutionContext> condition) {
		this.condition = condition;
	}
}
