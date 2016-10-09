package be.nabu.glue.core.impl.executors;

import java.text.ParseException;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.Operation;

public class WhileExecutor extends SequenceExecutor {

	private Operation<ExecutionContext> whileOperation, rewritten;
	private Converter converter = ConverterFactory.getInstance().getConverter();

	public WhileExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition, Operation<ExecutionContext> whileOperation, Executor...children) {
		super(parent, context, condition, children);
		this.whileOperation = whileOperation;
	}

	private Operation<ExecutionContext> getRewrittenWhile() throws ExecutionException {
		// can only rewrite operations if we have a glue operation provider that can give us static method descriptions
		if (rewritten == null && whileOperation != null) {
			synchronized(this) {
				if (rewritten == null) {
					try {
						rewritten = rewrite(whileOperation);
					}
					catch (ParseException e) {
						throw new ExecutionException(e);
					}
				}
			}
		}
		return rewritten;
	}
	
	public void execute(ExecutionContext context) throws ExecutionException {
		try {
			Boolean result = converter.convert(getRewrittenWhile().evaluate(context), Boolean.class);
			while (result != null && result && !ScriptRuntime.getRuntime().isAborted()) {
				super.execute(context);
				if (context.getBreakCount() > 0) {
					context.incrementBreakCount(-1);
					break;
				}
				// execute again
				result = converter.convert(getRewrittenWhile().evaluate(context), Boolean.class);	
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
