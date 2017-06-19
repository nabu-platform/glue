package be.nabu.glue.core.impl.methods.v2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.ListableContextAccessor;
import be.nabu.libs.evaluator.api.operations.OperationExecutor;

public class StructureOperationExecutor implements OperationExecutor {

	@Override
	public boolean support(Object leftOperand, Type operator, Object rightOperand) {
		if (leftOperand != null && rightOperand != null && operator == Type.ADD) {
			return ContextAccessorFactory.getInstance().getAccessor(leftOperand.getClass()) instanceof ListableContextAccessor 
				&& ContextAccessorFactory.getInstance().getAccessor(rightOperand.getClass()) instanceof ListableContextAccessor;
		}
		return false;
	}

	@Override
	public Object calculate(Object leftOperand, Type operator, Object rightOperand) {
		try {
			return merge(leftOperand, rightOperand);
		}
		catch (EvaluationException e) {
			throw new RuntimeException(e);
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Map<String, Object> merge(Object leftOperand, Object rightOperand) throws EvaluationException {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		ListableContextAccessor left = (ListableContextAccessor) ContextAccessorFactory.getInstance().getAccessor(leftOperand.getClass());
		ListableContextAccessor right = (ListableContextAccessor) ContextAccessorFactory.getInstance().getAccessor(rightOperand.getClass());
		
		List<String> keys = new ArrayList<String>();
		for (String key : (Collection<String>) left.list(leftOperand)) {
			Object leftValue = left.get(leftOperand, key);
			Object rightValue = right.get(rightOperand, key);
			
			// if the right value is null, we simply put the left value
			if (rightValue == null) {
				result.put(key, leftValue);
			}
			// if the left value is null, the right value wins
			else if (leftValue == null) {
				result.put(key, rightValue);
			}
			else if (leftValue instanceof Iterable || rightValue instanceof Iterable) {
				result.put(key, SeriesMethods.merge(leftValue, rightValue));
			}
			// if we can merge them recursively, do so
			else if (support(leftValue, Type.ADD, rightValue)) {
				result.put(key, merge(leftValue, rightValue));
			}
			// otherwise the right value wins
			else {
				result.put(key, rightValue);
			}
			keys.add(key);
		}
		
		for (String key : (Collection<String>) right.list(rightOperand)) {
			// if we didn't handle it yet, it did not occur in the left, just add it
			if (!keys.contains(key)) {
				result.put(key, right.get(rightOperand, key));
			}
		}
		return result;
	}
	

}
