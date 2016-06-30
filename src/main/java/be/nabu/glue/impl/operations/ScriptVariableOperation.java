package be.nabu.glue.impl.operations;

import java.util.ArrayList;
import java.util.Collection;

import be.nabu.glue.impl.GlueUtils;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.impl.VariableOperation;

public class ScriptVariableOperation<T> extends VariableOperation<T> {

	@Override
	public Object evaluate(T context) throws EvaluationException {
		Object value = super.evaluate(context);
		// arraylists are used to create custom result sets
		// convert these to arrays for integration purposes
		// tuples don't use arraylist
		if (value instanceof ArrayList && GlueUtils.getVersion().contains(1.0)) {
			value = ((Collection<?>) value).toArray();
		}
		return value;
	}

}
