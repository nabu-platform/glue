package be.nabu.glue.core.impl.methods.v2;

import java.util.Calendar;
import java.util.Date;

import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.operations.OperationExecutor;

public class DateOperationExecutor implements OperationExecutor {

	@Override
	public boolean support(Object leftOperand, QueryPart.Type operator, Object rightOperand) {
		return leftOperand instanceof Date
				&& (operator == QueryPart.Type.ADD || operator == QueryPart.Type.SUBSTRACT || (
						((operator == QueryPart.Type.EQUALS || operator == QueryPart.Type.NOT_EQUALS || operator == QueryPart.Type.GREATER || operator == QueryPart.Type.GREATER_OR_EQUALS
								|| operator == QueryPart.Type.LESSER || operator == QueryPart.Type.LESSER_OR_EQUALS) && rightOperand instanceof Date)));
	}

	@Override
	public Object calculate(Object leftOperand, QueryPart.Type operator, Object rightOperand) {
		if (operator == QueryPart.Type.EQUALS) {
			return ((Date) leftOperand).getTime() == ((Date) rightOperand).getTime();
		}
		else if (operator == QueryPart.Type.NOT_EQUALS) {
			return ((Date) leftOperand).getTime() != ((Date) rightOperand).getTime();
		}
		else if (operator == QueryPart.Type.GREATER) {
			return ((Date) leftOperand).getTime() > ((Date) rightOperand).getTime();
		}
		else if (operator == QueryPart.Type.GREATER_OR_EQUALS) {
			return ((Date) leftOperand).getTime() >= ((Date) rightOperand).getTime();
		}
		else if (operator == QueryPart.Type.LESSER) {
			return ((Date) leftOperand).getTime() < ((Date) rightOperand).getTime();
		}
		else if (operator == QueryPart.Type.LESSER_OR_EQUALS) {
			return ((Date) leftOperand).getTime() <= ((Date) rightOperand).getTime();
		}
		// we assume it's an offset in milliseconds
		else if (rightOperand instanceof Number) {
			return operator == QueryPart.Type.ADD
				? new Date(((Date) leftOperand).getTime() + ((Number) rightOperand).longValue())
				: new Date(((Date) leftOperand).getTime() - ((Number) rightOperand).longValue());
		}
		else if (rightOperand instanceof Date) {
			return ((Date) leftOperand).getTime() - ((Date) rightOperand).getTime();
		}
		else {
			String right = rightOperand instanceof String ? (String) rightOperand : GlueUtils.convert(rightOperand, String.class);
			return increment((Date) leftOperand, right, operator);
		}
	}

	public static Date increment(Date date, String increment, Type type) {
		int amount = new Integer(increment.replaceAll("^([0-9]+).*$", "$1"));
		int calendarField = getCalendarField(increment.replaceAll("^[0-9]+[\\s]*(.*)$", "$1").toLowerCase());
		if (type == Type.SUBSTRACT) {
			amount *= -1;
		}
		return add(date, calendarField, amount);
	}
	
	public static Date add(Date date, int field, int amount) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(field, amount);
		return calendar.getTime();
	}
	
	public static int getCalendarField(String name) {
		if (name.startsWith("y")) {
			return Calendar.YEAR;
		}
		else if (name.startsWith("min")) {
			return Calendar.MINUTE;
		}
		else if (name.startsWith("m")) {
			return Calendar.MONTH;
		}
		else if (name.startsWith("w")) {
			return Calendar.WEEK_OF_YEAR;
		}
		else if (name.startsWith("d")) {
			return Calendar.DATE;
		}
		else if (name.startsWith("h")) {
			return Calendar.HOUR;
		}
		else if (name.startsWith("s")) {
			return Calendar.SECOND;
		}
		else if (name.startsWith("mil") || name.startsWith("ms")) {
			return Calendar.MILLISECOND;
		}
		throw new IllegalArgumentException("Unsupported calendar field: " + name);
	}

}
