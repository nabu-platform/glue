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
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.api.ContextAccessor;

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
		
		@SuppressWarnings("rawtypes")
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			for (Lambda lambda : lambdas) {
				if (lambda.getDescription().getParameters().size() == method.getParameterCount()) {
					ScriptRuntime runtime = new ScriptRuntime(null, new SimpleExecutionEnvironment("java"), false, new HashMap<String, Object>());
					Object result = GlueUtils.calculate(lambda, runtime, Arrays.asList(args));
					if (result != null && !method.getReturnType().isAssignableFrom(result.getClass())) {
						// check if we have a good conversion path
						Object converted = ConverterFactory.getInstance().getConverter().convert(result, method.getReturnType());
						if (converted == null) {
							// if we don't, check that we have a context accessor and the return value is a (bean-compatible) interface
							if (method.getReturnType().isInterface()) {
								ContextAccessor accessor = ContextAccessorFactory.getInstance().getAccessor(result.getClass());
								if (accessor != null) {
									return newBeanInstance(method.getReturnType(), accessor, result);
								}
								else {
									throw new IllegalArgumentException("Can not convert lambda output to: " + method.getReturnType());
								}
							}
						}
						else {
							result = converted;
						}
					}
					return result;
				}
			}
			// if we don't implement the tostring, it will return null, which is very confusing when outputting debug information :P
			if (method.getName().equals("toString") && method.getParameterCount() == 0) {
				return lambdas[0].toString();
			}
			return null;
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> T newBeanInstance(Class<T> target, ContextAccessor accessor, Object object) {
		return (T) Proxy.newProxyInstance(target.getClassLoader(), new Class[] { target }, new BeanInvocationHandler(accessor, object));
	}
	
	@SuppressWarnings("rawtypes")
	public static class BeanInvocationHandler implements InvocationHandler {
		private ContextAccessor accessor;
		private Object object;
		
		public BeanInvocationHandler(ContextAccessor accessor, Object object) {
			this.accessor = accessor;
			this.object = object;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			// a getter!
			if (method.getName().startsWith("get") && method.getParameterCount() == 0) {
				String name = method.getName().substring("get".length());
				if (!name.isEmpty()) {
					name = name.substring(0, 1).toLowerCase() + name.substring(1);
					Object result = accessor.get(object, name);
					if (result != null && !method.getReturnType().isAssignableFrom(result.getClass())) {
						Object converted = ConverterFactory.getInstance().getConverter().convert(result, method.getReturnType());
						if (converted == null) {
							throw new IllegalArgumentException("Can not convert lambda output to: " + method.getReturnType());
						}
						result = converted;
					}
					return result;
				}
			}
			if (method.getName().equals("toString") && method.getParameterCount() == 0) {
				return "Dynamic Bean Accessor for: " + object;
			}
			return null;
		}
		
	}
}
