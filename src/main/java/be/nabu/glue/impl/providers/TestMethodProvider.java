package be.nabu.glue.impl.providers;

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
import be.nabu.glue.impl.methods.TestMethods;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.base.BaseMethodOperation;

/**
 * This operates in the same namespace as TestMethods
 */
public class TestMethodProvider implements MethodProvider {

	@Override
	public Operation<ExecutionContext> resolve(String name) {
		if (name.equals("validateFails") || name.equals("test.validateFails")) {
			return new TestExceptionOperation(true, false);
		}
		else if (name.equals("confirmFails") || name.equals("test.confirmFails")) {
			return new TestExceptionOperation(true, true);
		}
		else if (name.equals("validateNotFails") || name.equals("test.validateNotFails")) {
			return new TestExceptionOperation(false, false);
		}
		else if (name.equals("confirmNotFails") || name.equals("test.confirmNotFails")) {
			return new TestExceptionOperation(false, true);
		}
		return null;
	}

	@Override
	public List<MethodDescription> getAvailableMethods() {
		List<MethodDescription> descriptions = new ArrayList<MethodDescription>();
		descriptions.add(new SimpleMethodDescription("test", "validateFails", "This method will check that the arguments fail",
			Arrays.asList(new ParameterDescription [] { 
				new SimpleParameterDescription("message", "The message for this test", "String"),
				new SimpleParameterDescription("toExecute", "Any amount of executable statements", "Method")
			}),
			new ArrayList<ParameterDescription>()));
		descriptions.add(new SimpleMethodDescription("test", "validateNotFails", "This method will check that the arguments do not fail",
			Arrays.asList(new ParameterDescription [] { 
					new SimpleParameterDescription("message", "The message for this test", "String"),
					new SimpleParameterDescription("toExecute", "Any amount of executable statements", "Method")
			}),
			new ArrayList<ParameterDescription>()));
		descriptions.add(new SimpleMethodDescription("test", "confirmFails", "This method will check that the arguments fail",
			Arrays.asList(new ParameterDescription [] { 
				new SimpleParameterDescription("message", "The message for this test", "String"),
				new SimpleParameterDescription("toExecute", "Any amount of executable statements", "Method")
			}),
			new ArrayList<ParameterDescription>()));
		descriptions.add(new SimpleMethodDescription("test", "confirmNotFails", "This method will check that the arguments do not fail",
			Arrays.asList(new ParameterDescription [] { 
					new SimpleParameterDescription("message", "The message for this test", "String"),
					new SimpleParameterDescription("toExecute", "Any amount of executable statements", "Method")
			}),
				new ArrayList<ParameterDescription>()));
		return descriptions;
	}
	
	public static class TestExceptionOperation extends BaseMethodOperation<ExecutionContext> {

		private boolean expectingException;
		private Converter converter;
		private boolean fail;
		
		public TestExceptionOperation(boolean expectingException, boolean fail) {
			this.expectingException = expectingException;
			this.fail = fail;
			this.converter = ConverterFactory.getInstance().getConverter();
		}
		
		@Override
		public void finish() throws ParseException {
			// do nothing
		}

		@SuppressWarnings("unchecked")
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			Exception exception = null;
			String message = null;
			// execute all the arguments
			for (int i = 1; i < getParts().size(); i++) {
				Operation<ExecutionContext> argumentOperation = (Operation<ExecutionContext>) getParts().get(i).getContent();
				if (i == 1) {
					message = converter.convert(argumentOperation.evaluate(context), String.class);
				}
				else {
					try {
						argumentOperation.evaluate(context);
					}
					catch (Exception e) {
						exception = e;
						break;
					}
				}
			}
			if (exception == null && expectingException) {
				return TestMethods.check(message, false, "no exception thrown", fail);
			}
			else if (exception != null && !expectingException) {
				return TestMethods.check(message, false, "an exception was thrown", fail);
			}
			else {
				return TestMethods.check(message, true, exception == null ? "no exception thrown" : "an exception was thrown", fail);
			}
		}
	}

}
