package be.nabu.glue.core.impl.providers;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.api.ScriptRepositoryWithDescriptions;
import be.nabu.glue.core.api.EnclosedLambda;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.api.MethodProvider;
import be.nabu.glue.core.api.OptionalTypeConverter;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.LambdaImpl;
import be.nabu.glue.core.impl.LambdaMethodProvider.LambdaExecutionOperation;
import be.nabu.glue.core.impl.executors.EvaluateExecutor;
import be.nabu.glue.core.impl.methods.ScriptMethods;
import be.nabu.glue.impl.SimpleExecutionContext;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.impl.SimpleParameterDescription;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.glue.utils.ScriptUtils;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseMethodOperation;
import be.nabu.libs.evaluator.impl.VariableOperation;

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
			else if (ALLOW_LAMBDAS && ("method".equals(name) || "script.method".equals(name))) {
				return new LambdaOperation(true);
			}
			else if (ALLOW_LAMBDAS && ("dispatch".equals(name) || "script.dispatch".equals(name))) {
				return new DispatchOperation();
			}
			else if ("throw".equals(name) || "script.throw".equals(name)) {
				return new ThrowOperation();
			}
			else if ("curry".equals(name) || "script.curry".equals(name)) {
				return new CurryOperation();
			}
			else if ("import".equals(name) || "script.import".equals(name)) {
				return new ImportOperation();
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
		List<MethodDescription> descriptions;
		if (repository instanceof ScriptRepositoryWithDescriptions) {
			descriptions = new ArrayList<MethodDescription>(((ScriptRepositoryWithDescriptions) repository).getDescriptions());
		}
		else {
			descriptions = ScriptUtils.buildDescriptionsFor(repository);
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
		private boolean useActualPipeline;

		public LambdaOperation() {
			this(false);
		}
		public LambdaOperation(boolean useActualPipeline) {
			this.useActualPipeline = useActualPipeline;
		}
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
					throw new EvaluationException("The parameter " + i + " has too many parts: " + argumentOperation.getParts());
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
			Map<String, Object> enclosedContext = useActualPipeline ? context.getPipeline() : new HashMap<String, Object>(context.getPipeline());
			return new LambdaImpl(new SimpleMethodDescription(runtime.getScript().getNamespace(), runtime.getScript().getName() + "$" + counter, "Lambda " + counter, inputParameters, 
					Arrays.asList(new ParameterDescription [] { new SimpleParameterDescription("return", null, "object") })), 
					lambdaOperation, enclosedContext, useActualPipeline);
		}

		@Override
		public void finish() throws ParseException {
			// do nothing
		}
	}

	public static class ScriptOperation extends BaseMethodOperation<ExecutionContext> {

		private Script script;
		private Map<String, Object> enclosedContext;

		public ScriptOperation(Script script) {
			this.script = script;
		}
		
		public ScriptOperation(Script script, Map<String, Object> enclosedContext) {
			this.script = script;
			this.enclosedContext = enclosedContext;
		}
		
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			Map<String, Object> input = new HashMap<String, Object>();
			if (enclosedContext != null) {
				input.putAll(enclosedContext);
			}
			try {
				List<ParameterDescription> keys = ScriptUtils.getInputs(script);
				boolean wasOriginalList = false;
				for (int i = 1; i < getParts().size(); i++) {
					if (keys.size() == 0) {
						throw new EvaluationException("The script '" + ScriptUtils.getFullName(script) + "' does not allow for any input parameters, received: " + (getParts().size() - 1) + " parameters");
					}
					Operation<ExecutionContext> argumentOperation = (Operation<ExecutionContext>) getParts().get(i).getContent();
					if (GlueUtils.getVersion().contains(1.0) && i > keys.size()) { 
						if (!ALLOW_VARARGS || keys.isEmpty() || !keys.get(keys.size() - 1).isVarargs()) {
							throw new EvaluationException("Too many parameters, expecting: " + keys.size());
						}
						else {
							input.put(keys.get(keys.size() - 1).getName(), ScriptMethods.array(input.get(keys.get(keys.size() - 1).getName()), argumentOperation.evaluate(context)));
						}
					}
					// if you have indicated a list, we always want a list as last argument, not potentially a single element
					else if (!GlueUtils.getVersion().contains(1.0) && i >= keys.size() && getParts().size() > keys.size() + 1) {
						Object object = input.get(keys.get(keys.size() - 1).getName());
						Object evaluated = argumentOperation.evaluate(context);
						// if this is the exact count of the requested parameters and it is already a list, set it as parameter, you may want to pass it along as such
						if (evaluated instanceof Iterable && i == keys.size()) {
							wasOriginalList = true;
							object = evaluated;
						}
						else {
							// we don't have a list yet, create it
							if (object == null) {
								object = new ArrayList();
							}
							// the object exists, we are one past the last requested parameter and the previous was an original list, we want a list of lists
							else if (i == keys.size() + 1 && wasOriginalList) {
								object = new ArrayList(Arrays.asList(object));
							}
							((List) object).add(evaluated);
						}
						input.put(keys.get(keys.size() - 1).getName(), object);
					}
					else {
						input.put(keys.get(i - 1).getName(), argumentOperation.evaluate(context));
					}
				}
				SimpleExecutionContext newContext = new SimpleExecutionContext(context.getExecutionEnvironment(), context.getLabelEvaluator(), context.isDebug());
				newContext.setPrincipal(context.getPrincipal());
				newContext.setOutputCurrentLine(false);
				newContext.setTrace(context.isTrace());
//				ScriptRuntime runtime = new ScriptRuntime(script, context.getExecutionEnvironment(), context.isDebug(), input);
				ScriptRuntime runtime = new ScriptRuntime(script, newContext, input);
				runtime.setTrace(context.isTrace());
				if (context.isTrace()) {
					runtime.addBreakpoint(context.getBreakpoints().toArray(new String[0]));
				}
				VariableOperation.registerRoot();
				try {
					runtime.run();
				}
				finally {
					VariableOperation.unregisterRoot();
				}
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
		
		public Script getScript() {
			return script;
		}

		public Map<String, Object> getEnclosedContext() {
			return enclosedContext;
		}

		@Override
		public void finish() throws ParseException {
			// do nothing
		}
	}
	
	public static class ImportOperation extends BaseMethodOperation<ExecutionContext> {
		@Override
		public void finish() throws ParseException {
			// do nothing
		}
		@SuppressWarnings("unchecked")
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			for (int i = 1; i < getParts().size(); i++) {
				Object content = getParts().get(i).getContent();
				if (content instanceof Operation) {
					content = ((Operation<ExecutionContext>) content).evaluate(context);
				}
				if (content != null) {
					ScriptRuntime.getRuntime().getImports().add(content.toString());
				}
			}
			return null;
		}
	}
	
	public static class CurryOperation extends BaseMethodOperation<ExecutionContext> {

		@Override
		public void finish() throws ParseException {
			// do nothing
		}

		@SuppressWarnings("unchecked")
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			if (getParts().size() != 2) {
				throw new EvaluationException("Not the expected amount of parameters");
			}
			ScriptRuntime runtime = ScriptRuntime.getRuntime();
			String fullName = ScriptUtils.getFullName(runtime.getScript());
			Integer counter = (Integer) runtime.getContext().get(fullName + ".lambda.counter");
			if (counter == null) {
				counter = -1;
			}
			counter++;
			runtime.getContext().put(fullName + ".lambda.counter", counter);
			Operation<ExecutionContext> argumentOperation = (Operation<ExecutionContext>) getParts().get(1).getContent();
			Lambda lambda = (Lambda) argumentOperation.evaluate(context);
			// IMPORTANT: we make sure named parameters are not rewritten by the top level but instead passed along
			// this means we won't get "null" values for missing parameters which means we can distinguish between truely missing and actually set to null
			return new LambdaImpl(new SimpleMethodDescription(lambda.getDescription().getNamespace(), lambda.getDescription().getName(), lambda.getDescription().getDescription(), lambda.getDescription().getParameters(), lambda.getDescription().getReturnValues(), true),
				new CurryOperationInstance(lambda, new HashMap<String, Object>(), lambda.getDescription().getParameters()),
				new HashMap<String, Object>()
			);
		}
	}
	
	public static class CurryOperationInstance extends BaseMethodOperation<ExecutionContext> {

		private Lambda lambda;
		private Map<String, Object> captured;
		private List<ParameterDescription> currentParameters;

		public CurryOperationInstance(Lambda lambda, Map<String, Object> captured, List<ParameterDescription> currentParameters) {
			this.lambda = lambda;
			this.currentParameters = currentParameters;
			this.captured = new LinkedHashMap<String, Object>(captured);
			if (lambda.getDescription().getParameters().size() <= captured.size()) {
				throw new RuntimeException("Can not curry a function that is already fully evaluated");
			}
		}

		@Override
		public void finish() throws ParseException {
			// do nothing
		}

		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			Map<String, Object> captured = new HashMap<String, Object>(this.captured);
			for (ParameterDescription description : currentParameters) {
				if (context.getPipeline().containsKey(description.getName())) {
					captured.put(description.getName(), context.getPipeline().get(description.getName()));
				}
			}
			// construct a new lambda based on the old lambda but with the input parameters injected into the context
			if (captured.size() == lambda.getDescription().getParameters().size()) {
				Map<String, Object> pipeline = null;
				if (lambda instanceof EnclosedLambda) {
					if (((EnclosedLambda) lambda).isMutable()) {
						pipeline = ((EnclosedLambda) lambda).getEnclosedContext();
					}
					else {
						pipeline = new HashMap<String, Object>();
						pipeline.putAll(((EnclosedLambda) lambda).getEnclosedContext());
					}
				}
				pipeline.putAll(captured);
				LambdaExecutionOperation operation = new LambdaExecutionOperation(new SimpleMethodDescription(lambda.getDescription().getNamespace(), lambda.getDescription().getName(), lambda.getDescription().getDescription(), new ArrayList<ParameterDescription>(), lambda.getDescription().getReturnValues()), lambda.getOperation(), pipeline);
				return operation.evaluate(context);
			}
			// construct a new curry lambda
			else {
				List<ParameterDescription> parameters = new ArrayList<ParameterDescription>();
				for (ParameterDescription parameter : lambda.getDescription().getParameters()) {
					if (!captured.containsKey(parameter.getName())) {
						parameters.add(parameter);
					}
				}
				return new LambdaImpl(new SimpleMethodDescription(lambda.getDescription().getNamespace(), lambda.getDescription().getName(), lambda.getDescription().getDescription(), parameters, lambda.getDescription().getReturnValues(), true), 
					new CurryOperationInstance(lambda, captured, parameters), new HashMap<String, Object>());
			}
		}
		
	}
	
	public static class DecoratorOperation extends BaseMethodOperation<ExecutionContext> {
		private Lambda decorated;
		private Lambda decorator;
		public DecoratorOperation(Lambda decorated, Lambda decorator) {
			this.decorated = decorated;
			this.decorator = decorator;
		}
		@Override
		public void finish() throws ParseException {
			// do nothing
		}
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			List<Object> arguments = new ArrayList<Object>();
			for (ParameterDescription parameter : decorated.getDescription().getParameters()) {
				arguments.add(context.getPipeline().get(parameter.getName()));
			}
			LambdaExecutionOperation lambdaExecutionOperation = new LambdaExecutionOperation(decorator.getDescription(), decorator.getOperation(), decorator instanceof EnclosedLambda ? ((EnclosedLambda) decorator).getEnclosedContext() : new HashMap<String, Object>());
			return lambdaExecutionOperation.evaluateWithParameters(context, decorated, arguments);
		}
		
	}
}
