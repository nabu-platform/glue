package be.nabu.glue.impl.methods.v2;

import java.util.Iterator;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.operations.OperationExecutor;
import be.nabu.libs.evaluator.impl.ClassicOperation;

public class IterableOperationExecutor implements OperationExecutor {

	@Override
	public boolean support(Operator operator) {
		return true;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Object calculate(final Object leftOperand, final Operator operator, final Object rightOperand) {
		return new Iterable() {
			@Override
			public Iterator iterator() {
				return new Iterator() {
					private Iterator left = ((Iterable) leftOperand).iterator();
					private Iterator right = rightOperand instanceof Iterable ? ((Iterable) rightOperand).iterator() : null;
					@Override
					public boolean hasNext() {
						return left.hasNext();
					}
					@Override
					public Object next() {
						Object leftValue = left.next();
						Object rightValue = right == null ? rightOperand : right.next();
						ClassicOperation<ExecutionContext> classic = new ClassicOperation<ExecutionContext>();
						// note: this is currently cheating, the classic operation looks for "native" parts or operation parts
						// we could (like the lambda does) create "native operations" and use the type unknown
						// but this also works if we simply take any query part that is of type native
						classic.getParts().add(new QueryPart(Type.STRING, leftValue));
						classic.getParts().add(new QueryPart(operator.getQueryPartType(), operator.getQueryPartType().toString()));
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

	@Override
	public Class<?> getSupportedType() {
		return Iterable.class;
	}

}
