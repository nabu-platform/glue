package be.nabu.glue.impl.providers;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.operations.Minus;
import be.nabu.libs.evaluator.api.operations.Plus;

public class CustomDate implements Plus, Minus, Comparable<CustomDate> {
	
	private Date date;

	public CustomDate(Date date) {
		this.date = date;
	}

	public CustomDate() {
		this(new Date());
	}

	public CustomDate add(int field, int amount) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(field, amount);
		return new CustomDate(calendar.getTime());
	}
	
	public int getMonth() {
		return getCalendarField(Calendar.MONTH);
	}
	
	public int getYear() {
		return getCalendarField(Calendar.YEAR);
	}
	
	public int getDay() {
		return getCalendarField(Calendar.DAY_OF_MONTH);
	}
	
	public int getMinutes() {
		return getCalendarField(Calendar.MINUTE);
	}
	
	public int getSeconds() {
		return getCalendarField(Calendar.MINUTE);
	}
	
	public int getHours() {
		return getCalendarField(Calendar.HOUR);
	}
	
	public int getMilliSeconds() {
		return getCalendarField(Calendar.MILLISECOND);
	}
	
	public int getCalendarField(int field) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return calendar.get(field); 
	}
	
	@Override
	public String toString() {
		return date.toString();
	}

	public Date getDate() {
		return date;
	}
	
	public CustomDate normalize(String format) {
		SimpleDateFormat formatter = new SimpleDateFormat(format);
		try {
			return new CustomDate(formatter.parse(formatter.format(date)));
		}
		catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Object minus(Object value) {
		return increment(this, ConverterFactory.getInstance().getConverter().convert(value, String.class), Type.SUBSTRACT);
	}

	@Override
	public Object plus(Object value) {
		return increment(this, ConverterFactory.getInstance().getConverter().convert(value, String.class), Type.ADD);
	}
	
	public static CustomDate increment(CustomDate date, String increment, Type type) {
		int amount = new Integer(increment.replaceAll("^([0-9]+).*$", "$1"));
		int calendarField = getCalendarField(increment.replaceAll("^[0-9]+(.*)$", "$1").toLowerCase().charAt(0));
		if (type == Type.SUBSTRACT) {
			amount *= -1;
		}
		return date.add(calendarField, amount);
	}
	
	public static int getCalendarField(char name) {
		switch (name) {
			case 'y':
				return Calendar.YEAR;
			case 'm':
				return Calendar.MONTH;
			case 'd':
				return Calendar.DATE;
			case 'h':
				return Calendar.HOUR;
			case 'i':
				return Calendar.MINUTE;
			case 's':
				return Calendar.SECOND;
			case 'S':
				return Calendar.MILLISECOND;
		}
		throw new IllegalArgumentException("Unsupported calendar field: " + name);
	}

	@Override
	public int compareTo(CustomDate other) {
		return date.compareTo(other.date);
	}

}
