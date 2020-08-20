package be.nabu.glue.core.impl;

import java.util.ArrayList;
import java.util.Collection;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.ListableContextAccessor;
import be.nabu.libs.evaluator.api.WritableContextAccessor;

public class ExecutionContextAccessor implements ListableContextAccessor<ExecutionContext>, WritableContextAccessor<ExecutionContext> {

	@Override
	public Class<ExecutionContext> getContextType() {
		return ExecutionContext.class;
	}

	@Override
	public boolean has(ExecutionContext context, String name) throws EvaluationException {
		return context.getPipeline().containsKey(name);
	}

	@Override
	public Object get(ExecutionContext context, String name) throws EvaluationException {
		return context.getPipeline().get(name);
	}

	@Override
	public Collection<String> list(ExecutionContext context) {
		return new ArrayList<String>(context.getPipeline().keySet());
	}

	@Override
	public void set(ExecutionContext context, String name, Object value) throws EvaluationException {
		context.getPipeline().put(name, value);
	}

}
