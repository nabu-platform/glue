package be.nabu.glue.core.impl.operations;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;

import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.impl.SimpleExecutionEnvironment;
import be.nabu.glue.utils.ScriptRuntime;

public class LambdaProxy {
	
	@SuppressWarnings("unchecked")
	public static <T> T newInstance(Class<T> target, Lambda lambda) {
		return (T) Proxy.newProxyInstance(target.getClassLoader(), new Class[] { target }, new LambdaInvocationHandler(lambda));
	}
	
	public static class LambdaInvocationHandler implements InvocationHandler {
		private Lambda[] lambdas;
		
		public LambdaInvocationHandler(Lambda...lambdas) {
			this.lambdas = lambdas;
		}
		
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			for (Lambda lambda : lambdas) {
				if (lambda.getDescription().getParameters().size() == method.getParameterCount()) {
					ScriptRuntime runtime = new ScriptRuntime(null, new SimpleExecutionEnvironment("java"), false, new HashMap<String, Object>());
					return GlueUtils.calculate(lambda, runtime, Arrays.asList(args));
				}
			}
			// if we don't implement the tostring, it will return null, which is very confusing when outputting debug information :P
			if (method.getName().equals("toString") && method.getParameterCount() == 0) {
				return lambdas[0].toString();
			}
			return null;
		}
	}
}
