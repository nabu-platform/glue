package be.nabu.glue.impl.executors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.Operation;

public class ForEachExecutor extends SequenceExecutor {

	private Operation<ExecutionContext> forEach;
	private String temporaryVariable;
	private String temporaryIndex;

	public ForEachExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition, Operation<ExecutionContext> forEach, String temporaryVariable, String temporaryIndex, Executor...steps) {
		super(parent, context, condition);
		this.forEach = forEach;
		this.temporaryVariable = temporaryVariable;
		this.temporaryIndex = temporaryIndex;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void execute(ExecutionContext context) throws ExecutionException {
		try {
			Object original = forEach.evaluate(context);
			if (original != null) {
				List elements = new ArrayList();
				if (original instanceof Collection) {
					elements.addAll((Collection) original);
				}
				else if (original instanceof Object[]) {
					elements.addAll(Arrays.asList((Object[]) original));
				}
				else if (original instanceof Integer) {
					for (int i = 0; i < (Integer) original; i++) {
						elements.add(i);
					}
				}
				else if (original instanceof Long) {
					for (long i = 0; i < (Long) original; i++) {
						elements.add(i);
					}
				}
				else {
					throw new ExecutionException("The variable " + forEach + " is not of type array or collection");
				}
				if (context.getPipeline().get(temporaryVariable) != null) {
					throw new ExecutionException("The variable " + temporaryVariable + " is already taken, it can not be reused");
				}
				int index = 0;
				for (Object element : elements) {
					context.getPipeline().put(temporaryVariable, element);
					context.getPipeline().put(temporaryIndex, index++);
					super.execute(context);
				}
				context.getPipeline().put(temporaryVariable, null);
				context.getPipeline().put(temporaryIndex, null);
			}
		}
		catch (EvaluationException e) {
			throw new ExecutionException(e);
		}
	}
}
