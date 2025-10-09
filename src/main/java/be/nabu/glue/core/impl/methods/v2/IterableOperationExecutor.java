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

package be.nabu.glue.core.impl.methods.v2;

import java.util.Iterator;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.core.api.CollectionIterable;
import be.nabu.glue.core.converters.IterableToBoolean;
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
		// @2025-09-08, assume this example:
		// 		myDocuments = series()
		// 		echo(myDocuments == null)
		// 		echo(null == myDocuments)
		// Until now this would output "true", then "false" because we only allow the left operand to be a list and the right null
		// In glue the left operand normally determines the type of the operation
		// However, null does not have a valid data type so the argument can be made that that particular rule should not apply here
		if ( ((leftOperand instanceof Iterable && rightOperand == null) || (leftOperand == null && rightOperand instanceof Iterable)) && (operator == QueryPart.Type.EQUALS || operator == QueryPart.Type.NOT_EQUALS)) {
			return true;
		}
		// @2025-09-08: disabled for consistency sake
		// normally the left operand determines the type in which the operation will be done
		// it is usually rather useless to do a comparison of a list to a singular anyway so it is not used much
		// currently I would prefer consistency over supporting a very vague edge case
		
//		// boolean comparison of series
//		else if (leftOperand instanceof Iterable && rightOperand instanceof Boolean && (operator == QueryPart.Type.EQUALS || operator == QueryPart.Type.NOT_EQUALS)) {
//			return true;
//		}
//		else if (rightOperand instanceof Iterable && leftOperand instanceof Boolean && (operator == QueryPart.Type.EQUALS || operator == QueryPart.Type.NOT_EQUALS)) {
//			return true;
//		}
		// return all the entries that exist in all (intersect) or any (union)
//		else if (leftOperand instanceof Iterable && rightOperand instanceof Iterable && (operator == QueryPart.Type.BITWISE_AND || operator == QueryPart.Type.BITWISE_OR)) {
//			return true;
//		}
		return (leftOperand instanceof Iterable || rightOperand instanceof Iterable) && operator != QueryPart.Type.IN && operator != QueryPart.Type.NOT_IN
				&& operator != QueryPart.Type.EQUALS && operator != QueryPart.Type.NOT_EQUALS 
				// @2025-09-08
				// we exclude boolean logic (NOT was already excluded!) because:
				// a) it generally does not make sense to do bulk boolean operations because this requires each element is a boolean, this is a vanishingly small usecase
				// b) it complicates what we assume will happen in this case for instance:
				// 		myDocuments = series("test", "test2")
				//		boolean b = myDocuments && false
				// 		echo(b)
				// If we don't blacklist boolean operators, this will output "true" because we actually create a series with [false, false] which (when cast to boolean) is a series with a non-null value so it becomes true!
				// We considered other options (e.g. special handling of boolean-only arrays when casting to boolean) but these seem even worse as a solution
				&& operator != QueryPart.Type.NOT && operator != QueryPart.Type.BITWISE_AND && operator != QueryPart.Type.LOGICAL_AND && operator != QueryPart.Type.LOGICAL_OR && operator != QueryPart.Type.BITWISE_OR && operator != QueryPart.Type.XOR && operator != QueryPart.Type.NOT_XOR;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Object calculate(final Object leftOperand, final QueryPart.Type operator, final Object rightOperand) {
		if (leftOperand instanceof Iterable && rightOperand == null && (operator == QueryPart.Type.EQUALS || operator == QueryPart.Type.NOT_EQUALS)) {
			boolean isEmpty = !((Iterable) leftOperand).iterator().hasNext();
			return operator == QueryPart.Type.EQUALS ? isEmpty : !isEmpty;
		}
		else if (leftOperand == null && rightOperand instanceof Iterable  && (operator == QueryPart.Type.EQUALS || operator == QueryPart.Type.NOT_EQUALS)) {
			boolean isEmpty = !((Iterable) rightOperand).iterator().hasNext();
			return operator == QueryPart.Type.EQUALS ? isEmpty : !isEmpty;
		}
		// boolean comparison of series
		if ((leftOperand instanceof Boolean || rightOperand instanceof Boolean) && (operator == QueryPart.Type.EQUALS || operator == QueryPart.Type.NOT_EQUALS)) {
			Iterable iterable = (Iterable) (leftOperand instanceof Iterable ? leftOperand : rightOperand);
			Boolean result = (Boolean) (leftOperand instanceof Boolean ? leftOperand : rightOperand);
			// @2025-09-08: this way of calculating the boolean result of a list is inconsistent with:
			// - boolean cast of the list (e.g. boolean a = series("true")
			// - boolean casting of single elements within the list since we use the Boolean.equals method rather than converting to boolean, which misses things like the string "true"
			// for consistency sake, we will reuse the logic as defined in iterable to boolean
			return new IterableToBoolean().convert(iterable).equals(result);
//			for (Object object : iterable) {
//				boolean equals = result.equals(object);
//				if (equals && operator == QueryPart.Type.NOT_EQUALS) {
//					return false;
//				}
//				else if (!equals && operator == QueryPart.Type.EQUALS) {
//					return false;
//				}
//			}
//			return true;
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
