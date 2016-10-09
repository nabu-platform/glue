package be.nabu.glue.core.impl;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import be.nabu.glue.OptionalTypeProviderFactory;
import be.nabu.glue.api.AssignmentExecutor;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.api.OptionalTypeConverter;
import be.nabu.glue.core.api.OptionalTypeProvider;
import be.nabu.glue.core.impl.executors.FunctionExecutor.FunctionOperation;
import be.nabu.glue.impl.ForkedExecutionContext;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.glue.utils.ScriptUtils;
import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.api.ListableContextAccessor;

public class StructureTypeProvider implements OptionalTypeProvider {

	public static final boolean INCLUDE_NON_SHARED_FIELDS = Boolean.parseBoolean(System.getProperty("glue.include_non_shared_fields", "true"));
	private ScriptRepository repository;
	private Map<String, Object> additionalContext;

	public StructureTypeProvider(ScriptRepository repository) {
		this.repository = repository;
	}
	
	public StructureTypeProvider(ScriptRepository repository, Map<String, Object> additionalContext) {
		this.repository = repository;
		this.additionalContext = additionalContext;
	}
	
	@Override
	public OptionalTypeConverter getConverter(String type) {
		try {
			Script script = repository.getScript(type);
			if (script != null) {
				return new StructureTypeConverter(ScriptUtils.getFullName(script), script.getRoot());
			}
			else {
				Object object = null;
				if (additionalContext != null) {
					object = additionalContext.get(type);
				}
				if (object == null || !(object instanceof Lambda)) {
					ScriptRuntime runtime = ScriptRuntime.getRuntime();
					if (runtime != null) {
						object = runtime.getExecutionContext().getPipeline().get(type);
					}
				}
				if (object instanceof Lambda && ((Lambda) object).getOperation() instanceof FunctionOperation) {
					return new StructureTypeConverter(type, (ExecutorGroup) ((FunctionOperation) ((Lambda) object).getOperation()).getExecutor());
				}
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		catch (ParseException e) {
			// ignore
		}
		return null;
	}

	public class StructureTypeConverter implements OptionalTypeConverter {

		private List<ParameterDescription> parameters;
		private Map<String, AssignmentExecutor> inputExecutors;
		
		private boolean allowDefaultValueInitialization = Boolean.parseBoolean(System.getProperty("structure.allow.defaultValues", "true"));
		private ExecutorGroup root;
		private String name;

		public StructureTypeConverter(String name, ExecutorGroup root) {
			this.name = name;
			this.root = root;
		}
		
		@Override
		public Object convert(Object object) {
			try {
				return cast(object, getParameters());
			}
			catch (EvaluationException e) {
				throw new ClassCastException("Could not cast to " + name + ": " + e.getMessage());
			}
			catch (ParseException e) {
				throw new ClassCastException("Could not cast to " + name + ": " + e.getMessage());
			}
			catch (IOException e) {
				throw new ClassCastException("Could not cast to " + name + ": " + e.getMessage());
			}
		}
		
		private List<ParameterDescription> getParameters() throws ParseException, IOException {
			if (parameters == null) {
				synchronized(this) {
					if (parameters == null) {
						parameters = ScriptUtils.getParameters(root, true, false);
					}
				}
			}
			return parameters;
		}
		
		private Map<String, AssignmentExecutor> getInputExecutors() {
			if (inputExecutors == null) {
				synchronized(this) {
					if (inputExecutors == null) {
						Map<String, AssignmentExecutor> inputExecutors = new HashMap<String, AssignmentExecutor>();
						scanInputExecutors(root, inputExecutors);
						this.inputExecutors = inputExecutors;
					}
				}
			}
			return inputExecutors;
		}
		
		private void scanInputExecutors(ExecutorGroup group, Map<String, AssignmentExecutor> inputExecutors) {
			for (Executor child : group.getChildren()) {
				if (child instanceof AssignmentExecutor) {
					AssignmentExecutor executor = (AssignmentExecutor) child;
					if (executor.getVariableName() != null) { // && !executor.isOverwriteIfExists()
						if (!inputExecutors.containsKey(executor.getVariableName())) {
							inputExecutors.put(executor.getVariableName(), executor);
						}
					}
				}
				if (child instanceof ExecutorGroup) {
					scanInputExecutors((ExecutorGroup) child, inputExecutors);
				}
			}
		}
		
		@SuppressWarnings({ "rawtypes", "unchecked" })
		private Map<String, Object> cast(Object original, List<ParameterDescription> parameters) throws EvaluationException {
			if (original == null) {
				return null;
			}
			ContextAccessor accessor = ContextAccessorFactory.getInstance().getAccessor(original.getClass());
			Map<String, Object> castValues = new LinkedHashMap<String, Object>();
			for (ParameterDescription parameter : parameters) {
				Object value;
				if (!accessor.has(original, parameter.getName())) {
					if (allowDefaultValueInitialization) {
						AssignmentExecutor assignmentExecutor = getInputExecutors().get(parameter.getName());
						if (assignmentExecutor == null) {
							throw new RuntimeException("Could not find assignment executor for: "  + parameter.getName());
						}
						try {
							ForkedExecutionContext context = new ForkedExecutionContext(ScriptRuntime.getRuntime().getExecutionContext(), true);
							context.getPipeline().putAll(castValues);
							assignmentExecutor.execute(context);
							value = context.getPipeline().get(parameter.getName());
							if (value == null) {
								throw new ClassCastException("The original '" + original + "' is not compatible with the required parameters");
							}
						}
						catch (ExecutionException e) {
							throw new EvaluationException(e);
						}
					}
					else {
						throw new ClassCastException("The original '" + original + "' is not compatible with the required parameters");
					}
				}
				else {
					value = accessor.get(original, parameter.getName());
				}
				if (parameter.getType() != null) {
					OptionalTypeConverter converter = OptionalTypeProviderFactory.getInstance().getProvider().getConverter(parameter.getType());
					if (converter == null) {
						converter = getConverter(parameter.getType());
					}
					if (converter == null) {
						throw new EvaluationException("Can not cast to " + parameter.getType());
					}
					castValues.put(parameter.getName(), converter.convert(value));
				}
				else {
					castValues.put(parameter.getName(), value);
				}
			}
			if (INCLUDE_NON_SHARED_FIELDS && accessor instanceof ListableContextAccessor) {
				Collection list = ((ListableContextAccessor) accessor).list(original);
				for (String key : (Collection<String>) list) {
					if (!castValues.containsKey(key)) {
						castValues.put(key, accessor.get(original, key));
					}
				}
			}
			return castValues;
		}

		@Override
		public Class<?> getComponentType() {
			return Map.class;
		}
	}
	
}
