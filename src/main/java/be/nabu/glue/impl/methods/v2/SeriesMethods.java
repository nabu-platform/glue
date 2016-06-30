package be.nabu.glue.impl.methods.v2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.api.EnclosedLambda;
import be.nabu.glue.api.Lambda;
import be.nabu.glue.impl.GlueUtils;
import be.nabu.glue.impl.LambdaMethodProvider.LambdaExecutionOperation;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "series")
public class SeriesMethods {
	
	@GlueMethod(returns = "series", description = "This method creates a series out of a number of objects", version = 2)
	public static Iterable<?> series(@GlueParam(name = "content", description = "The objects to put in the series") Object...objects) {
		return objects == null ? new ArrayList<Object>() : Arrays.asList(objects);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@GlueMethod(returns = "series", description = "This method reverses a series, note that the series will have to be resolved to reverse it", version = 2)
	public static List<?> reverse(Object...objects) {
		List<?> list = new ArrayList(resolve(GlueUtils.toSeries(objects)));
		Collections.reverse(list);
		return list;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@GlueMethod(returns = "series", description = "This method resolves a potentially lazy series", version = 2)
	public static List<?> resolve(Iterable<?> iterable) {
		if (iterable instanceof List) {
			return (List<?>) iterable;
		}
		else {
			List<Object> objects = new ArrayList<Object>();
			ForkJoinPool pool = null;
			final ScriptRuntime runtime = ScriptRuntime.getRuntime();
			for (final Object single : iterable) {
				if (single instanceof Callable) {
					if (GlueUtils.useParallelism()) {
						if (pool == null) {
							pool = new ForkJoinPool();
						}
						objects.add(pool.submit(new Callable() {
							@Override
							public Object call() throws Exception {
								runtime.fork(false).registerInThread();
								return ((Callable) single).call();
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
	}
	
	@GlueMethod(description = "Checks if a series is resolved", version = 2)
	public static boolean resolved(Iterable<?> iterable) {
		return iterable instanceof List;
	}
	
	@GlueMethod(description = "Gets the last entry in a series", version = 2)
	public static Object last(Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		List<?> series = resolve(GlueUtils.toSeries(original));
		return series.get(series.size() - 1);
	}
	
	@GlueMethod(description = "Gets the first entry in a series", version = 2)
	public static Object first(Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		Iterable<?> series = GlueUtils.toSeries(original);
		Iterator<?> iterator = series.iterator();
		return iterator.hasNext() ? iterator.next() : null;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@GlueMethod(description = "Folds the given series", version = 2)
	public static Iterable<?> fold(@GlueParam(name = "lambda") final Lambda lambda, @GlueParam(name = "content") Object...original) {
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
		if (lambda.getDescription().getParameters().size() != iterables.size()) {
			throw new IllegalArgumentException("The lambda does not have enough parameters to process the series: expecting " + iterables.size() + ", received " + lambda.getDescription().getParameters().size());
		}
		return new Iterable() {
			private Object calculate(ScriptRuntime runtime, List parameters) {
				LambdaExecutionOperation lambdaOperation = new LambdaExecutionOperation(lambda.getDescription(), lambda.getOperation(), 
					lambda instanceof EnclosedLambda ? ((EnclosedLambda) lambda).getEnclosedContext() : new HashMap<String, Object>());
				try {
					return lambdaOperation.evaluateWithParameters(runtime.getExecutionContext(), parameters.toArray());
				}
				catch (EvaluationException e) {
					throw new RuntimeException(e);
				}
			}
			
			@Override
			public Iterator iterator() {
				return new Iterator() {
					ScriptRuntime runtime;
					private List<Iterator> iterators = new ArrayList<Iterator>(); {
						for (Object iterable : iterables) {
							iterators.add(((Iterable) iterable).iterator());
						}
						runtime = ScriptRuntime.getRuntime();
					}
					@Override
					public boolean hasNext() {
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
							return new Callable() {
								@Override
								public Object call() throws Exception {
									return calculate(runtime, parameters);
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
		};
	}
	
	@SuppressWarnings("rawtypes")
	@GlueMethod(description = "Adds an offset to the series", version = 2)
	public static Iterable<?> offset(@GlueParam(name = "limit") final long offset, @GlueParam(name = "content") Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		final Iterable<?> iterable = GlueUtils.toSeries(original);
		if (resolved(iterable)) {
			List list = (List) iterable;
			return list.subList((int) Math.min(offset, list.size() - 1), list.size());
		}
		else {
			return new Iterable() {
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
	public static Iterable<?> limit(@GlueParam(name = "limit") final long limit, @GlueParam(name = "content") Object...original) {
		if (original == null || original.length == 0) {
			return null;
		}
		final Iterable<?> iterable = GlueUtils.toSeries(original);
		if (resolved(iterable)) {
			List list = (List) iterable;
			return list.subList(0, (int) Math.min(limit, list.size()));
		}
		else {
			return new Iterable() {
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
	
	@GlueMethod(version = 2)
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Map hash(Lambda lambda, Object...original) throws EvaluationException {
		if (original == null || original.length == 0) {
			return null;
		}
		final Iterable<?> iterable = GlueUtils.toSeries(original);
		Map map = new HashMap();
		if (lambda.getDescription().getParameters().size() != 1) {
			throw new IllegalArgumentException("The lambda does not have enough parameters to process the hash value");
		}
		for (Object object : iterable) {
			LambdaExecutionOperation lambdaOperation = new LambdaExecutionOperation(lambda.getDescription(), lambda.getOperation(), 
				lambda instanceof EnclosedLambda ? ((EnclosedLambda) lambda).getEnclosedContext() : new HashMap<String, Object>());
			Object key = lambdaOperation.evaluateWithParameters(ScriptRuntime.getRuntime().getExecutionContext(), object);
			for (Object single : key instanceof Object[] ? (Object[]) key : new Object[] { key }) {
				Object current = map.get(single);
				if (current instanceof List) {
					((List) current).add(object);
				}
				else if (current != null) {
					List list = new ArrayList();
					list.add(current);
					list.add(object);
					map.put(single, list);
				}
				else {
					map.put(single, object);
				}
			}
		}
		return map;
	}
}
