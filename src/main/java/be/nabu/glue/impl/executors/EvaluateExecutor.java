package be.nabu.glue.impl.executors;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.AssignmentExecutor;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.impl.methods.ScriptMethods;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.Operation;

public class EvaluateExecutor extends BaseExecutor implements AssignmentExecutor {

	private static final int BLOB_LENGTH = 500;
	
	private String variableName;
	private Operation<ExecutionContext> operation;
	private boolean overwriteIfExists;
	
	public EvaluateExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition, String variableName, Operation<ExecutionContext> operation, boolean overwriteIfExists) {
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
						// trim values that are too long
						String stringValue = value == null ? "" : value.toString();
						if (stringValue.length() > BLOB_LENGTH) {
							stringValue = "BLOB: " + stringValue.substring(0, BLOB_LENGTH).replaceAll("[\r\n]+", " ") + "...";
						}
						ScriptMethods.debug("Result: " + variableName + " = " + stringValue);
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

	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}

	public void setOperation(Operation<ExecutionContext> operation) {
		this.operation = operation;
	}
	
}
