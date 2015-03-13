package be.nabu.glue.impl.executors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.glue.ScriptRuntime;
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
			else if (context.isDebug()) {
				ScriptRuntime.getRuntime().getFormatter().print("Skipping " + child.getContext().getLine());
			}
		}
	}

	@Override
	public List<Executor> getChildren() {
		return children;
	}
}
