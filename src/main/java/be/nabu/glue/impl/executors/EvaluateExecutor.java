package be.nabu.glue.impl.executors;

import java.io.Closeable;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import be.nabu.glue.OptionalTypeProviderFactory;
import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.AssignmentExecutor;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.OptionalTypeConverter;
import be.nabu.glue.api.OptionalTypeProvider;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.impl.MultipleOptionalTypeProvider;
import be.nabu.glue.impl.StructureTypeProvider;
import be.nabu.glue.impl.TransactionalCloseable;
import be.nabu.glue.impl.methods.ScriptMethods;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.api.Operation;

public class EvaluateExecutor extends BaseExecutor implements AssignmentExecutor {

	private static final boolean ALLOW_STRUCTURE_TYPES = Boolean.parseBoolean(System.getProperty("structure.allow.types", "true"));
	
	private String variableName;
	private Operation<ExecutionContext> operation, rewrittenOperation;
	private boolean overwriteIfExists;
	private boolean autocastIfOptional = false;
	private boolean generated = false;
	private boolean allowNamedParameters = Boolean.parseBoolean(System.getProperty("named.parameters", "true"));
	private String optionalType;
	private boolean isList = false;
	private OptionalTypeProvider optionalTypeProvider;
	private OptionalTypeConverter converter;
	
	public EvaluateExecutor(ExecutorGroup parent, ExecutorContext context, ScriptRepository repository, Operation<ExecutionContext> condition, String variableName, String optionalType, Operation<ExecutionContext> operation, boolean overwriteIfExists) throws ParseException {
		super(parent, context, condition);
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
		// can only rewrite operations if we have a glue operation provider that can give us static method descriptions
		if (rewrittenOperation == null) {
			synchronized(this) {
				if (rewrittenOperation == null) {
					rewrittenOperation = rewrite(operation);
				}
			}
		}
		return rewrittenOperation;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
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
						context.getPipeline().put(variableName, value);
					}
				}
			}
			catch (Exception e) {
				throw new ExecutionException(e);
			}
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
			else if (context.getPipeline().get(variableName) instanceof Collection) {
				Collection items = (Collection) context.getPipeline().get(variableName);
				Collection targetItems = (Collection) new ArrayList(items.size());
				for (Object item : items) {
					targetItems.add(converter.convert(item));
				}
				context.getPipeline().put(variableName, targetItems);
			}
			else {
				context.getPipeline().put(variableName, converter.convert(context.getPipeline().get(variableName)));
			}
		}
		// make it an array if neccessary
		if (isList && context.getPipeline().get(variableName) != null && !(context.getPipeline().get(variableName) instanceof Object[]) && !(context.getPipeline().get(variableName) instanceof Collection)) {
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
