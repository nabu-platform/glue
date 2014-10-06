package be.nabu.glue.impl.executors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.libs.evaluator.api.Operation;

public class SequenceExecutor extends BaseExecutor implements ExecutorGroup {

	private List<Executor> children = new ArrayList<Executor>();
	
	public SequenceExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition, Executor...children) {
		super(parent, context, condition);
		this.children.addAll(Arrays.asList(children));
	}
	
	@Override
	public void execute(ExecutionContext context) throws ExecutionException {
		String timeout = context.getExecutionEnvironment().getParameters().get("timeout");
		for (Executor child : children) {
			context.setCurrent(child);
			if (context.getBreakpoint() != null && context.getBreakpoint().equals(child.getId())) {
				synchronized(Thread.currentThread()) {
					try {
						Thread.sleep(timeout == null ? Long.MAX_VALUE : new Long(timeout));
					}
					catch (InterruptedException e) {
						// continue;
					}
				}
			}
			if (child.shouldExecute(context)) {
				child.execute(context);
			}
		}
	}

	@Override
	public List<Executor> getChildren() {
		return children;
	}
}
