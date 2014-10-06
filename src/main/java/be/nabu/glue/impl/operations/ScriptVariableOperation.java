package be.nabu.glue.impl.operations;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.impl.VariableOperation;

public class ScriptVariableOperation<T> extends VariableOperation<T> {

	@Override
	protected Object get(T context, String name) throws EvaluationException {
		if (context instanceof ExecutionContext) {
			return ((ExecutionContext) context).getPipeline().get(name);
		}
		else {
			return super.get(context, name);
		}
	}

}
