package be.nabu.glue.impl.providers;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.ScriptUtils;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.MethodProvider;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseOperation;

public class ScriptMethodProvider implements MethodProvider {

	private ScriptRepository repository;

	public ScriptMethodProvider(ScriptRepository repository) {
		this.repository = repository;
	}
	
	@Override
	public Operation<ExecutionContext> resolve(String name) {
		try {
			if (repository != null && repository.getScript(name) != null) {
				return new ScriptOperation(repository.getScript(name));
			}
			return null;
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<MethodDescription> getAvailableMethods() {
		List<MethodDescription> descriptions = new ArrayList<MethodDescription>();
		for (Script script : repository) {
			try {
				descriptions.add(new SimpleMethodDescription(script.getName(), script.getRoot().getContext().getComment(), ScriptUtils.getInputs(script).toArray(new ParameterDescription[0])));
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
			catch (ParseException e) {
				throw new RuntimeException(e);
			}
		}
		return descriptions;
	}
	
	public static class ScriptOperation extends BaseOperation<ExecutionContext> {

		private Script script;

		public ScriptOperation(Script script) {
			this.script = script;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			Map<String, Object> input = new HashMap<String, Object>();
			try {
				List<ParameterDescription> keys = ScriptUtils.getInputs(script);
				for (int i = 1; i < getParts().size(); i++) {
					if (i > keys.size()) {
						throw new EvaluationException("Too many parameters, expecting: " + keys);
					}
					Operation<ExecutionContext> argumentOperation = (Operation<ExecutionContext>) getParts().get(i).getContent();
					input.put(keys.get(i - 1).getName(), argumentOperation.evaluate(context));
				}
				ScriptRuntime runtime = new ScriptRuntime(script, context.getExecutionEnvironment(), false, input);
				runtime.run();
				return runtime.getExecutionContext();
			}
			catch (IOException e) {
				throw new EvaluationException(e);
			}
			catch (ParseException e) {
				throw new EvaluationException(e);
			}
		}

		@Override
		public void finish() throws ParseException {
			// do nothing
		}
		
		@Override
		public OperationType getType() {
			return OperationType.METHOD;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			// first the method name
			builder.append((String) getParts().get(0).getContent());
			// then the rest
			builder.append("(");
			for (int i = 1; i < getParts().size(); i++) {
				QueryPart part = getParts().get(i);
				if (i > 1) {
					builder.append(", ");
				}
				if (part.getType() == Type.STRING) {
					builder.append("\"" + part.getContent().toString() + "\"");
				}
				else {
					builder.append(part.getContent().toString());
				}
			}
			builder.append(")");
			return builder.toString();
		}
	}
}
