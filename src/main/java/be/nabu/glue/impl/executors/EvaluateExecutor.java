package be.nabu.glue.impl.executors;

import java.text.ParseException;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.AssignmentExecutor;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.Operation;

public class EvaluateExecutor extends BaseExecutor implements AssignmentExecutor {

	private String variableName;
	private Operation<ExecutionContext> operation;
	private boolean overwriteIfExists;
	
	public EvaluateExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition, String variableName, Operation<ExecutionContext> operation, boolean overwriteIfExists) throws ParseException {
		super(parent, context, condition);
		this.variableName = variableName;
		this.operation = operation;
		this.overwriteIfExists = overwriteIfExists;
	}

	@Override
	public void execute(ExecutionContext context) throws ExecutionException {
		if (variableName == null || context.getPipeline().get(variableName) == null || overwriteIfExists) {
			try {
				Object value = operation.evaluate(context);
				if (variableName != null) {
					if (context.isDebug()) {
						ScriptRuntime.getRuntime().log("Result: " + variableName + " = " + value);
					}
					if (value instanceof String) {
						value = ScriptRuntime.getRuntime().getScript().getParser().substitute((String) value, context);
					}
					context.getPipeline().put(variableName, value);
				}
			}
			catch (EvaluationException e) {
				throw new ExecutionException(e);
			}
		}
		else if (context.isDebug() && variableName != null && context.getPipeline().get(variableName) != null && !overwriteIfExists) {
			ScriptRuntime.getRuntime().log("Inherited parameter: " + variableName + " = " + context.getPipeline().get(variableName));
		}
	}

	@Override
	public boolean isOverwriteIfExists() {
		return overwriteIfExists;
	}

	@Override
	public String getVariableName() {
		return variableName;
	}

	public Operation<ExecutionContext> getOperation() {
		return operation;
	}
	
	@Override
	public String toString() {
		return operation.toString();
	}
}
