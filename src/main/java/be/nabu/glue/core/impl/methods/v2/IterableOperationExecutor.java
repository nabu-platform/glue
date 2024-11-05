package be.nabu.glue.core.impl.methods.v2;

import java.util.Iterator;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.core.api.CollectionIterable;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.operations.OperationExecutor;
import be.nabu.libs.evaluator.impl.ClassicOperation;

public class IterableOperationExecutor implements OperationExecutor {

	@Override
	public boolean support(Object leftOperand, QueryPart.Type operator, Object rightOperand) {
		// if we are checking that the list is null or not null, we don't particularly care to differentiate between empty lists and actual null values
		if (leftOperand instanceof Iterable && rightOperand == null && (operator == QueryPart.Type.EQUALS || operator == QueryPart.Type.NOT_EQUALS)) {
			return true;
		}
		// boolean comparison of series
		else if (leftOperand instanceof Iterable && rightOperand instanceof Boolean && (operator == QueryPart.Type.EQUALS || operator == QueryPart.Type.NOT_EQUALS)) {
			return true;
		}
		else if (rightOperand instanceof Iterable && leftOperand instanceof Boolean && (operator == QueryPart.Type.EQUALS || operator == QueryPart.Type.NOT_EQUALS)) {
			return true;
		}
		// return all the entries that exist in all (intersect) or any (union)
//		else if (leftOperand instanceof Iterable && rightOperand instanceof Iterable && (operator == QueryPart.Type.BITWISE_AND || operator == QueryPart.Type.BITWISE_OR)) {
//			return true;
//		}
		return (leftOperand instanceof Iterable || rightOperand instanceof Iterable) && operator != QueryPart.Type.IN && operator != QueryPart.Type.NOT_IN
				&& operator != QueryPart.Type.EQUALS && operator != QueryPart.Type.NOT_EQUALS && operator != QueryPart.Type.NOT;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Object calculate(final Object leftOperand, final QueryPart.Type operator, final Object rightOperand) {
		if (leftOperand instanceof Iterable && rightOperand == null && (operator == QueryPart.Type.EQUALS || operator == QueryPart.Type.NOT_EQUALS)) {
			boolean isEmpty = !((Iterable) leftOperand).iterator().hasNext();
			return operator == QueryPart.Type.EQUALS ? isEmpty : !isEmpty;
		}
		// boolean comparison of series
		if ((leftOperand instanceof Boolean || rightOperand instanceof Boolean) && (operator == QueryPart.Type.EQUALS || operator == QueryPart.Type.NOT_EQUALS)) {
			Iterable iterable = (Iterable) (leftOperand instanceof Iterable ? leftOperand : rightOperand);
			Boolean result = (Boolean) (leftOperand instanceof Boolean ? leftOperand : rightOperand);
			for (Object object : iterable) {
				boolean equals = result.equals(object);
				if (equals && operator == QueryPart.Type.NOT_EQUALS) {
					return false;
				}
				else if (!equals && operator == QueryPart.Type.EQUALS) {
					return false;
				}
			}
			return true;
		}
		return new CollectionIterable() {
			@Override
			public Iterator iterator() {
				return new Iterator() {
					private Iterator left = leftOperand instanceof Iterable ? ((Iterable) leftOperand).iterator() : null;
					private Iterator right = rightOperand instanceof Iterable ? ((Iterable) rightOperand).iterator() : null;
					@Override
					public boolean hasNext() {
						return (left != null && left.hasNext()) || (right != null && right.hasNext());
					}
					@Override
					public Object next() {
						Object leftValue = left == null ? leftOperand : GlueUtils.resolveSingle(left.next());
						Object rightValue = right == null ? rightOperand : GlueUtils.resolveSingle(right.next());
						ClassicOperation<ExecutionContext> classic = new ClassicOperation<ExecutionContext>();
						// note: this is currently cheating, the classic operation looks for "native" parts or operation parts
						// we could (like the lambda does) create "native operations" and use the type unknown
						// but this also works if we simply take any query part that is of type native
						classic.getParts().add(new QueryPart(Type.STRING, leftValue));
						classic.getParts().add(new QueryPart(operator, operator.toString()));
						classic.getParts().add(new QueryPart(Type.STRING, rightValue));
						try {
							return classic.evaluate(null);
						}
						catch (EvaluationException e) {
							throw new RuntimeException(e);
						}
					}
				};
			}
		};
	}

}
