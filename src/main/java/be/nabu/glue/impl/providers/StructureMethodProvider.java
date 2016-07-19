package be.nabu.glue.impl.providers;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.EnclosedLambda;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.Lambda;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.MethodProvider;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.impl.ForkedExecutionContext;
import be.nabu.glue.impl.GlueUtils;
import be.nabu.glue.impl.LambdaImpl;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.impl.SimpleParameterDescription;
import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.api.ListableContextAccessor;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseMethodOperation;

@MethodProviderClass(namespace = "script")
public class StructureMethodProvider implements MethodProvider {

	@Override
	public Operation<ExecutionContext> resolve(String name) {
		if ("structure".equals(name) || "script.structure".equals(name)) {
			return new StructureOperation();
		}
		return null;
	}

	@Override
	public List<MethodDescription> getAvailableMethods() {
		List<MethodDescription> descriptions = new ArrayList<MethodDescription>();
		descriptions.add(new SimpleMethodDescription("script", "structure", "This will dynamically create a structure",
			Arrays.asList(new ParameterDescription [] { new SimpleParameterDescription("objects", "You can pass in multiple object consisting of existing structs and named parameters to be added", "Object", true) } ),
			new ArrayList<ParameterDescription>(),
			true));
		return descriptions;
	}

	private static class StructureOperation extends BaseMethodOperation<ExecutionContext> {

		@Override
		public void finish() throws ParseException {
			// do nothing
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			ForkedExecutionContext forkedContext = new ForkedExecutionContext(context, new HashMap<String, Object>());
			for (int i = 1; i < getParts().size(); i++) {
				Operation<ExecutionContext> argumentOperation = (Operation<ExecutionContext>) getParts().get(i).getContent();
				if (argumentOperation.getType() == OperationType.CLASSIC && argumentOperation.getParts().size() == 3 && argumentOperation.getParts().get(1).getType() == Type.NAMING && argumentOperation.getParts().get(1).getContent().equals(":")) {
					String parameterName;
					if (argumentOperation.getParts().get(0).getContent() instanceof Operation && ((Operation) argumentOperation.getParts().get(0).getContent()).getType() == OperationType.METHOD) {
						Object evaluated = ((Operation) argumentOperation.getParts().get(0).getContent()).evaluate(context);
						parameterName = evaluated instanceof Lambda ? GlueUtils.calculate((Lambda) evaluated, ScriptRuntime.getRuntime(), new ArrayList()).toString() : evaluated.toString();
					}
					else {
						parameterName = argumentOperation.getParts().get(0).getContent().toString();
					}
					Object value = argumentOperation.getParts().get(2).getType() == QueryPart.Type.OPERATION 
						? ((Operation<ExecutionContext>) argumentOperation.getParts().get(2).getContent()).evaluate(context)
						: argumentOperation.getParts().get(2).getContent();
					forkedContext.getPipeline().put(parameterName, value);
				}
				else {
					Object value = argumentOperation.evaluate(context);
					if (value != null) {
						ContextAccessor accessor = ContextAccessorFactory.getInstance().getAccessor(value.getClass());
						if (!(accessor instanceof ListableContextAccessor)) {
							throw new EvaluationException("Can not access the returned content of type: " + value.getClass());
						}
						for (String variable : ((ListableContextAccessor<Object>) accessor).list(value)) {
							forkedContext.getPipeline().put(variable, accessor.get(value, variable));
						}
					}
				}
			}
			// here we rewrite the lambda's in the new structure to make sure they have the correct scope
			for (String key : forkedContext.getPipeline().keySet()) {
				if (forkedContext.getPipeline().get(key) instanceof EnclosedLambda && ((EnclosedLambda) forkedContext.getPipeline().get(key)).isMutable()) {
					EnclosedLambda currentLambda = (EnclosedLambda) forkedContext.getPipeline().get(key);
					EnclosedLambda newLambda = new LambdaImpl(currentLambda.getDescription(), currentLambda.getOperation(), forkedContext.getPipeline(), true);
					forkedContext.getPipeline().put(key, newLambda);
				}
			}
			return forkedContext.getPipeline();
		}
		
	}
}
