package be.nabu.glue.impl.providers;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
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
import be.nabu.glue.impl.LambdaImpl;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.impl.SimpleParameterDescription;
import be.nabu.glue.impl.methods.ScriptMethods;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.base.BaseMethodOperation;

public class ScriptMethodProvider implements MethodProvider {

	private ScriptRepository repository;
	public static boolean ALLOW_VARARGS = Boolean.parseBoolean(System.getProperty("script.varargs", "true"));
	public static boolean ALLOW_LAMBDAS = Boolean.parseBoolean(System.getProperty("script.lambdas", "true"));

	public ScriptMethodProvider(ScriptRepository repository) {
		this.repository = repository;
	}
	
	@Override
	public Operation<ExecutionContext> resolve(String name) {
		try {
			if (ALLOW_LAMBDAS && ("lambda".equals(name) || "script.lambda".equals(name))) {
				return new LambdaOperation();
			}
			else if (repository != null && repository.getScript(name) != null) {
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
				descriptions.add(new SimpleMethodDescription(script.getNamespace(), script.getName(), script.getRoot().getContext().getComment(), ScriptUtils.getInputs(script), ScriptUtils.getOutputs(script)));
			}
			catch (IOException e) {
				// ignore
			}
			catch (ParseException e) {
				// ignore
			}
			catch (RuntimeException e) {
				// ignore
			}
		}
		return descriptions;
	}
	
	/**
	 * This is the lambda() method allowing you to create lambdas
	 */
	public static class LambdaOperation extends BaseMethodOperation<ExecutionContext> {
		@SuppressWarnings("unchecked")
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			if (getParts().size() < 2) {
				throw new EvaluationException("Not enough parameters for the lambda");
			}
			List<ParameterDescription> inputParameters = new ArrayList<ParameterDescription>();
			// leave the last part, it is the actual lambda operation
			for (int i = 1; i < getParts().size() - 1; i++) {
				Operation<ExecutionContext> argumentOperation = (Operation<ExecutionContext>) getParts().get(i).getContent();
				if (argumentOperation.getParts().size() > 1) {
					throw new EvaluationException("The parameter " + i + " has too many parts");
				}
				else if (argumentOperation.getParts().isEmpty()) {
					throw new EvaluationException("No parameters for: " + i);
				}
				inputParameters.add(new SimpleParameterDescription(argumentOperation.getParts().get(0).getContent().toString().trim(), null, "object"));
			}
			ScriptRuntime runtime = ScriptRuntime.getRuntime();
			String fullName = ScriptUtils.getFullName(runtime.getScript());
			Integer counter = (Integer) runtime.getContext().get(fullName + ".lambda.counter");
			if (counter == null) {
				counter = -1;
			}
			counter++;
			runtime.getContext().put(fullName + ".lambda.counter", counter);
			Operation<ExecutionContext> lambdaOperation = (Operation<ExecutionContext>) getParts().get(getParts().size() - 1).getContent();
			HashMap<String, Object> enclosedContext = new HashMap<String, Object>(context.getPipeline());
			return new LambdaImpl(new SimpleMethodDescription(runtime.getScript().getNamespace(), runtime.getScript().getName() + "$" + counter, "Lambda " + counter, inputParameters, 
					Arrays.asList(new ParameterDescription [] { new SimpleParameterDescription("return", null, "object") })), 
					lambdaOperation, enclosedContext);
		}

		@Override
		public void finish() throws ParseException {
			// do nothing
		}
	}

	public static class ScriptOperation extends BaseMethodOperation<ExecutionContext> {

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
					Operation<ExecutionContext> argumentOperation = (Operation<ExecutionContext>) getParts().get(i).getContent();
					if (i > keys.size()) { 
						if (!ALLOW_VARARGS || keys.isEmpty() || !keys.get(keys.size() - 1).isVarargs()) {
							throw new EvaluationException("Too many parameters, expecting: " + keys.size());
						}
						else {
							input.put(keys.get(keys.size() - 1).getName(), ScriptMethods.array(input.get(keys.get(keys.size() - 1).getName()), argumentOperation.evaluate(context)));
						}
					}
					else {
						input.put(keys.get(i - 1).getName(), argumentOperation.evaluate(context));
					}
				}
				ScriptRuntime runtime = new ScriptRuntime(script, context.getExecutionEnvironment(), context.isDebug(), input);
				runtime.setTrace(context.isTrace());
				if (context.isTrace()) {
					runtime.addBreakpoint(context.getBreakpoints().toArray(new String[0]));
				}
				runtime.run();
				// could have turned off trace mode in runtime
				context.setTrace(runtime.isTrace());
				if (context.isTrace()) {
					// copy back the last breakpoint set, you could have toggled breakpoints
					context.removeBreakpoints();
					context.addBreakpoint(runtime.getExecutionContext().getBreakpoints().toArray(new String[0]));
				}
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
	}
}
