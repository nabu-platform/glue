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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.core.api.CollectionIterable;
import be.nabu.glue.core.api.EnclosedLambda;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.LambdaMethodProvider.LambdaExecutionOperation;
import be.nabu.glue.core.impl.methods.v2.generators.LambdaSeriesGenerator;
import be.nabu.glue.core.impl.methods.v2.generators.LongGenerator;
import be.nabu.glue.core.impl.methods.v2.generators.StringGenerator;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "series")
public class SeriesMethods {
	// small retrofits were done to support modifying the list, like wrapping an array list around the arrays.aslist...
	@GlueMethod(returns = "series", description = "This method creates a series out of a number of objects", version = 2)
	public static Iterable<?> series(@GlueParam(name = "content", description = "The objects to put in the series") Object...objects) {
		return objects == null ? new ArrayList<Object>() : new ArrayList<Object>(Arrays.asList(objects));
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@GlueMethod(version = 2)
	public static Object unique(Object...objects) {
		Iterable<?> series = GlueUtils.toSeries(objects);
		return new LinkedHashSet(resolve(series));
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@GlueMethod(version = 2)
	public static Map group(Lambda lambda, Object...objects) throws EvaluationException {
		if (objects == null || objects.length == 0) {
			return null;
		}
		Map map = new HashMap();
		if (lambda.getDescription().getParameters().size() != 1) {
			throw new IllegalArgumentException("The lambda does not have enough parameters to process the element");
		}
		Iterable<?> series = GlueUtils.toSeries(objects);
		ExecutionContext executionContext = ScriptRuntime.getRuntime().getExecutionContext();
		for (Object object : resolve(series)) {
			LambdaExecutionOperation lambdaOperation = new LambdaExecutionOperation(lambda.getDescription(), lambda.getOperation(), 
				lambda instanceof EnclosedLambda ? ((EnclosedLambda) lambda).getEnclosedContext() : new HashMap<String, Object>());
			Object key = lambdaOperation.evaluateWithParameters(executionContext, object);
			// it is possible to belong to multiple groups, hence the key can either be a single key or a list of keys
			if (!(key instanceof Iterable)) {
				key = Arrays.asList(key);
			}
			for (Object single : (Iterable) key) {
				if (!map.containsKey(single)) {
					map.put(single, new ArrayList());
				}
				((List) map.get(single)).add(object);
			}
		}
		return map;
	}	
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@GlueMethod(version = 2)
	public static Iterable<?> sort(@GlueParam(name = "lambda") final Lambda lambda, @GlueParam(name = "series") Object...objects) {
		final Iterable<?> series = GlueUtils.toSeries(objects);
		List<?> resolved = resolve(series);
		final ScriptRuntime runtime = ScriptRuntime.getRuntime();
		if (lambda == null) {
			Collections.sort((List<Comparable>) resolved);
		}
		else {
			Collections.sort(resolved, new Comparator() {
				@Override
				public int compare(Object o1, Object o2) {
					List parameters = new ArrayList();
					parameters.add(o1);
					parameters.add(o2);
					return GlueUtils.convert(GlueUtils.calculate(lambda, runtime, parameters), Integer.class);
				}
			});
		}
		return resolved;
	}
	
	@SuppressWarnings("rawtypes")
	@GlueMethod(version = 2)
	public static CollectionIterable<?> repeat(Object...objects) {
		final Iterable<?> series = GlueUtils.toSeries(objects);
		return new CollectionIterable() {
			@Override
			public Iterator iterator() {
				return new Iterator() {
					private Iterator iterator = series.iterator();

					@Override
					public boolean hasNext() {
						if (!iterator.hasNext()) {
							iterator = series.iterator();
						}
						return iterator.hasNext();
					}
					@Override
					public Object next() {
						if (hasNext()) {
							return iterator.next();
						}
						return null;
					}
				};
			}
		};
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static long position(final Object input, Object...objects) {
		if (input instanceof Lambda) {
			Iterable<?> series = GlueUtils.toSeries(objects);
			ScriptRuntime runtime = ScriptRuntime.getRuntime();
			Lambda lambda = (Lambda) input;
			boolean includeIndex = lambda.getDescription().getParameters().size() == 2;
			long index = 0;
			for (Object object : series) {
				object = GlueUtils.resolveSingle(object);
				List parameters = new ArrayList();
				parameters.add(object);
				if (includeIndex) {
					parameters.add(index);
				}
				Boolean calculate = (Boolean) GlueUtils.calculate(lambda, runtime, parameters);
				if (calculate != null && calculate) {
					return index;
				}
				index++;
			}
			return -1;
		}
		else {
			Iterable<?> series = GlueUtils.toSeries(objects);
			int index = 0;
			for (Object object : series) {
				if (object == null && input == null) {
					return index;
				}
				else if (input != null && input.equals(object)) {
					return index;
				}
				index++;
			}
			return -1;
		}
	}
	
	@SuppressWarnings("rawtypes")
	@GlueMethod(returns = "series", description = "Find elements in the series that match the lambda expression", version = 2)
	public static CollectionIterable filter(final Lambda lambda, Object...objects) {
		final Iterable<?> series = GlueUtils.toSeries(objects);
		final ScriptRuntime runtime = ScriptRuntime.getRuntime().fork(true);
		return new CollectionIterable() {
			@Override
			public Iterator iterator() {
				return new Iterator() {
					private Iterator parent = series.iterator();
					private Object next = null;
					private boolean hasNext = false;
					private int index = 0;
					@Override
					public boolean hasNext() {
						if (!hasNext) {
							if (parent.hasNext()) {
								while (parent.hasNext() && !hasNext) {
									Object nextFromParent = parent.next();
									Boolean calculated = (Boolean) GlueUtils.calculate(lambda, runtime, Arrays.asList(nextFromParent, index++));
									if (calculated != null && calculated) {
										next = nextFromParent;
										hasNext = true;
									}
								}
								return hasNext;
							}
							else {
								return false;
							}
						}
						else {
							return true;
						}
					}
					@Override
					public Object next() {
						if (hasNext()) {
							hasNext = false;
							return next;
						}
						return null;
					}
				};
			}
		};
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@GlueMethod(returns = "series", description = "This method reverses a series", version = 2)
	public static List<?> reverse(Object...objects) {
		Iterable<?> series = GlueUtils.toSeries(objects);
		List list = new ArrayList();
		if (series instanceof Collection) {
			list.addAll((Collection) series);
		}
		else {
			for (Object object : series) {
				list.add(object);
			}
		}
		Collections.reverse(list);
		return list;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@GlueMethod(returns = "series", description = "This method resolves a potentially lazy series", version = 2)
	public static List<?> resolve(Iterable<?> iterable) {
		if (iterable == null) {
			return null;
		}
		List<Object> objects = new ArrayList<Object>();
		ForkJoinPool pool = null;
		boolean sandboxed = "true".equals(ScriptRuntime.getRuntime().getExecutionContext().getExecutionEnvironment().getParameters().get("sandboxed"));
		long counter = 0;
		for (final Object single : iterable) {
			if (single instanceof Callable) {
				if (!sandboxed && GlueUtils.useParallelism()) {
					if (pool == null) {
						pool = new ForkJoinPool();
					}
					final ScriptRuntime runtime = ScriptRuntime.getRuntime().fork(true);
					objects.add(pool.submit(new Callable() {
						@Override
						public Object call() throws Exception {
							runtime.registerInThread();
							try {
								return ((Callable) single).call();
							}
							finally {
								runtime.unregisterInThread();
							}
						}
					}));
				}
				else {
					try {
						objects.add(((Callable) single).call());
					}
					catch (Exception e) {
						throw new RuntimeException("An error occurred when executing the task", e);
					}		
				}
			}
			else {
				objects.add(single);
			}
			counter++;
			if (sandboxed && counter > 1000) {
				break;
			}
		}
		if (pool != null) {
			pool.shutdown();
			try {
				pool.awaitTermination(365, TimeUnit.DAYS);
			}
			catch (InterruptedException e) {
				throw new RuntimeException("Could not finish computation", e);
			}
			for (int i = 0; i < objects.size(); i++) {
				if (objects.get(i) instanceof ForkJoinTask) {
					try {
						objects.set(i, ((ForkJoinTask) objects.get(i)).get());
					}
					catch (Exception e) {
						throw new RuntimeException("Could not compile results", e);
					}
				}
			}
		}
		return objects;
	}
	
	@SuppressWarnings("rawtypes")
	@GlueMethod(description = "Merges the given series into a single series", version = 2)
	public static CollectionIterable merge(final Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		return new CollectionIterable() {
			@Override
			public Iterator iterator() {
				return new Iterator() {
					private List<Iterator> iterators = new ArrayList<Iterator>(); {
						for (Object iterable : original) {
							if (!(iterable instanceof Iterable)) {
								iterable = Arrays.asList(iterable);
							}
							iterators.add(((Iterable) iterable).iterator());
						}
					}
					private int current = 0;
					@Override
					public boolean hasNext() {
						while(current < iterators.size() && !iterators.get(current).hasNext()) {
							current++;
						}
						return current < iterators.size();
					}
					@Override
					public Object next() {
						return hasNext() ? iterators.get(current).next() : null;
					}
				};
			}
		};
	}
	
	@GlueMethod(description = "Gets the amount of dimensions for a given series", version = 2)
	@SuppressWarnings("rawtypes")
	public static Integer depth(Iterable series) {
		int dimensions = 0;
		while (series != null) {
			dimensions++;
			Iterator iterator = series.iterator();
			if (!iterator.hasNext()) {
				break;
			}
			Object next = iterator.next();
			if (next instanceof Iterable) {
				series = (Iterable<?>) next;
			}
			else {
				break;
			}
		}
		return dimensions;
	}
	
	@GlueMethod(description = "Gets the dimensions of a given series", version = 2)
	@SuppressWarnings("rawtypes")
	public static List<Integer> dimensions(Iterable series) throws Exception {
		List<Integer> shape = new ArrayList<Integer>();
		while (series != null) {
			Iterator iterator = series.iterator();
			series = null;
			int size = 0;
			while (iterator.hasNext()) {
				Object next = iterator.next();
				if (next instanceof Callable) {
					next = ((Callable) next).call();
				}
				if (size == 0 && next instanceof Iterable) {
					series = (Iterable) next;
				}
				size++;
			}
			shape.add(size);
		}
		return shape;
	}
	
//	private static List<ValueImpl> preprocess(List<Integer> shape, ValueImpl...values) {
//		List<ValueImpl> processed = new ArrayList<ValueImpl>();
//		for (ValueImpl value : values) {
//			value.getColumn()
//		}
//	}
//	
//	public static Object mutate(Iterable series, ValueImpl...values) {
//		List<Integer> shape = shape(series);
//		List<ValueImpl> processed = preprocess(shape, values);
//		for (ValueImpl value : processed) {
//			if (value.row != null) {
//				
//			}
//		}
//	}

	@GlueMethod(description = "Gets the last entry in a series", version = 2)
	public static Object last(Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		Iterable<?> series = GlueUtils.toSeries(original);
		Object result = null;
		for (Object object : series) {
			result = object;
		}
		return GlueUtils.resolveSingle(result);
	}
	
	@GlueMethod(description = "Gets the first entry in a series", version = 2)
	public static Object first(Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		Iterable<?> series = GlueUtils.toSeries(original);
		Iterator<?> iterator = series.iterator();
		return iterator.hasNext() ? GlueUtils.resolveSingle(iterator.next()) : null;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@GlueMethod(description = "Creates a derived series based on the given one(s)", version = 2)
	public static CollectionIterable<?> derive(@GlueParam(name = "lambda") final Lambda lambda, @GlueParam(name = "content") Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		final List iterables = new ArrayList<Iterable<?>>();
		if (original[0] instanceof Iterable) {
			iterables.addAll(Arrays.asList(original));
		}
		else {
			iterables.add(series(original));
		}
		if (lambda.getDescription().getParameters().size() > iterables.size() || (iterables.size() > lambda.getDescription().getParameters().size() && !lambda.getDescription().getParameters().get(lambda.getDescription().getParameters().size() - 1).isList())) {
			throw new IllegalArgumentException("The lambda does not have enough parameters to process the series: expecting " + iterables.size() + " input parameters, has " + lambda.getDescription().getParameters().size());
		}
		final ScriptRuntime runtime = ScriptRuntime.getRuntime().fork(true);
		return new CollectionIterable() {
			@Override
			public Iterator iterator() {
				return new Iterator() {
					private List<Iterator> iterators = new ArrayList<Iterator>(); {
						for (Object iterable : iterables) {
							iterators.add(((Iterable) iterable).iterator());
						}
					}
					@Override
					public boolean hasNext() {
						if (iterators.isEmpty()) {
							return false;
						}
						for (Iterator iterator : iterators) {
							if (!iterator.hasNext()) {
								return false;
							}
						}
						return true;
					}
					@Override
					public Object next() {
						if (hasNext()) {
							final List parameters = new ArrayList();
							for (Iterator iterator : iterators) {
								parameters.add(iterator.next());
							}
							final ScriptRuntime fork = runtime.fork(true);
							return new Callable() {
								@Override
								public Object call() throws Exception {
									return GlueUtils.calculate(lambda, fork, parameters);
								}
							};
						}
						else {
							return null;
						}
					}
					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
			@Override
			public String toString() {
				List<Object> list = new ArrayList<Object>();
				Iterator iterator = iterator();
				while (iterator.hasNext()) {
					Object next = iterator.next();
					try {
						list.add(next instanceof Callable ? ((Callable) next).call() : next);
					}
					catch (Exception e) {
						list.add(e);
					}
				}
				return list.toString();
			}
		};
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static CollectionIterable<?> offsetFromBack(final long offset, final Iterable<?> iterable) {
		return new CollectionIterable() {
			@Override
			public Iterator iterator() {
				return new Iterator() {
					private Iterator parent = iterable.iterator();
					private ArrayDeque queue = new ArrayDeque();
					@Override
					public boolean hasNext() {
						while (queue.size() < offset && parent.hasNext()) {
							queue.add(parent.next());
						}
						return parent.hasNext();
					}
					@Override
					public Object next() {
						return hasNext() ? queue.poll() : null;
					}
				};
			}
		};
	}
	
	@SuppressWarnings("rawtypes")
	@GlueMethod(description = "Adds an offset to the series", version = 2)
	public static Iterable<?> offset(@GlueParam(name = "limit") final long offset, @GlueParam(name = "content") Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		final Iterable<?> iterable = GlueUtils.toSeries(original);
		if (offset  == 0) {
			return iterable;
		}
		if (iterable instanceof List) {
			List list = (List) iterable;
			return offset < 0
				? list.subList(0, (int) (list.size() + offset))
				: list.subList((int) Math.min(offset, list.size() - 1), list.size());
		}
		else if (offset < 0) {
			return offsetFromBack(Math.abs(offset), iterable);
		}
		else {
			return new CollectionIterable() {
				@Override
				public Iterator iterator() {
					return new Iterator() {
						private Iterator parent = iterable.iterator(); {
							for (long i = 0; i < offset; i++) {
								if (parent.hasNext()) {
									parent.next();
								}
								else {
									throw new IllegalArgumentException("Can not skip to offset " + offset);
								}
							}
						}
						@Override
						public boolean hasNext() {
							return parent.hasNext();
						}
						@Override
						public Object next() {
							return parent.next();
						}
						@Override
						public void remove() {
							throw new UnsupportedOperationException();
						}
					};
				}
			};
		}
	}
	
	@SuppressWarnings("rawtypes")
	@GlueMethod(description = "Limits the series to a certain amount", version = 2)
	public static Iterable<?> limit(@GlueParam(name = "limit") final long limit, @GlueParam(name = "content") Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		final Iterable<?> iterable = GlueUtils.toSeries(original);
		if (iterable instanceof List) {
			List list = (List) iterable;
			return list.subList(0, (int) Math.min(limit, list.size()));
		}
		else {
			return new CollectionIterable() {
				@Override
				public Iterator iterator() {
					return new Iterator() {
						private Iterator parent = iterable.iterator();
						private long index;
						@Override
						public boolean hasNext() {
							return index < limit && parent.hasNext();
						}
						@Override
						public Object next() {
							return index++ < limit ? parent.next() : null;
						}
						@Override
						public void remove() {
							throw new UnsupportedOperationException();
						}
					};
				}
			};
		}
	}
	
	@SuppressWarnings("rawtypes")
	@GlueMethod(description = "Stops a series when a certain condition is met", version = 2)
	public static CollectionIterable<?> to(final Lambda lambda, Object...original) {
		final Iterable<?> iterable = GlueUtils.toSeries(original);
		final ScriptRuntime runtime = ScriptRuntime.getRuntime().fork(true);
		return new CollectionIterable() {
			@Override
			public Iterator iterator() {
				return new Iterator() {

					private Object next;
					private boolean isDone, hasNext;
					private Iterator parent = iterable.iterator();
					
					@SuppressWarnings("unchecked")
					@Override
					public boolean hasNext() {
						if (!isDone && !hasNext && parent.hasNext()) {
							Object parentNext = parent.next();
							List parameters = new ArrayList();
							parameters.add(parentNext);
							Boolean accepted = (Boolean) GlueUtils.calculate(lambda, runtime, parameters);
							if (accepted) {
								isDone = true;
								hasNext = false;
							}
							else {
								next = parentNext;
								hasNext = true;
							}
						}
						return hasNext;
					}

					@Override
					public Object next() {
						if (hasNext()) {
							hasNext = false;
							return next;
						}
						return null;
					}
					
				};
			}
		};
	}
	
	@SuppressWarnings("rawtypes")
	@GlueMethod(description = "Starts a series when a certain condition is met", version = 2)
	public static CollectionIterable<?> from(final Lambda lambda, Object...original) {
		final ScriptRuntime runtime = ScriptRuntime.getRuntime().fork(true);
		final Iterable<?> iterable = GlueUtils.toSeries(original);
		return new CollectionIterable() {
			@Override
			public Iterator iterator() {
				return new Iterator() {

					private boolean isActive, isFirst;
					private Object first;
					private Iterator parent = iterable.iterator();
					
					@SuppressWarnings("unchecked")
					@Override
					public boolean hasNext() {
						while(!isActive && parent.hasNext()) {
							Object parentNext = parent.next();
							List parameters = new ArrayList();
							parameters.add(parentNext);
							Boolean accepted = (Boolean) GlueUtils.calculate(lambda, runtime, parameters);
							if (accepted) {
								isActive = true;
								isFirst = true;
								first = parentNext;
							}
						}
						return isActive;
					}

					@Override
					public Object next() {
						if (hasNext()) {
							if (isFirst) {
								isFirst = false;
								return first;
							}
							else {
								return parent.next();
							}
						}
						return null;
					}
					
				};
			}
		};
	}
	
	@GlueMethod(description = "Unwraps a series to pass along as arguments", version = 2)
	public static Object unwrap(Object...original) {
		List<?> resolve = resolve(GlueUtils.toSeries(original));
		return resolve.toArray();
	}
	
	@SuppressWarnings("rawtypes")
	@GlueMethod(description = "Explodes each argument in the series into a multiple new arguments for the new series", version = 2)
	public static CollectionIterable explode(final Lambda lambda, Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		final Iterable<?> series = GlueUtils.toSeries(original);
		final ScriptRuntime runtime = ScriptRuntime.getRuntime().fork(true);
		return new CollectionIterable() {
			@Override
			public Iterator iterator() {
				return new Iterator() {
					private Iterator iterator = series.iterator();
					// started with an arraydeque but it does not allow for null values
					private Stack queue = new Stack();
					private List<Object> history = new ArrayList<Object>(); {
						// add a history of nulls so the next is fitted in the last slot
						for (int i = 0; i < lambda.getDescription().getParameters().size() - 1; i++) {
							history.add(null);
						}
					}
					@SuppressWarnings("unchecked")
					@Override
					public boolean hasNext() {
						while(queue.isEmpty() && iterator.hasNext()) {
							Object next = iterator.next();
							history.add(next);
							List<Object> parameterList = new ArrayList(history);
							Object calculate = GlueUtils.calculate(lambda, runtime, parameterList);
							if (calculate != null) {
								if (!(calculate instanceof Iterable)) {
									calculate = Arrays.asList(calculate);
								}
								for (Object single : (Iterable) calculate) {
									queue.add(single);
								}
							}
							// always remove the first item, we have pre-filled the array with enough elements
							history.remove(0);
						}
						return !queue.isEmpty();
					}
					@Override
					public Object next() {
						if (hasNext()) {
							return queue.remove(0);
						}
						else {
							return null;
						}
					}
					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}
	
	@GlueMethod(description = "Creates a dimensional value that can be used to manipulate series", version = 2)
	public static Object value(@GlueParam(name = "value") Object value, @GlueParam(name = "row") Long row, @GlueParam(name = "column") Long column, @GlueParam(name = "page") Long...pages) {
		return new ValueImpl(value, row, column, pages);
	}
	
	public static class ValueImpl {
		private Object value;
		private Long row, column;
		private Long [] pages;
		public ValueImpl() {
			// auto construct
		}
		public ValueImpl(Object value, Long row, Long column, Long...pages) {
			this.value = value;
			this.row = row;
			this.column = column;
			this.pages = pages;
		}
		public Object getValue() {
			return value;
		}
		public void setValue(Object value) {
			this.value = value;
		}
		public Long getRow() {
			return row;
		}
		public void setRow(Long row) {
			this.row = row;
		}
		public Long getColumn() {
			return column;
		}
		public void setColumn(Long column) {
			this.column = column;
		}
		public Long[] getPages() {
			return pages;
		}
		public void setPages(Long[] pages) {
			this.pages = pages;
		}
	}
	
	@GlueMethod(version = 2)
	public static Iterable<?> aggregate(final Lambda lambda, Object...objects) {
		final Iterable<?> series = GlueUtils.toSeries(objects);
		LambdaSeriesGenerator generator = new LambdaSeriesGenerator(lambda, series);
		return generator.newSeries();
	}
	
	@GlueMethod(version = 2)
	public static Iterable<?> generate(@GlueParam(name = "series") Object series) {
		SeriesGenerator<?> generator;
		if (series instanceof Number) {
			generator = new LongGenerator(((Number) series).longValue());
		}
		else if (series instanceof String) {
			generator = new StringGenerator((String) series);
		}
		else if (series instanceof Lambda) {
			generator = new LambdaSeriesGenerator((Lambda) series);
		}
		else if (series instanceof Object[]) {
			return new ArrayList<Object>(Arrays.asList((Object[]) series));
		}
		else {
			throw new IllegalArgumentException("Can not unfold into a series");
		}
		return generator.newSeries();
	}
	
}
