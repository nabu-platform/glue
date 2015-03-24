package be.nabu.glue.impl.executors;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.Operation;

public class WhileExecutor extends SequenceExecutor {

	private Operation<ExecutionContext> whileOperation;
	private Converter converter = ConverterFactory.getInstance().getConverter();

	public WhileExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition, Operation<ExecutionContext> whileOperation, Executor...children) {
		super(parent, context, condition, children);
		this.whileOperation = whileOperation;
	}

	public void execute(ExecutionContext context) throws ExecutionException {
		try {
			Boolean result = converter.convert(whileOperation.evaluate(context), Boolean.class);
			while (result != null && result && !ScriptRuntime.getRuntime().isAborted()) {
				super.execute(context);
				if (context.getBreakCount() > 0) {
					context.incrementBreakCount(-1);
					break;
				}
				// execute again
				result = converter.convert(whileOperation.evaluate(context), Boolean.class);	
			}
		}
		catch (EvaluationException e) {
			throw new ExecutionException(e);
		}
	}
	
	public Operation<ExecutionContext> getWhile() {
		return whileOperation;
	}

	public void setWhile(Operation<ExecutionContext> whileOperation) {
		this.whileOperation = whileOperation;
	}
}
