package be.nabu.glue.impl;

import java.util.ArrayList;
import java.util.Collection;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.ListableContextAccessor;

public class ExecutionContextAccessor implements ListableContextAccessor<ExecutionContext> {

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

}
