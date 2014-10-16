package be.nabu.glue.impl.operations;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.impl.VariableOperation;

public class ScriptVariableOperation<T> extends VariableOperation<T> {

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
