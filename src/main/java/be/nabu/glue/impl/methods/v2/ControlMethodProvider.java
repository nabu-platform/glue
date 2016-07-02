package be.nabu.glue.impl.methods.v2;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.MethodProvider;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.impl.SimpleParameterDescription;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.base.BaseMethodOperation;

public class ControlMethodProvider implements MethodProvider {

	@Override
	public Operation<ExecutionContext> resolve(String name) {
		if ("control.when".equals(name) || "when".equals(name)) {
			return new WhenOperation();
		}
		return null;
	}

	@Override
	public List<MethodDescription> getAvailableMethods() {
		List<MethodDescription> descriptions = new ArrayList<MethodDescription>();
		descriptions.add(new SimpleMethodDescription("control", "when", "This will conditionally execute the second or third argument depending on the result of the first argument: when(test, then, else)",
			Arrays.asList(new ParameterDescription [] { 
				new SimpleParameterDescription("test", "A test to see which one of the other arguments has to be executed", "boolean"),
				new SimpleParameterDescription("then", "If the test returns 'true', this part is executed", "object"),
				new SimpleParameterDescription("else", "Otherwise this part is executed", "object")
			}),
			new ArrayList<ParameterDescription>()));
		return descriptions;
	}
	
	private static class WhenOperation extends BaseMethodOperation<ExecutionContext> {

		@Override
		public void finish() throws ParseException {
			// do nothing
		}

		@SuppressWarnings("unchecked")
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			Boolean result = (Boolean) resolve((Operation<ExecutionContext>) getParts().get(1).getContent(), context);
			if (result != null && result) {
				return resolve((Operation<ExecutionContext>) getParts().get(2).getContent(), context);
			}
			else {
				return resolve((Operation<ExecutionContext>) getParts().get(3).getContent(), context);
			}
		}
		
		@SuppressWarnings({ "unchecked", "rawtypes" })
		private Object resolve(Object object, ExecutionContext context) throws EvaluationException {
			if (object instanceof Operation) {
				return ((Operation) object).evaluate(context);
			}
			else {
				return object;
			}
		}
	}

}
