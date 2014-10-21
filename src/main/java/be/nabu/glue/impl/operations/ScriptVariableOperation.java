package be.nabu.glue.impl.operations;

import java.util.ArrayList;
import java.util.Collection;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.impl.VariableOperation;

public class ScriptVariableOperation<T> extends VariableOperation<T> {

	@Override
	public Object evaluate(T context) throws EvaluationException {
		Object value = super.evaluate(context);
		// arraylists are used to create custom result sets
		// convert these to arrays for integration purposes
		// tuples don't use arraylist
		if (value instanceof ArrayList) {
			value = ((Collection<?>) value).toArray();
		}
		return value;
	}

	@Override
	protected Object get(T context, String name) throws EvaluationException {
		if (context instanceof ExecutionContext) {
			Object object = ((ExecutionContext) context).getPipeline().get(name);
			if (object == null) {
				object = ScriptRuntime.getRuntime().getExecutionContext().getExecutionEnvironment().getParameters().get(name);
			}
			return object;
		}
		else if (ScriptRuntime.getRuntime().getExecutionContext().getPipeline().containsKey(name)) {
			return ScriptRuntime.getRuntime().getExecutionContext().getPipeline().get(name);
		}
		else {
			return super.get(context, name);
		}
	}

}
