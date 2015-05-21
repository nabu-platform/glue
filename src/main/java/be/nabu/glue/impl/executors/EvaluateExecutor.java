package be.nabu.glue.impl.executors;

import java.io.Closeable;
import java.text.ParseException;
import java.util.Date;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.AssignmentExecutor;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.impl.TransactionalCloseable;
import be.nabu.glue.impl.methods.ScriptMethods;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.api.Operation;

public class EvaluateExecutor extends BaseExecutor implements AssignmentExecutor {

	private static final int BLOB_LENGTH = 500;
	
	private String variableName;
	private Operation<ExecutionContext> operation;
	private boolean overwriteIfExists;
	private boolean autocastIfOptional = false;
	private boolean generated = false;
	private String optionalType;
	private Class<?> targetType;
	
	public EvaluateExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition, String variableName, String optionalType, Operation<ExecutionContext> operation, boolean overwriteIfExists) throws ParseException {
		super(parent, context, condition);
		this.variableName = variableName;
		this.optionalType = optionalType;
		this.operation = operation;
		this.overwriteIfExists = overwriteIfExists;
		if (optionalType != null) {
			if (optionalType.equalsIgnoreCase("integer")) {
				targetType = Long.class;
			}
			else if (optionalType.equalsIgnoreCase("decimal")) {
				targetType = Double.class;
			}
			else if (optionalType.equalsIgnoreCase("date")) {
				targetType = Date.class;
			}
			else if (optionalType.equalsIgnoreCase("string")) {
				targetType = String.class;
			}
			else if (optionalType.equalsIgnoreCase("boolean")) {
				targetType = Boolean.class;
			}
			else {
				throw new ParseException("Unknown type: " + optionalType, 0);
			}
		}
	}

	@Override
	public void execute(ExecutionContext context) throws ExecutionException {
		if (variableName == null || context.getPipeline().get(variableName) == null || overwriteIfExists || (variableName != null && autocastIfOptional && !overwriteIfExists && context.getPipeline().get(variableName) != null)) {
			try {
				Object value = operation.evaluate(context);
				if (value instanceof Closeable) {
					ScriptRuntime.getRuntime().addTransactionable(new TransactionalCloseable((Closeable) value));
				}
				if (variableName != null) {
					// in this specific scenario we assume it is an optional assign and a value was passed in
					// we will attempt to cast the existing value to the type of the optionally assigned value as this is closest to what the script wanted
					if (autocastIfOptional && !overwriteIfExists && context.getPipeline().get(variableName) != null) {
						if (value != null) {
							Object current = context.getPipeline().get(variableName);
							if (current != null) {
								context.getPipeline().put(variableName, ConverterFactory.getInstance().getConverter().convert(current, value.getClass()));
							}
						}
					}
					else {
						if (context.isDebug()) {
							// trim values that are too long
							String stringValue = value == null ? "" : value.toString();
							if (stringValue.length() > BLOB_LENGTH) {
								stringValue = "BLOB: " + stringValue.substring(0, BLOB_LENGTH).replaceAll("[\r\n]+", " ") + "...";
							}
							ScriptMethods.debug("Result: " + variableName + " = " + stringValue);
						}
						context.getPipeline().put(variableName, value);
					}
					if (targetType != null && context.getPipeline().containsKey(variableName)) {
						context.getPipeline().put(variableName, ConverterFactory.getInstance().getConverter().convert(context.getPipeline().get(variableName), targetType));
					}
				}
			}
			catch (Exception e) {
				throw new ExecutionException(e);
			}
		}
		else if (context.isDebug() && variableName != null && context.getPipeline().get(variableName) != null && !overwriteIfExists) {
			ScriptRuntime.getRuntime().getFormatter().print("Inherited parameter: " + variableName + " = " + context.getPipeline().get(variableName));
		}
	}

	@Override
	public boolean isOverwriteIfExists() {
		return overwriteIfExists;
	}
	
	public void setOverwriteIfExists(boolean overwriteIfExists) {
		this.overwriteIfExists = overwriteIfExists;
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

	public boolean isAutocastIfOptional() {
		return autocastIfOptional;
	}

	public void setAutocastIfOptional(boolean autocastIfOptional) {
		this.autocastIfOptional = autocastIfOptional;
	}

	@Override
	public boolean isGenerated() {
		return generated;
	}
	public void setGenerated(boolean generated) {
		this.generated = generated;
	}

	@Override
	public String getOptionalType() {
		return optionalType;
	}

	public void setOptionalType(String optionalType) {
		this.optionalType = optionalType;
	}
}
