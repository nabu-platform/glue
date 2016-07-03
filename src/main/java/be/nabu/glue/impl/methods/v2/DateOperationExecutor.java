package be.nabu.glue.impl.methods.v2;

import java.util.Calendar;
import java.util.Date;

import be.nabu.glue.impl.GlueUtils;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.operations.OperationExecutor;

public class DateOperationExecutor implements OperationExecutor {

	@Override
	public boolean support(Operator operator) {
		return operator == Operator.PLUS || operator == Operator.MINUS;
	}

	@Override
	public Object calculate(Object leftOperand, Operator operator, Object rightOperand) {
		// we assume it's an offset in milliseconds
		if (rightOperand instanceof Number) {
			return operator == Operator.PLUS
				? new Date(((Date) leftOperand).getTime() + ((Number) rightOperand).longValue())
				: new Date(((Date) leftOperand).getTime() - ((Number) rightOperand).longValue());
		}
		else {
			String right = rightOperand instanceof String ? (String) rightOperand : GlueUtils.convert(rightOperand, String.class);
			return increment((Date) leftOperand, right, operator == Operator.PLUS ? Type.ADD : Type.SUBSTRACT);
		}
	}

	@Override
	public Class<?> getSupportedType() {
		return Date.class;
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
