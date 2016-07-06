package be.nabu.glue.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.EnclosedLambda;
import be.nabu.glue.api.Lambda;
import be.nabu.glue.impl.LambdaMethodProvider.LambdaExecutionOperation;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.evaluator.EvaluationException;

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
		private Double min, max;

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
			range = getVersion(null);
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
			return new Iterable() {
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
			return new Iterable() {
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
		if (parameters.size() > lambda.getDescription().getParameters().size()) {
			if (lambda.getDescription().getParameters().get(lambda.getDescription().getParameters().size() - 1).isVarargs()) {
				List varargs = new ArrayList();
				for (int i = lambda.getDescription().getParameters().size() - 1; i < parameters.size(); i++) {
					varargs.add(parameters.get(i));
				}
				parameters.set(lambda.getDescription().getParameters().size() - 1, varargs.toArray());
				for (int i = parameters.size() - 1; i > lambda.getDescription().getParameters().size(); i--) {
					parameters.remove(i);
				}
			}
			else {
				throw new RuntimeException("Too many parameters for the lambda");
			}
		}
		LambdaExecutionOperation lambdaOperation = new LambdaExecutionOperation(lambda.getDescription(), lambda.getOperation(), 
			lambda instanceof EnclosedLambda ? ((EnclosedLambda) lambda).getEnclosedContext() : new HashMap<String, Object>());
		try {
			return lambdaOperation.evaluateWithParameters(runtime.getExecutionContext(), parameters.toArray());
		}
		catch (EvaluationException e) {
			throw new RuntimeException(e);
		}
	}
}
