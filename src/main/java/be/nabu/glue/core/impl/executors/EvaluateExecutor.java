/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.glue.core.impl.executors;

import java.io.Closeable;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import be.nabu.glue.OptionalTypeProviderFactory;
import be.nabu.glue.api.AssignmentExecutor;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.core.api.CollectionIterable;
import be.nabu.glue.core.api.OptionalTypeConverter;
import be.nabu.glue.core.api.OptionalTypeProvider;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.MultipleOptionalTypeProvider;
import be.nabu.glue.core.impl.StructureTypeProvider;
import be.nabu.glue.core.impl.methods.ScriptMethods;
import be.nabu.glue.core.impl.parsers.GlueParserProvider;
import be.nabu.glue.impl.TransactionalCloseable;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.glue.utils.ScriptUtils;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.api.WritableContextAccessor;

public class EvaluateExecutor extends BaseExecutor implements AssignmentExecutor {

	private static final boolean ALLOW_STRUCTURE_TYPES = Boolean.parseBoolean(System.getProperty("structure.allow.types", "true"));
	
	public static String DEFAULT_VARIABLE_NAME_PARAMETER = "defaultVariableName";
	
	private String variableName;
	private Operation<ExecutionContext> operation, rewrittenOperation, variableAccessOperation, indexAccessOperation;
	private boolean overwriteIfExists;
	private boolean autocastIfOptional = false;
	private boolean generated = false;
	private boolean allowNamedParameters = Boolean.parseBoolean(System.getProperty("named.parameters", "true"));
	private String optionalType;
	private boolean isList = false;
	private OptionalTypeProvider optionalTypeProvider;
	private OptionalTypeConverter converter;
	private boolean createNonExistentParents = true;

	private ScriptRepository repository;
	
	public EvaluateExecutor(ExecutorGroup parent, ExecutorContext context, ScriptRepository repository, Operation<ExecutionContext> condition, String variableName, String optionalType, Operation<ExecutionContext> operation, boolean overwriteIfExists, Operation<ExecutionContext> variableAccessOperation, Operation<ExecutionContext> indexAccessOperation) throws ParseException {
		super(parent, context, condition);
		this.repository = repository;
		this.variableName = variableName;
		this.optionalType = optionalType;
		this.operation = operation;
		this.overwriteIfExists = overwriteIfExists;
		this.variableAccessOperation = variableAccessOperation;
		this.indexAccessOperation = indexAccessOperation;
		if (optionalType != null) {
			optionalTypeProvider = getTypeProvider(repository, null);
			converter = optionalTypeProvider.getConverter(optionalType);
		}
	}

	public static OptionalTypeProvider getTypeProvider(ScriptRepository repository, Map<String, Object> additionalContext) {
		return ALLOW_STRUCTURE_TYPES
			? new MultipleOptionalTypeProvider(Arrays.asList(OptionalTypeProviderFactory.getInstance().getProvider(), new StructureTypeProvider(ScriptUtils.getRoot(repository), additionalContext)))
			: OptionalTypeProviderFactory.getInstance().getProvider();
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
		String variableName = this.variableName;
		// in some circumstances you want to keep track of the last calculation you did (e.g. interactive mode). this means we capture any result if no variable name given
		if (variableName == null) {
			variableName = context.getExecutionEnvironment().getParameters().get(DEFAULT_VARIABLE_NAME_PARAMETER);
		}
		
		List<Object> targets = new ArrayList<Object>();
		// if we have a variable access inside the variable name, we are accessing something deeper down
		if (variableAccessOperation != null) {
			try {
				Object target = variableAccessOperation.evaluate(context);
				if (target instanceof Object[]) {
					targets.addAll(Arrays.asList((Object[]) target));
				}
				else if (target instanceof Collection) {
					targets.addAll((Collection) target);
				}
				else if (target instanceof Iterable) {
					for (Object single : (Iterable) target) {
						targets.add(single);
					}
				}
				else if (target != null) {
					targets.add(target);
				}
				// if we have no target, we should probably create one
				else if (createNonExistentParents) {
					Object current = context.getPipeline();
					List<QueryPart> parts = new ArrayList<QueryPart>(variableAccessOperation.getParts());
					for (int i = 0; i < parts.size(); i++) {
						QueryPart part = parts.get(i);
						// if we are accessing a variable, create it if it doesn't exist
						// if we are followed by an operation accessor, this should be a list
						if (part.getType() == QueryPart.Type.VARIABLE) {
							ContextAccessor accessor = ContextAccessorFactory.getInstance().getAccessor(current.getClass());
							if (accessor == null) {
								throw new IllegalArgumentException("No accessor found for: " + current.getClass());
							}
							String localName = part.getContent().toString().replaceAll("^[/]+", "");
							Object object = accessor.get(current, localName);
							if (object == null) {
								if (accessor instanceof WritableContextAccessor) {
									object = i == parts.size() - 1 || parts.get(i + 1).getType() != QueryPart.Type.OPERATION ? new HashMap<String, Object>() : new ArrayList<Object>();
									((WritableContextAccessor) accessor).set(current, localName, object);
									// the accessor might transform the object (e.g. wrap it in a MapContent), update the object to resolving any transformation
									object = accessor.get(current, localName);
								}
								else {
									throw new IllegalArgumentException("Can not create " + localName + ", it is not a writable context");
								}
							}
							current = object;
						}
						// the operation accessor can assume that the variable operator in the previous part has already been executed, so the "current" is pointing to the list already
						else {
							String content = part.getContent().toString().replaceAll("^[/]+", "");
							Integer index;
							// if we have pure indexed access, we need to make sure the index exists (fill with nulls if necessary)
							if (content.matches("^[0-9]+$")) {
								index = Integer.parseInt(content);
							}
							// otherwise we hope that it is a variable available on the pipeline and that points to a numeric identifier
							else {
								Object object = context.getPipeline().get(content);
								if (object == null) {
									throw new IllegalArgumentException("Can not resolve '" + content + "' in " + variableAccessOperation);
								}
								index = Integer.parseInt(object.toString());
							}
							// currently rather restrictive to only support list but hey...
							if (!(current instanceof List)) {
								throw new IllegalArgumentException("Not an editable list '" + content + "': " + current);
							}
							Object object = index >= ((List) current).size() ? null : ((List) current).get(index);
							if (object == null) {
								object = new HashMap<String, Object>();
								for (int j = ((List) current).size(); j < index; j++) {
									((List) current).add(null);	
								}
								if (index == ((List) current).size()) {
									((List) current).add(object);
								}
								else {
									((List) current).set(index, object);
								}
							}
							current = object;
						}
					}
					targets.add(current);
				}
			}
			catch (EvaluationException e) {
				throw new ExecutionException("Failed to execute variable access operation: " + variableAccessOperation, e);
			}
		}
		else {
			targets.add(context);
		}
		Object value = null;
		boolean evaluated = false;
		
		Object index = null;
		if (indexAccessOperation != null) {
			try {
				index = indexAccessOperation.evaluate(context);
			}
			catch (EvaluationException e) {
				throw new ExecutionException("Failed to execute index access operation: " + indexAccessOperation, e);
			}
			if (index == null) {
				throw new ExecutionException("The index '" + indexAccessOperation + "' is null");
			}
			// we set the variablename to the index!
			// we should end up on the collectioncontextaccessor (normally?)
			variableName = index.toString();
		}
		
		for (Object target : targets) {
			ContextAccessor accessor = ContextAccessorFactory.getInstance().getAccessor(target.getClass());
			if (!(accessor instanceof WritableContextAccessor)) {
				throw new ExecutionException("Could not access target context for writing: " + target.getClass());
			}
			WritableContextAccessor writer = (WritableContextAccessor) accessor;
			
			// if we have indexed access, we do things a bit differently...
			if (index != null) {
				try {
					Object currentCollection = writer.get(target, this.variableName);
					// if we don't have a collection yet, we instantiate one (currently only list is supported!)
					if (currentCollection == null) {
						currentCollection = new ArrayList();
						writer.set(target, this.variableName, currentCollection);
					}
					// if we have an iterable that is not a collection, we likely have a lazy list
					// it must be resolved to perform indexed access...
					else if (currentCollection instanceof Iterable && !(currentCollection instanceof Collection)) {
						ArrayList newCollection = new ArrayList();
						for (Object single : (Iterable) currentCollection) {
							newCollection.add(single == null ? null : GlueUtils.resolveSingle(single));
						}
						currentCollection = newCollection;
						// must update it...
						writer.set(target, this.variableName, currentCollection);
					}
					// we update the target to point to the collection!
					accessor = ContextAccessorFactory.getInstance().getAccessor(currentCollection.getClass());
					if (!(accessor instanceof WritableContextAccessor)) {
						throw new ExecutionException("Could not access target context for collection writing: " + currentCollection.getClass());
					}
					writer = (WritableContextAccessor) accessor;
					target = currentCollection;
				}
				catch (EvaluationException e) {
					throw new ExecutionException("Can not access the collection", e);
				}
			}
			
			try {
				if (variableName == null || writer.get(target, variableName) == null || overwriteIfExists || (variableName != null && autocastIfOptional && !overwriteIfExists && writer.get(target, variableName) != null)) {
					try {
						if (!evaluated) {
							value = allowNamedParameters ? getRewrittenOperation().evaluate(context) : operation.evaluate(context);
							evaluated = true;
						}
						if (value instanceof Closeable) {
							ScriptRuntime.getRuntime().addTransactionable(new TransactionalCloseable((Closeable) value));
						}
						if (variableName != null) {
							// in this specific scenario we assume it is an optional assign and a value was passed in
							// we will attempt to cast the existing value to the type of the optionally assigned value as this is closest to what the script wanted
							if (autocastIfOptional && !overwriteIfExists && writer.get(target, variableName) != null) {
								if (value != null) {
									Object current = writer.get(target, variableName);
									if (current != null) {
										writer.set(target, variableName, ConverterFactory.getInstance().getConverter().convert(current, value.getClass()));
									}
								}
							}
							else {
								writer.set(target, variableName, value);
							}
						}
					}
					catch (Exception e) {
						throw new ExecutionException("Failed to execute: " + operation, e);
					}
				}
				// it is possible the type can only be resolved at runtime because it is a lambda
				OptionalTypeConverter converter = this.converter;
				if (optionalType != null && converter == null) {
					converter = getTypeProvider(repository, context.getPipeline()).getConverter(optionalType);
					if (converter == null) {
						throw new ExecutionException("Unknown type: " + optionalType);
					}
				}
				// convert if necessary
				if (variableName != null && converter != null && writer.get(target, variableName) != null) {
					// for arrays, loop over the items
					if (isList && writer.get(target, variableName) instanceof Object[]) {
						Object [] items = (Object[]) writer.get(target, variableName);
						Object [] targetItems = (Object[]) Array.newInstance(converter.getComponentType(), items.length);
						for (int i = 0; i < items.length; i++) {
							targetItems[i] = converter.convert(items[i]);
						}
						writer.set(target, variableName, targetItems);
					}
					else if (isList && writer.get(target, variableName) instanceof Collection) {
						Collection items = (Collection) writer.get(target, variableName);
						Collection targetItems = (Collection) new ArrayList(items.size());
						for (Object item : items) {
							targetItems.add(converter.convert(item));
						}
						writer.set(target, variableName, targetItems);
					}
					else if (isList && writer.get(target, variableName) instanceof Iterable) {
						final Iterable items = (Iterable) writer.get(target, variableName);
						final OptionalTypeConverter finalConverter = converter;
						writer.set(target, variableName, new CollectionIterable() {
							@Override
							public Iterator iterator() {
								return new Iterator() {
									private Iterator parent = items.iterator();
									@Override
									public boolean hasNext() {
										return parent.hasNext();
									}
									@Override
									public Object next() {
										Object next = parent.next();
										return next == null ? null : finalConverter.convert(next);
									}
									
								};
							}
						});
					}
					else {
						writer.set(target, variableName, converter.convert(writer.get(target, variableName)));
					}
				}
				// this is only valid pre-version 2
				if (GlueUtils.getVersion().contains(1.0)) {
					// make it an array if neccessary
					if (isList && writer.get(target, variableName) != null && !(writer.get(target, variableName) instanceof Object[]) && !(writer.get(target, variableName) instanceof Collection)) {
						writer.set(target, variableName, ScriptMethods.array(writer.get(target, variableName)));
					}
				}
				// otherwise, make it an iterable if requested
				else if (isList && writer.get(target, variableName) != null && !(writer.get(target, variableName) instanceof Iterable)) {
					ArrayList list = new ArrayList();
					list.add(writer.get(target, variableName));
					writer.set(target, variableName, list);
				}
			}
			catch (Exception e) {
				throw new ExecutionException("Could not update " + target, e);
			}
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

	@Override
	public Object getDefaultValue() {
		if (this.operation != null) {
			if (operation.getType() == OperationType.NATIVE && !operation.getParts().isEmpty()) {
				return operation.getParts().get(0).getContent();
			}
		}
		return null;
	}

	public Operation<ExecutionContext> getVariableAccessOperation() {
		return variableAccessOperation;
	}

	public void setVariableAccessOperation(Operation<ExecutionContext> variableAccessOperation) {
		this.variableAccessOperation = variableAccessOperation;
	}

	public Operation<ExecutionContext> getIndexAccessOperation() {
		return indexAccessOperation;
	}

	public void setIndexAccessOperation(Operation<ExecutionContext> indexAccessOperation) {
		this.indexAccessOperation = indexAccessOperation;
	}
	
}
