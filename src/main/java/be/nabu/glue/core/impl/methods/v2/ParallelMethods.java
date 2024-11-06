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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "parallel")
public class ParallelMethods {
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static final class CallableImpl implements Callable {
		private final ScriptRuntime runtime;
		private final List<?> resolved;
		private final Lambda lambda;
		private ScriptRuntime fork;

		private CallableImpl(ScriptRuntime runtime, List<?> resolved, Lambda lambda) {
			this.runtime = runtime;
			this.resolved = resolved;
			this.lambda = lambda;
		}
		@Override
		public Object call() throws Exception {
			fork = runtime.fork(false);
			fork.registerInThread();
			try {
				List parameters = new ArrayList();
				for (Object resolve : resolved) {
					if (resolve instanceof Future) {
						parameters.add(((Future) resolve).get());
					}
					else {
						parameters.add(resolve);
					}
				}
				return GlueUtils.calculate(lambda, fork, parameters);
			}
			finally {
				fork.unregisterInThread();
			}
		}
		
		public void abort() {
			if (fork != null) {
				fork.abort(true);
			}
		}
	}

	private static ForkJoinPool pool = new ForkJoinPool();
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@GlueMethod(description = "Runs the given lambda asynchronously. Any additional parameters are given to the lambda, if they are a future they are resolved first", version = 2, restricted = true)
	public static Future run(final Lambda lambda, Object...objects) {
		final List<?> resolved = SeriesMethods.resolve(GlueUtils.toSeries(objects));
		final ScriptRuntime runtime = ScriptRuntime.getRuntime().fork(true);
		final CallableImpl callable = new CallableImpl(runtime, resolved, lambda);
		final ForkJoinTask submit = pool.submit(callable);
		return new Future() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				if (mayInterruptIfRunning) {
					callable.abort();
				}
				return submit.cancel(mayInterruptIfRunning);
			}
			@Override
			public boolean isCancelled() {
				return submit.isCancelled();
			}
			@Override
			public boolean isDone() {
				return submit.isDone();
			}
			@Override
			public Object get() throws InterruptedException, ExecutionException {
				return submit.get();
			}
			@Override
			public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
				return submit.get(timeout, unit);
			}
		};
	}
	
	/**
	 * You can wait until a specific condition returns something other than null or false
	 * This means you can never send back "false" in an until, but it outweighs the ease of use when thinking of it as a filter lambda
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Future until(final Lambda lambda, final @GlueParam(name = "interval") Long interval, final @GlueParam(name = "timeout") Long duration, @GlueParam(name = "parameters") Object...objects) {
		final List<?> resolved = SeriesMethods.resolve(GlueUtils.toSeries(objects));
		final ScriptRuntime runtime = ScriptRuntime.getRuntime().fork(true);
		final CallableImpl callable = new CallableImpl(runtime, resolved, lambda);
		final Date started = new Date();
		final ForkJoinTask submit = pool.submit(callable);
		return new Future() {
			// start with the original submit
			private Future lastFuture = submit;
			private Object result;
			private CallableImpl lastCallable = callable;
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				if (mayInterruptIfRunning) {
					lastCallable.abort();
				}
				return lastFuture.cancel(mayInterruptIfRunning);
			}
			@Override
			public boolean isCancelled() {
				return lastFuture.isCancelled();
			}
			@Override
			public boolean isDone() {
				return lastFuture.isDone();
			}
			private long getRemainder() {
				if (duration != null) {
					long passed = new Date().getTime() - started.getTime();
					return passed > duration ? 0 : duration - passed;
				}
				return -1l;
			}
			@Override
			public Object get() throws InterruptedException, ExecutionException {
				// wait a year by default!
				try {
					return get(365, TimeUnit.DAYS);
				}
				catch (TimeoutException e) {
					throw new ExecutionException(e);
				}
			}
			@Override
			public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
				while (result == null) {
					long remainder = getRemainder();
					long millis = unit.toMillis(timeout);
					if (remainder == 0) {
						throw new TimeoutException();
					}
					else if (remainder >= 0 && remainder < millis) {
						millis = remainder;
					}
					result = lastFuture.get(millis, TimeUnit.MILLISECONDS);
					if (result instanceof Boolean && !(Boolean) result) {
						result = null;
					}
					if (result == null) {
						if (interval != null && interval > 0) {
							Thread.sleep(interval);
						}
						// if we have a remainder (so _some_ timeout)
						if (remainder > 0) {
							// check how much we have remaining
							remainder = getRemainder();
							// if we have none remaining, we shouldn't start a new cycle
							if (remainder == 0) {
								throw new TimeoutException();
							}
						}
						lastCallable = new CallableImpl(runtime, resolved, lambda);
						lastFuture = pool.submit(lastCallable);
					}
				}
				return result;
			}
		};
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@GlueMethod(description = "Waits for the given futures to end and returns the result", version = 2, restricted = true)
	public static Object wait(@GlueParam(name = "futures") Object...objects) throws InterruptedException, ExecutionException {
		List result = new ArrayList();
		Iterable<?> series = GlueUtils.toSeries(objects);
		for (Object object : series) {
			if (object instanceof Future) {
				result.add(((Future) object).get());
			}
			else {
				result.add(object);
			}
		}
		// if we are not waiting for anything specifically, wait for all
		if (result.isEmpty()) {
			pool.awaitQuiescence(365, TimeUnit.DAYS);
		}
		// if you did not pass in a series and it is of size one, just return the result
		return result.size() == 1 && !GlueUtils.isSeries(objects) ? result.get(0) : result;
	}

	@GlueMethod(description = "Aborts the given futures or the current script run", version = 2)
	public static List<Boolean> abort(Future<?>...futures) {
		if (futures == null || futures.length == 0) {
			ScriptRuntime.getRuntime().abort();
			return null;
		}
		else {
			List<Boolean> cancels = new ArrayList<Boolean>();
			for (Future<?> future : futures) {
				cancels.add(future.cancel(true));
			}
			return cancels;
		}
	}
	
	@GlueMethod(description = "Check if the current run is aborted", version = 2)
	public static boolean aborted() {
		return ScriptRuntime.getRuntime().isAborted();
	}
}
