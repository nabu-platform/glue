package be.nabu.glue.impl.executors;

import java.io.Closeable;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import be.nabu.glue.OptionalTypeProviderFactory;
import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.AssignmentExecutor;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.MethodProvider;
import be.nabu.glue.api.OptionalTypeProvider;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.api.OptionalTypeConverter;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.impl.MultipleOptionalTypeProvider;
import be.nabu.glue.impl.StructureTypeProvider;
import be.nabu.glue.impl.TransactionalCloseable;
import be.nabu.glue.impl.methods.ScriptMethods;
import be.nabu.glue.impl.operations.GlueOperationProvider;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.PathAnalyzer;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;

public class EvaluateExecutor extends BaseExecutor implements AssignmentExecutor {

	private static final int BLOB_LENGTH = 500;
	private static final boolean ALLOW_STRUCTURE_TYPES = Boolean.parseBoolean(System.getProperty("structure.allow.types", "true"));
	
	private String variableName;
	private Operation<ExecutionContext> operation, rewrittenOperation;
	private boolean overwriteIfExists;
	private boolean autocastIfOptional = false;
	private boolean generated = false;
	private boolean allowNamedParameters = Boolean.parseBoolean(System.getProperty("named.parameters", "true"));
	private String optionalType;
	private boolean isList = false;
	private OperationProvider<ExecutionContext> operationProvider;

	private PathAnalyzer<ExecutionContext> pathAnalyzer;
	private OptionalTypeProvider optionalTypeProvider;

	private OptionalTypeConverter converter;
	
	public EvaluateExecutor(ExecutorGroup parent, ExecutorContext context, ScriptRepository repository, OperationProvider<ExecutionContext> operationProvider, Operation<ExecutionContext> condition, String variableName, String optionalType, Operation<ExecutionContext> operation, boolean overwriteIfExists) throws ParseException {
		super(parent, context, condition);
		this.operationProvider = operationProvider;
		this.pathAnalyzer = new PathAnalyzer<ExecutionContext>(operationProvider);
		this.variableName = variableName;
		this.optionalType = optionalType;
		this.operation = operation;
		this.overwriteIfExists = overwriteIfExists;
		if (optionalType != null) {
			optionalTypeProvider = ALLOW_STRUCTURE_TYPES
				? new MultipleOptionalTypeProvider(Arrays.asList(OptionalTypeProviderFactory.getInstance().getProvider(), new StructureTypeProvider(repository)))
				: OptionalTypeProviderFactory.getInstance().getProvider();
			converter = optionalTypeProvider.getConverter(optionalType);
			if (converter == null) {
				throw new ParseException("Unknown type: " + optionalType, 0);
			}
		}
	}
	
	private Operation<ExecutionContext> getRewrittenOperation() throws ParseException {
		if (rewrittenOperation == null) {
			synchronized(this) {
				if (rewrittenOperation == null) {
					rewrittenOperation = rewrite(operation);
				}
			}
		}
		return rewrittenOperation;
	}

	@SuppressWarnings("unchecked")
	private Operation<ExecutionContext> rewrite(Operation<ExecutionContext> operation) throws ParseException {
		// can only rewrite operations if we have a glue operation provider that can give us static method descriptions
		if (operation == null || operationProvider == null || !(operationProvider instanceof GlueOperationProvider)) {
			return operation;
		}
		GlueOperationProvider operationProvider = (GlueOperationProvider) this.operationProvider;
		List<QueryPart> parts = null;
		if (operation.getType() == OperationType.METHOD) {
			String fullName = operation.getParts().get(0).getContent().toString();
			MethodDescription description = null;
			int minimumAmountOfParameters = 0;
			// we need to find the description that has the most parameters for this method
			// the methods with the same name are assumed to be overloaded versions of one another!
			// depending on the variable calculation, it is possible that fewer parameters are sent along
			for (MethodProvider provider : operationProvider.getMethodProviders()) {
				for (MethodDescription possibleDescription : provider.getAvailableMethods()) {
					if (possibleDescription.getName().equals(fullName) || (possibleDescription.getNamespace() != null && fullName.equals(possibleDescription.getNamespace() + "." + possibleDescription.getName()))) {
						if (description == null || possibleDescription.getParameters().size() > description.getParameters().size()) {
							description = possibleDescription;
						}
						if (possibleDescription.getParameters().size() <= minimumAmountOfParameters) {
							minimumAmountOfParameters = possibleDescription.getParameters().size() - 1;
						}
					}
				}
			}
			boolean canRewrite = description != null;
			boolean shouldRewrite = description == null || !description.isNamedParametersAllowed();
			// some methods don't need to be rewritten
			// even if we should rewrite but we can't we need to check, because in that case you should _not_ have a named parameter
			if (shouldRewrite) {
				// we can only rewrite if we have a description
				Map<String, QueryPart> newParts = new LinkedHashMap<String, QueryPart>();
				if (description != null) {
					for (ParameterDescription parameter : description.getParameters()) {
						// two parameters with same name are not allowed!
						if (newParts.containsKey(parameter.getName())) {
							canRewrite = false;
							break;
						}
						// set it to null
						Operation<ExecutionContext> nullOperation = operationProvider.newOperation(OperationType.NATIVE);
						nullOperation.add(new QueryPart(Type.NULL, null));
						newParts.put(parameter.getName(), new QueryPart(Type.OPERATION, nullOperation));
					}
				}
				// for index-based access
				List<String> parameterNames = new ArrayList<String>(newParts.keySet());
				// even if we can't rewrite, we need to check that you don't actually use the syntax in such a case
				// the last set parameter index in case of mingling named and unnamed
				int lastParameterIndex = -1;
				int highestIndex = -1;
				for (int i = 1; i < operation.getParts().size(); i++) {
					boolean isRewritten = false;
					if (operation.getParts().get(i).getContent() instanceof Operation) {
						Operation<ExecutionContext> argumentOperation = (Operation<ExecutionContext>) operation.getParts().get(i).getContent();
						if (argumentOperation.getType() == OperationType.CLASSIC && argumentOperation.getParts().size() == 3 && argumentOperation.getParts().get(1).getType() == Type.DIVIDE && argumentOperation.getParts().get(1).getContent().equals(":")) {
							if (!canRewrite) {
								throw new ParseException("The method '" + fullName + "' does not allow for named parameters", 0);
							}
							String parameterName = argumentOperation.getParts().get(0).getContent().toString();
							QueryPart valuePart = argumentOperation.getParts().get(2);
							if (!newParts.containsKey(parameterName)) {
								throw new ParseException("The parameter '" + parameterName + "' does not exist in the method description of '" + description.getName() + "' (" + description.getNamespace() + ")", 0);
							}
							newParts.put(parameterName, valuePart.getType() == Type.OPERATION ? valuePart : new QueryPart(Type.OPERATION, pathAnalyzer.analyze(Arrays.asList(valuePart))));
							lastParameterIndex = parameterNames.indexOf(parameterName);
							isRewritten = true;
						}
					}
					// we did not do a name-based rewrite in the above
					if (canRewrite && !isRewritten) {
						// if you have more parameters than defined, it must be a varargs parameter
						if (lastParameterIndex + 1 >= parameterNames.size()) {
							if (!description.getParameters().get(lastParameterIndex).isVarargs()) {
								throw new ParseException("Too many parameters when calling: " + fullName, 0);
							}
							newParts.put(parameterNames.get(lastParameterIndex) + newParts.size(), operation.getParts().get(i));
							// set the highest index higher!
							highestIndex = newParts.size() - 1; 
						}
						else {
							newParts.put(parameterNames.get(++lastParameterIndex), operation.getParts().get(i));
						}
					}
					if (lastParameterIndex > highestIndex) {
						highestIndex = lastParameterIndex;
					}
				}
				// only replace the parts if we can rewrite
				if (canRewrite) {
					// refresh names, some might have been added dynamically
					parameterNames = new ArrayList<String>(newParts.keySet());
					parts = new ArrayList<QueryPart>();
					parts.add(operation.getParts().get(0).clone());
					// put at least as many parameters in there as the smallest matching method expects
					for (int i = 0; i <= Math.max(highestIndex, minimumAmountOfParameters); i++) {
						parts.add(newParts.get(parameterNames.get(i)));
					}
				}
			}
		}
		if (parts == null) {
			parts = operation.getParts();
		}
		// rewrite children
		Operation<ExecutionContext> clone = operationProvider.newOperation(operation.getType());
		for (int i = 0; i < parts.size(); i++) {
			boolean isAdded = false;
			if (parts.get(i).getContent() instanceof Operation) {
				Operation<ExecutionContext> original = (Operation<ExecutionContext>) parts.get(i).getContent();
				if (original.getType() != OperationType.NATIVE) {
					clone.add(new QueryPart(Type.OPERATION, rewrite(original)));
					isAdded = true;
				}
			}
			if (!isAdded) {
				clone.add(parts.get(i));
			}
		}
		clone.finish();
		return clone;
	}
	
	@Override
	public void execute(ExecutionContext context) throws ExecutionException {
		if (variableName == null || context.getPipeline().get(variableName) == null || overwriteIfExists || (variableName != null && autocastIfOptional && !overwriteIfExists && context.getPipeline().get(variableName) != null)) {
			try {
				Object value = allowNamedParameters ? getRewrittenOperation().evaluate(context) : operation.evaluate(context);
				if (value instanceof Closeable) {
					ScriptRuntime.getRuntime().addTransactionable(new TransactionalCloseable((Closeable) value));
				}
				if (variableName != null) {
					// in this specific scenario we assume it is an optional assign and a value was passed in
					// we will attempt to cast the existing value to the type of the optionally assigned value as this is closest to what the script wanted
					if (autocastIfOptional && !overwriteIfExists && context.getPipeline().get(variableName) != null) {
						if (value != null) {
							Object current = context.getPipeline().get(variableName);
							if (current != null) {
								context.getPipeline().put(variableName, ConverterFactory.getInstance().getConverter().convert(current, value.getClass()));
							}
						}
					}
					else {
						if (context.isDebug()) {
							// trim values that are too long
							String stringValue = value == null ? "" : value.toString();
							if (stringValue.length() > BLOB_LENGTH) {
								stringValue = "BLOB: " + stringValue.substring(0, BLOB_LENGTH).replaceAll("[\r\n]+", " ") + "...";
							}
							ScriptMethods.debug("Result: " + variableName + " = " + stringValue);
						}
						context.getPipeline().put(variableName, value);
					}
				}
			}
			catch (Exception e) {
				throw new ExecutionException(e);
			}
		}
		else if (context.isDebug() && variableName != null && context.getPipeline().get(variableName) != null && !overwriteIfExists) {
			ScriptRuntime.getRuntime().getFormatter().print("Inherited parameter: " + variableName + " = " + context.getPipeline().get(variableName));
		}
		// convert if necessary
		if (variableName != null && converter != null && context.getPipeline().get(variableName) != null) {
			// for arrays, loop over the items
			if (context.getPipeline().get(variableName) instanceof Object[]) {
				Object [] items = (Object[]) context.getPipeline().get(variableName);
				Object [] targetItems = (Object[]) Array.newInstance(converter.getComponentType(), items.length);
				for (int i = 0; i < items.length; i++) {
					targetItems[i] = converter.convert(items[i]);
				}
				context.getPipeline().put(variableName, targetItems);
			}
			else {
				context.getPipeline().put(variableName, converter.convert(context.getPipeline().get(variableName)));
			}
		}
		// make it an array if neccessary
		if (isList && context.getPipeline().get(variableName) != null && !(context.getPipeline().get(variableName) instanceof Object[])) {
			context.getPipeline().put(variableName, ScriptMethods.array(context.getPipeline().get(variableName)));
		}
	}

	@Override
	public boolean isOverwriteIfExists() {
		return overwriteIfExists;
	}
	
	public void setOverwriteIfExists(boolean overwriteIfExists) {
		this.overwriteIfExists = overwriteIfExists;
	}

	@Override
	public String getVariableName() {
		return variableName;
	}

	public Operation<ExecutionContext> getOperation() {
		return operation;
	}
	
	@Override
	public String toString() {
		return operation.toString();
	}

	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}

	public void setOperation(Operation<ExecutionContext> operation) {
		this.operation = operation;
	}

	public boolean isAutocastIfOptional() {
		return autocastIfOptional;
	}

	public void setAutocastIfOptional(boolean autocastIfOptional) {
		this.autocastIfOptional = autocastIfOptional;
	}

	@Override
	public boolean isGenerated() {
		return generated;
	}
	public void setGenerated(boolean generated) {
		this.generated = generated;
	}

	@Override
	public String getOptionalType() {
		return optionalType;
	}

	public void setOptionalType(String optionalType) {
		this.optionalType = optionalType;
	}

	@Override
	public boolean isList() {
		return isList;
	}

	public void setList(boolean isList) {
		this.isList = isList;
	}
}
