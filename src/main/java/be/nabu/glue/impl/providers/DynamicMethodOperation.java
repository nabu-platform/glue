package be.nabu.glue.impl.providers;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.Lambda;
import be.nabu.glue.api.MethodProvider;
import be.nabu.glue.impl.GlueUtils;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;
import be.nabu.libs.evaluator.base.BaseOperation;
import be.nabu.libs.evaluator.impl.MethodOperation;
import be.nabu.utils.io.IOUtils;

public class DynamicMethodOperation extends BaseOperation<ExecutionContext> {

	private Operation<ExecutionContext> operation;

	private MethodProvider [] methodProviders;
	
	public DynamicMethodOperation(MethodProvider...methodProviders) {
		this.methodProviders = methodProviders;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Object evaluate(ExecutionContext context) throws EvaluationException {
		if (operation != null) {
			return postProcess(operation.evaluate(context));
		}
		else if (getParts().get(0).getContent() instanceof Operation) {
			Object result = ((Operation) getParts().get(0).getContent()).evaluate(context);
			if (result instanceof Lambda) {
				List parameters = new ArrayList();
				for (int i = 1; i < getParts().size(); i++) {
					parameters.add(getParts().get(i).getContent() instanceof Operation ? ((Operation) getParts().get(i).getContent()).evaluate(context) : getParts().get(i).getContent());
				}
				return postProcess(GlueUtils.calculate((Lambda) result, ScriptRuntime.getRuntime(), parameters));
			}
			throw new EvaluationException("Could not resolve a lambda: " + getParts().get(0).getContent());
		}
		else {
			throw new EvaluationException("Could not resolve a method with the name: " + getParts().get(0).getContent());
		}
	}
	
	private static Object postProcess(Object returnValue) {
		if (!GlueUtils.getVersion().contains(1.0)) {
			if (returnValue instanceof InputStream) {
				InputStream stream = (InputStream) returnValue;
				try {
					try {
						returnValue = IOUtils.toBytes(IOUtils.wrap(stream));
					}
					finally {
						stream.close();
					}
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
		return returnValue;
	}

	@Override
	public void finish() throws ParseException {
		if (operation == null && !(getParts().get(0).getContent() instanceof Operation)) {
			String fullName = (String) getParts().get(0).getContent();
			operation = getOperation(fullName);
			if (operation != null) {
				for (QueryPart part : getParts()) {
					operation.add(new QueryPart(part.getType(), part.getContent()));
				}
				operation.finish();
			}
		}
	}

	protected Operation<ExecutionContext> getOperation(String fullName) {
		for (MethodProvider provider : methodProviders) {
			Operation<ExecutionContext> operation = provider.resolve(fullName);
			if (operation != null) {
				return operation;
			}
		}
		// if no operations were found, just return a method operation, it will fail later on but at least it will show something
		return null;
	}
	
	@Override
	public OperationType getType() {
		return OperationType.METHOD;
	}
	
	@Override
	public String toString() {
		Operation<ExecutionContext> operation = this.operation;
		if (operation == null) {
			operation = new MethodOperation<ExecutionContext>();
			for (QueryPart part : getParts()) {
				operation.add(new QueryPart(part.getType(), part.getContent()));
			}
		}
		return operation.toString();
	}

	public MethodProvider[] getMethodProviders() {
		return methodProviders;
	}
}
