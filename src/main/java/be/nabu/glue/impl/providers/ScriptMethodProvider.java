package be.nabu.glue.impl.providers;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.ScriptUtils;
import be.nabu.glue.api.EnclosedLambda;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.Lambda;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.MethodProvider;
import be.nabu.glue.api.OptionalTypeConverter;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.impl.LambdaImpl;
import be.nabu.glue.impl.LambdaMethodProvider.LambdaExecutionOperation;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.impl.SimpleParameterDescription;
import be.nabu.glue.impl.executors.EvaluateExecutor;
import be.nabu.glue.impl.methods.ScriptMethods;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
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
			else if (ALLOW_LAMBDAS && ("dispatch".equals(name) || "script.dispatch".equals(name))) {
				return new DispatchOperation();
			}
			else if ("throw".equals(name) || "script.throw".equals(name)) {
				return new ThrowOperation();
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
		// add lambda if necessary
		if (ALLOW_LAMBDAS) {
			descriptions.add(new SimpleMethodDescription("script", "lambda", null, 
				Arrays.asList(new ParameterDescription [] { new SimpleParameterDescription("method", "The method", "lambda") }),
				Arrays.asList(new ParameterDescription [] { new SimpleParameterDescription("lambda", "The method", "lambda") }),
				true
			));
		}
		return descriptions;
	}
	
	public static class ParameterizedEvaluationException extends EvaluationException {
		private static final long serialVersionUID = 1L;
		private Object parameters;

		public ParameterizedEvaluationException(Object parameters) {
			this.parameters = parameters;
		}

		public ParameterizedEvaluationException(String message, Throwable cause, Object parameters) {
			super(message, cause);
			this.parameters = parameters;
		}

		public ParameterizedEvaluationException(String message, Object parameters) {
			super(message);
			this.parameters = parameters;
		}

		public ParameterizedEvaluationException(Throwable cause, Object parameters) {
			super(cause);
			this.parameters = parameters;
		}

		public Object getParameters() {
			return parameters;
		}
	}
	
	public static class ThrowOperation extends BaseMethodOperation<ExecutionContext> {
		@Override
		public void finish() throws ParseException {
			// do nothing
		}
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			// throw(message, parameters, chainedexception)
			String message = null;
			Throwable cause = null;
			Object parameters = null;
			for (int i = 1; i < getParts().size(); i++) {
				Object evaluated = ((Operation) getParts().get(i).getContent()).evaluate(context);
				if (evaluated instanceof Throwable) {
					cause = (Throwable) evaluated;
				}
				else if (evaluated instanceof String) {
					message = (String) evaluated;
				}
				else if (parameters == null) {
					parameters = evaluated;
				}
			}
			throw new ParameterizedEvaluationException(message, cause, parameters);
		}
	}
	
	public static class DispatchOperation extends BaseMethodOperation<ExecutionContext> {

		@Override
		public void finish() throws ParseException {
			// do nothing
		}

		@SuppressWarnings("unchecked")
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			ScriptRuntime runtime = ScriptRuntime.getRuntime();
			String fullName = ScriptUtils.getFullName(runtime.getScript());
			Integer counter = (Integer) runtime.getContext().get(fullName + ".lambda.counter");
			if (counter == null) {
				counter = -1;
			}
			counter++;
			runtime.getContext().put(fullName + ".lambda.counter", counter);
			List<Lambda> lambdas = new ArrayList<Lambda>();
			for (int i = 1; i < getParts().size(); i++) {
				Operation<ExecutionContext> argumentOperation = (Operation<ExecutionContext>) getParts().get(i).getContent();
				Lambda lambda = (Lambda) argumentOperation.evaluate(context);
				if (lambda.getOperation() instanceof DispatchOperationInstance) {
					lambdas.addAll(((DispatchOperationInstance) lambda.getOperation()).getLambdas());
				}
				else {
					lambdas.add(lambda);
				}
			}
			return new LambdaImpl(new SimpleMethodDescription(runtime.getScript().getNamespace(), runtime.getScript().getName() + "$" + counter, "Lambda " + counter,
				Arrays.asList(new ParameterDescription [] { new SimpleParameterDescription("x", "The input parameters", "object", true) }),
				Arrays.asList(new ParameterDescription [] { new SimpleParameterDescription("result", "The output parameters", "object", true) })),
				new DispatchOperationInstance(lambdas, runtime.getScript().getRepository()),
				new HashMap<String, Object>()
			);
		}
		
	}
	
	public static class DispatchOperationInstance extends BaseMethodOperation<ExecutionContext> {
		private List<Lambda> lambdas;
		private ScriptRepository repository;
		public DispatchOperationInstance(List<Lambda> lambdas, ScriptRepository repository) {
			this.lambdas = lambdas;
			this.repository = repository;
		}
		@Override
		public void finish() throws ParseException {
			// do nothing
		}
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			List<Object> arguments = new ArrayList<Object>();
			Object object = context.getPipeline().get("x");
			if (object instanceof Collection) {
				arguments.addAll((Collection) object);
			}
			else if (object instanceof Iterable) {
				for (Object single : (Iterable) object) {
					arguments.add(single);
				}
			}
			else if (object instanceof Object[]) {
				arguments.addAll(Arrays.asList((Object[]) object));
			}
			Lambda closeMatch = null, exactMatch = null;
			List<Object> closeConverted = null, exactConverted = null;
			// find the first match within the given lambdas
			for (Lambda lambda : lambdas) {
				// the correct amount of parameters
				if (lambda.getDescription().getParameters().size() == arguments.size()) {
					List<Object> converted = new ArrayList<Object>();
					boolean matches = true;
					boolean isExactMatch = true;
					// check the types
					List<ParameterDescription> parameters = lambda.getDescription().getParameters();
					for (int i = 0; i < parameters.size(); i++) {
						ParameterDescription description = parameters.get(i);
						converted.add(arguments.get(i));
						// if there is no type or the type is object, anything goes
						if (description.getType() != null && !description.getType().equalsIgnoreCase("object")) {
							OptionalTypeConverter converter = EvaluateExecutor.getTypeProvider(repository, lambda instanceof EnclosedLambda ? ((EnclosedLambda) lambda).getEnclosedContext() : null).getConverter(description.getType());
							if (converter == null) {
								throw new RuntimeException("Unknown type defined by lambda for variable '" + description.getName() + "': " + description.getType());
							}
							if (arguments.get(i) != null) {
								try {
									Object convert = converter.convert(arguments.get(i));
									if (convert == null) {
										matches = false;
										break;
									}
									else {
										converted.set(i, convert);
										isExactMatch &= convert.equals(arguments.get(i));
									}
								}
								catch (Exception e) {
									matches = false;
									break;
								}
							}
						}
					}
					if (matches) {
						if (isExactMatch) {
							exactMatch = lambda;
							exactConverted = converted;
							break;
						}
						else if (closeMatch == null) {
							closeMatch = lambda;
							closeConverted = converted;
						}
					}
				}
			}
			if (exactMatch != null) {
				LambdaExecutionOperation operation = new LambdaExecutionOperation(exactMatch.getDescription(), exactMatch.getOperation(), exactMatch instanceof EnclosedLambda ? ((EnclosedLambda) exactMatch).getEnclosedContext() : new HashMap<String, Object>());
				return operation.evaluateWithParameters(context, exactConverted.toArray());
			}
			else if (closeMatch != null) {
				LambdaExecutionOperation operation = new LambdaExecutionOperation(closeMatch.getDescription(), closeMatch.getOperation(), closeMatch instanceof EnclosedLambda ? ((EnclosedLambda) closeMatch).getEnclosedContext() : new HashMap<String, Object>());
				return operation.evaluateWithParameters(context, closeConverted.toArray());
			}
			else {
				throw new RuntimeException("Can not find matching target for dispatching");
			}
		}
		public List<Lambda> getLambdas() {
			return lambdas;
		}
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
				if (argumentOperation.getType() == OperationType.CLASSIC && argumentOperation.getParts().size() == 3 && argumentOperation.getParts().get(1).getType() == Type.NAMING && argumentOperation.getParts().get(1).getContent().equals(":")) {
					String parameterName = argumentOperation.getParts().get(0).getContent().toString();
					// here we could either store the operation and re-evaluate it for every lambda instance
					// or we evaluate it once and store the result
					// because all the values you can use must already exist in the lambda scope, it can be done now
					// the only downside could be if the computation was heavy
					Object value = argumentOperation.getParts().get(2).getType() == QueryPart.Type.OPERATION 
						? ((Operation<ExecutionContext>) argumentOperation.getParts().get(2).getContent()).evaluate(context)
						: argumentOperation.getParts().get(2).getContent();
					inputParameters.add(new SimpleParameterDescription(parameterName.trim(), null, "object").setDefaultValue(value));
				}
				else if (argumentOperation.getParts().size() > 1) {
					throw new EvaluationException("The parameter " + i + " has too many parts");
				}
				else if (argumentOperation.getParts().isEmpty()) {
					throw new EvaluationException("No parameters for: " + i);
				}
				else {
					inputParameters.add(new SimpleParameterDescription(argumentOperation.getParts().get(0).getContent().toString().trim(), null, "object"));
				}
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
