package be.nabu.glue.core.impl.executors;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.Operation;

public class ForEachExecutor extends SequenceExecutor {

	private Operation<ExecutionContext> forEach, rewritten;
	private String temporaryVariable;
	private String temporaryIndex;
	private boolean allowVariableReuse = true, allowNonCollectionIteration = true;

	public ForEachExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition, Operation<ExecutionContext> forEach, String temporaryVariable, String temporaryIndex, Executor...steps) {
		super(parent, context, condition, steps);
		this.forEach = forEach;
		this.temporaryVariable = temporaryVariable;
		this.temporaryIndex = temporaryIndex;
	}

	private Operation<ExecutionContext> getRewrittenForEach() throws ParseException {
		// can only rewrite operations if we have a glue operation provider that can give us static method descriptions
		if (rewritten == null) {
			synchronized(this) {
				if (rewritten == null) {
					rewritten = rewrite(forEach);
				}
			}
		}
		return rewritten;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void execute(ExecutionContext context) throws ExecutionException {
		try {
			Object original = getRewrittenForEach().evaluate(context);
			if (original != null) {
				Iterable elements;
				if (original instanceof Iterable) {
					elements = GlueUtils.resolve((Iterable) original);
				}
				else if (original instanceof Collection) {
					elements = new ArrayList((Collection) original);
				}
				else if (original instanceof Object[]) {
					elements = new ArrayList(Arrays.asList((Object[]) original));
				}
				else if (original instanceof Number) {
					elements = new ArrayList();
					for (int i = 0; i < ((Number) original).intValue(); i++) {
						((List) elements).add(i);
					}
				}
				else {
					if (allowNonCollectionIteration) {
						elements = new ArrayList(Arrays.asList(original));
					}
					else {
						throw new ExecutionException("The variable " + forEach + " is not of type array or collection");
					}
				}
				if (!allowVariableReuse && context.getPipeline().get(temporaryVariable) != null) {
					throw new ExecutionException("The variable " + temporaryVariable + " is already taken, it can not be reused");
				}
				int index = 0;
				for (Object element : elements) {
					context.getPipeline().put(temporaryVariable, element);
					context.getPipeline().put(temporaryIndex, index++);
					super.execute(context);
					if (context.getBreakCount() > 0) {
						context.incrementBreakCount(-1);
						break;
					}
					else if (ScriptRuntime.getRuntime().isAborted()) {
						break;
					}
				}
				context.getPipeline().put(temporaryVariable, null);
				context.getPipeline().put(temporaryIndex, null);
			}
		}
		catch (EvaluationException e) {
			throw new ExecutionException(e);
		}
		catch (ParseException e) {
			throw new ExecutionException(e);
		}
	}

	public Operation<ExecutionContext> getForEach() {
		return forEach;
	}

	public String getTemporaryVariable() {
		return temporaryVariable;
	}

	public String getTemporaryIndex() {
		return temporaryIndex;
	}

	public void setTemporaryVariable(String temporaryVariable) {
		this.temporaryVariable = temporaryVariable;
	}

	public void setTemporaryIndex(String temporaryIndex) {
		this.temporaryIndex = temporaryIndex;
	}

	public void setForEach(Operation<ExecutionContext> forEach) {
		this.forEach = forEach;
	}

	public boolean isAllowVariableReuse() {
		return allowVariableReuse;
	}

	public void setAllowVariableReuse(boolean allowVariableReuse) {
		this.allowVariableReuse = allowVariableReuse;
	}
}
