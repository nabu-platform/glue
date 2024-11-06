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

package be.nabu.glue.core.impl;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.core.api.CollectionIterable;
import be.nabu.glue.core.api.EnclosedLambda;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.impl.LambdaMethodProvider.LambdaExecutionOperation;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryParser;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.base.BaseMethodOperation;

public class GlueUtils {
	
	private static Map<String, VersionRange> versions = new HashMap<String, VersionRange>();

	public static final boolean USE_STREAMS = Boolean.parseBoolean(System.getProperty("glue.streams", "false"));
	
	private static boolean parallel = Boolean.parseBoolean(System.getProperty("glue.parallel", "true"));

	public static boolean useParallelism() {
		return parallel;
	}
	
	public static Object resolveSingle(Object object) {
		try {
			return object instanceof Callable ? ((Callable<?>) object).call() : object;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@SuppressWarnings("rawtypes")
	public static Iterable resolve(final Iterable iterable) {
		return iterable instanceof CallResolvingIterable ? iterable : new CallResolvingIterable(iterable);
	}
	
	public static String toSql(String rule, String tableName) throws ParseException {
		// we can only use single quotes, quote nesting is not feasible
		rule = rule.replace('"', '\'');
		List<QueryPart> parts = QueryParser.getInstance().parse(rule);
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			QueryPart part = parts.get(i);
			switch (part.getType()) {
				case LOGICAL_OR:
					builder.append(" or ");
				break;
				case LOGICAL_AND:
					builder.append(" and ");
				break;
				case NOT_EQUALS:
					if (parts.get(i + 1).getType() == Type.NULL) {
						builder.append(" is not null");
						i++;
					}
					else {
						builder.append(" <> ");
					}
				break;
				case EQUALS:
					if (parts.get(i + 1).getType() == Type.NULL) {
						builder.append(" is null");
						i++;
					}
					else {
						builder.append(" = ");
					}
				break;
				case NOT_MATCHES:
					builder.append(" not");
				case MATCHES:
					String content = parts.get(i + 1).getToken().getContent();
					// we want case insensitive
					if (content.contains("(?i)")) {
						content = content.replace("(?i)", "");
						builder.append(" ilike ");
					}
					else {
						builder.append(" like ");
					}
					// only basic regexes allowed
					content = content.replace(".*", "%");
					// does not matter here
					content = content.replace("(?m)", "");
					builder.append(content);
					i++;
				break;
				case VARIABLE:
					String variableName = part.getToken().getContent();
					variableName = variableName.replaceAll("([A-Z]{1,})", "_$1").toLowerCase();
					if (variableName.startsWith("_")) {
						variableName = variableName.substring(1);
					}
					if (tableName != null) {
						builder.append(tableName + ".");
					}
					builder.append(variableName);
				break;
				// quotes are maintained
//				case STRING:
//					builder.append("'" + part.getToken().getContent() + "'");
//				break;
				default:
					builder.append(part.getToken().getContent());
				// in glue we generally do "in" on a variable which can't work here
				// or we create a list of some sort (series, split,...) which is too much work to do for now
//				case IN:
//					builder.append(" any");
//				break;
			}
		}
		return builder.toString();
	}
	
	@SuppressWarnings("rawtypes")
	private static final class CallResolvingIterable implements Iterable {
		private final Iterable iterable;

		private CallResolvingIterable(Iterable iterable) {
			this.iterable = iterable;
		}
		@Override
		public Iterator iterator() {
			return new Iterator() {
				private Iterator iterator = iterable.iterator();
				@Override
				public boolean hasNext() {
					return iterator.hasNext();
				}
				@Override
				public Object next() {
					return hasNext() ? resolveSingle(iterator.next()) : null;
				}
			};
		}
	}

	public static class VersionRange {
		private Double min = 2d, max;

		public VersionRange(Double min, Double max) {
			this.min = min;
			this.max = max;
		}
		public VersionRange() {
			// auto construct
		}
		public Double getMin() {
			return min;
		}
		public void setMin(Double min) {
			this.min = min;
		}
		public Double getMax() {
			return max;
		}
		public void setMax(Double max) {
			this.max = max;
		}
		public boolean contains(Double version) {
			// no version is always in range, as is a negative version
			if (version == null || version < 0) {
				return true;
			}
			else if (min != null && version < min) {
				return false;
			}
			else if (max != null && version >= max) {
				return false;
			}
			return true;
		}
		public String toString() {
			return (min == null ? "*" : min) + "-" + (max == null ? "*" : max);
		}
	}
	
	public static VersionRange getVersion(String namespace, String name) {
		String fullName = (namespace == null ? "" : namespace + ".") + name;
		// first we try to get the version of the exact full name
		VersionRange range = getVersion(fullName);
		// if not found, we try to get the version of the namespace
		if (range == null) {
			range = getVersion(namespace);
		}
		// get a range for everything
		if (range == null) {
			range = getVersion();
		}
		return range;
	}
	
	public static VersionRange getVersion() {
		VersionRange range = getVersion(null);
		return range == null ? new VersionRange() : range;
	}
	
	private static VersionRange getVersion(String fullName) {
		if (!versions.containsKey(fullName)) {
			synchronized(versions) {
				if (!versions.containsKey(fullName)) {
					String property = System.getProperty(fullName == null ? "version" : "version:" + fullName);
					VersionRange range = null;
					if (property != null) {
						range = new VersionRange();
						int index = property.indexOf('-');
						// both min and max
						if (index > 0) {
							range.setMin(Double.parseDouble(property.substring(0, index)));
							range.setMax(Double.parseDouble(property.substring(index + 1)));
						}
						// only a max
						else if (index == 0) {
							range.setMax(Double.parseDouble(property.substring(index + 1)));
						}
						// only a min
						else {
							range.setMin(Double.parseDouble(property));
						}
					}
					versions.put(fullName, range);
				}
			}
		}
		return versions.get(fullName);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T convert(Object object, Class<T> targetClass) {
		if (object == null) {
			return null;
		}
		else if (targetClass.isAssignableFrom(object.getClass())) {
			return (T) object;
		}
		if (object instanceof String && targetClass.equals(byte[].class)) {
			return (T) ((String) object).getBytes(Charset.forName("UTF-8"));
		}
		Converter converter = ConverterFactory.getInstance().getConverter();
		if (!converter.canConvert(object.getClass(), targetClass)) {
			if (targetClass.equals(String.class)) {
				return (T) object.toString();
			}
			else {
				throw new ClassCastException("Can not convert to " + targetClass + ": " + object);
			}
		}
		return (T) converter.convert(object, targetClass);
	}
	
	public static Iterable<?> toSeries(Object...objects) {
		if (objects == null || objects.length == 0) {
			return new ArrayList<Object>();
		}
		else if (objects.length == 1 && objects[0] instanceof Iterable) {
			return (Iterable<?>) objects[0];
		}
		else {
			return Arrays.asList(objects);
		}
	}
	
	public static boolean isSeries(Object...objects) {
		return objects != null && (objects.length > 1 || (objects.length == 1 && objects[0] instanceof Iterable));
	}
	
	@SuppressWarnings("rawtypes")
	public static Object wrap(final ObjectHandler handler, final boolean handleNull, Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		else if (original.length == 1 && !GlueUtils.isSeries(original)) {
			return handler.handle(original[0]);
		}
		else {
			final Iterable<?> iterable = GlueUtils.toSeries(original);
			return new CollectionIterable() {
				@Override
				public Iterator iterator() {
					return new Iterator() {
						private Iterator parent = iterable.iterator();
						@Override
						public boolean hasNext() {
							return parent.hasNext();
						}
						@Override
						public Object next() {
							final Object next = parent.next();
							return new Callable() {
								@Override
								public Object call() throws Exception {
									if (next == null && !handleNull) {
										return null;
									}
									return handler.handle(GlueUtils.resolveSingle(next));
								}
							};
						}
					};
				}
			};
		}
	}
	
	public static ObjectHandler cast(final ObjectHandler handler, final Class<?> targetClass) {
		return new ObjectHandler() {
			@Override
			public Object handle(Object single) {
				if (single != null && !targetClass.isAssignableFrom(single.getClass())) {
					single = convert(single, targetClass);
				}
				return handler.handle(single);
			}
		};
	}
	
	@SuppressWarnings("rawtypes")
	public static Object explode(final ObjectHandler handler, final boolean handleNull, Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		else if (original.length == 1 && !GlueUtils.isSeries(original)) {
			return handler.handle(original[0]);
		}
		else {
			final Iterable<?> iterable = GlueUtils.toSeries(original);
			return new CollectionIterable() {
				@Override
				public Iterator iterator() {
					return new Iterator() {
						private Iterator parent = iterable.iterator();
						private Iterator current = null;
						@Override
						public boolean hasNext() {
							while (current == null || !current.hasNext()) {
								if (parent.hasNext()) {
									Object next = parent.next();
									if (next != null || handleNull) {
										if (next instanceof Callable) {
											try {
												next = ((Callable) next).call();
											}
											catch (Exception e) {
												throw new RuntimeException(e);
											}
										}
										current = ((Iterable<?>) handler.handle(next)).iterator();
									}
									else {
										current = null;
									}
								}
								else {
									current = null;
									break;
								}
							}
							return current != null && current.hasNext();
						}
						@Override
						public Object next() {
							return hasNext() ? current.next() : null;
						}
					};
				}
			};
		}
	}
	
	public static interface ObjectHandler {
		public Object handle(Object single);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Object calculate(Lambda lambda, ScriptRuntime runtime, List parameters) {
		int size = lambda.getDescription().getParameters().size();
		// if the last one is a list, allow it
		if (size < parameters.size() && (size == 0 || !lambda.getDescription().getParameters().get(size - 1).isList())) {
			parameters = parameters.subList(0, lambda.getDescription().getParameters().size());
		}
		// resolve any parameters that themselves are lazy
		for (int i = 0; i < parameters.size(); i++) {
			if (parameters.get(i) instanceof Callable) {
				try {
					parameters.set(i, ((Callable) parameters.get(i)).call());
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		LambdaExecutionOperation lambdaOperation = new LambdaExecutionOperation(lambda.getDescription(), lambda.getOperation(), 
			lambda instanceof EnclosedLambda ? ((EnclosedLambda) lambda).getEnclosedContext() : new HashMap<String, Object>());
		try {
			ScriptRuntime current = ScriptRuntime.getRuntime();
			runtime.registerInThread();
			try {
				return lambdaOperation.evaluateWithParameters(runtime.getExecutionContext(), parameters.toArray());
			}
			finally {
				if (current == null) {
					runtime.unregisterInThread();
				}
				else {
					current.registerInThread();
				}
			}
		}
		catch (EvaluationException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static Lambda toLambda(final Runnable runnable) {
		MethodDescription description = new SimpleMethodDescription("$generated", UUID.randomUUID().toString().replace("-", ""), null, new ArrayList<ParameterDescription>(), new ArrayList<ParameterDescription>());
		return new LambdaImpl(description, new BaseMethodOperation<ExecutionContext>() {
			@Override
			public void finish() throws ParseException {
			}
			@Override
			public Object evaluate(ExecutionContext context) throws EvaluationException {
				runnable.run();
				return null;
			}
			
		}, new HashMap<String, Object>());
	}
	
	public static InputStream toStream(Object content) throws IOException {
		InputStream input;
		if (content instanceof String) {
			input = be.nabu.glue.core.impl.methods.FileMethods.read((String) content);
			if (input == null) {
				throw new FileNotFoundException("Can not resolve the content of: " + content);
			}
		}
		else if (content instanceof byte[]) {
			input = new ByteArrayInputStream((byte []) content);
		}
		else if (content instanceof InputStream) {
			input = (InputStream) content;
		}
		else {
			throw new IllegalArgumentException("Can not figure out the type of content");
		}
		return input;
	}
}
