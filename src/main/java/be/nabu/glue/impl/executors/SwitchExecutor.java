package be.nabu.glue.impl.executors;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.Operation;

public class SwitchExecutor extends BaseExecutor implements ExecutorGroup {

	private List<Executor> children = new ArrayList<Executor>();
	private Operation<ExecutionContext> toMatch, rewritten;
	private String variableName;
	private boolean isIf;
	
	public SwitchExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition, String variableName, Operation<ExecutionContext> toMatch, Executor...children) {
		super(parent, context, condition);
		this.variableName = variableName;
		this.toMatch = toMatch;
	}
	
	private Operation<ExecutionContext> getRewrittenToMatch() throws ExecutionException {
		// can only rewrite operations if we have a glue operation provider that can give us static method descriptions
		if (rewritten == null && toMatch != null) {
			synchronized(this) {
				if (rewritten == null) {
					try {
						rewritten = rewrite(toMatch);
					}
					catch (ParseException e) {
						throw new ExecutionException(e);
					}
				}
			}
		}
		return rewritten;
	}

	@Override
	public void execute(ExecutionContext context) throws ExecutionException {
		try {
			context.getPipeline().put(variableName, toMatch == null ? true : getRewrittenToMatch().evaluate(context));
			for (Executor child : children) {
				if (child.shouldExecute(context)) {
					child.execute(context);
					break;
				}
			}
			context.getPipeline().put(variableName, null);
		}
		catch (EvaluationException e) {
			throw new ExecutionException(e);
		}
	}

	@Override
	public List<Executor> getChildren() {
		return children;
	}

	public Operation<ExecutionContext> getToMatch() {
		return toMatch;
	}

	public String getVariableName() {
		return variableName;
	}

	public void setToMatch(Operation<ExecutionContext> toMatch) {
		this.toMatch = toMatch;
	}

	public boolean isIf() {
		return isIf;
	}

	public void setIf(boolean isIf) {
		this.isIf = isIf;
	}
	
}
