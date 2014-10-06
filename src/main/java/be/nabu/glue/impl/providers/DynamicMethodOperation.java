package be.nabu.glue.impl.providers;

import java.text.ParseException;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodProvider;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseOperation;

public class DynamicMethodOperation extends BaseOperation<ExecutionContext> {

	private Operation<ExecutionContext> operation;

	private MethodProvider [] methodProviders;
	
	public DynamicMethodOperation(MethodProvider...methodProviders) {
		this.methodProviders = methodProviders;
	}
	
	@Override
	public Object evaluate(ExecutionContext context) throws EvaluationException {
		if (operation != null) {
			Object value = operation.evaluate(context);
			// if the returning value is a string, replace all the current variables in it
			if (value instanceof String) {
				value = ScriptRuntime.getRuntime().getScript().getParser().substitute((String) value, context);
			}
			return value;
		}
		else {
			throw new IllegalStateException("No method or script found");
		}
	}

	@Override
	public void finish() throws ParseException {
		if (operation == null) {
			String fullName = (String) getParts().get(0).getContent();
			// first check if it is a delegator method
			operation = getOperation(fullName);
			if (operation == null) {
				throw new ParseException("Could not resolve operation: " + fullName, 0);
			}
			for (QueryPart part : getParts()) {
				operation.add(new QueryPart(part.getType(), part.getContent()));
			}
			operation.finish();
		}
	}

	protected Operation<ExecutionContext> getOperation(String fullName) {
		for (MethodProvider provider : methodProviders) {
			Operation<ExecutionContext> operation = provider.resolve(fullName);
			if (operation != null) {
				return operation;
			}
		}
		return null;
	}
	
	@Override
	public OperationType getType() {
		return OperationType.METHOD;
	}
}
