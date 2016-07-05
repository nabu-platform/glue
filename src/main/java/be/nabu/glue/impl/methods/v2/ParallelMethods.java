package be.nabu.glue.impl.methods.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.api.Lambda;
import be.nabu.glue.impl.GlueUtils;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

@MethodProviderClass(namespace = "parallel")
public class ParallelMethods {
	
	private static ForkJoinPool pool = new ForkJoinPool();
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@GlueMethod(description = "Runs the given lambda asynchronously. Any additional parameters are given to the lambda, if they are a future they are resolved first", version = 2)
	public static Future run(final Lambda lambda, Object...objects) {
		final List<?> resolved = SeriesMethods.resolve(GlueUtils.toSeries(objects));
		final ScriptRuntime runtime = ScriptRuntime.getRuntime();
		Callable callable = new Callable() {
			@Override
			public Object call() throws Exception {
				runtime.fork(false).registerInThread();
				List parameters = new ArrayList();
				for (Object resolve : resolved) {
					if (resolve instanceof Future) {
						parameters.add(((Future) resolve).get());
					}
					else {
						parameters.add(resolve);
					}
				}
				return GlueUtils.calculate(lambda, runtime, parameters);
			}
		};
		return pool.submit(callable);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@GlueMethod(description = "Waits for the given futures to end and returns the result", version = 2)
	public static Object wait(Object...objects) throws InterruptedException, ExecutionException {
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

}
