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

package be.nabu.glue.core.impl.operations;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.impl.GlueUtils;
import be.nabu.glue.core.impl.LambdaImpl;
import be.nabu.glue.core.impl.methods.v2.SeriesMethods;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.impl.SimpleParameterDescription;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.MultipleContextAccessor;
import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.base.BaseMethodOperation;
import be.nabu.libs.evaluator.impl.VariableOperation;

public class ScriptVariableOperation<T> extends VariableOperation<T> {

	private ContextAccessor<T> accessor = null;
	
	@Override
	public Object evaluate(T context) throws EvaluationException {
		Object value = super.evaluate(context);
		// arraylists are used to create custom result sets
		// convert these to arrays for integration purposes
		// tuples don't use arraylist
		if (value instanceof ArrayList && GlueUtils.getVersion().contains(1.0)) {
			value = ((Collection<?>) value).toArray();
		}
		return value;
	}

	@Override
	@SuppressWarnings("unchecked")
	public ContextAccessor<T> getAccessor() {
		if (accessor == null) {
			accessor = (ContextAccessor<T>) new LambdaMultipleAccessor((MultipleContextAccessor) ContextAccessorFactory.getInstance().getAccessor());
		}
		return accessor;
	}

	@Override
	public void setAccessor(ContextAccessor<T> accessor) {
		this.accessor = accessor;
	}
	
	public static class LambdaMultipleAccessor extends MultipleContextAccessor {

		private ContextAccessor<?> parent;

		public LambdaMultipleAccessor(MultipleContextAccessor parent) {
			super(parent.getAccessors());
		}

		@SuppressWarnings("unchecked")
		@Override
		public ContextAccessor<?> getAccessor(Object object) {
			ContextAccessor<?> accessor = super.getAccessor(object);
			if (accessor.getContextType().equals(Object.class)) {
				return new LambdaJavaAccessor((ContextAccessor<Object>) accessor);
			}
			return accessor;
		}

		public ContextAccessor<?> getParent() {
			return parent;
		}
	}
	
	public static class LambdaJavaAccessor implements ContextAccessor<Object> {
		
		private ContextAccessor<Object> parent;

		public LambdaJavaAccessor(ContextAccessor<Object> parent) {
			this.parent = parent;
		}
		
		@SuppressWarnings("rawtypes")
		private static Map<Class, Map<String, Lambda>> methods = new HashMap<Class, Map<String, Lambda>>();
		
		@Override
		public boolean has(Object context, String name) throws EvaluationException {
			if (context == null) {
				return false;
			}
			if (parent == null || !parent.has(context, name)) {
				Map<String, Lambda> methods = getMethods(context.getClass());
				if (methods.containsKey(name)) {
					return true;
				}
			}
			return true;
		}
		
		private Map<String, Lambda> getMethods(Class<?> clazz) {
			if (!methods.containsKey(clazz)) {
				synchronized(methods) {
					if (!methods.containsKey(clazz)) {
						Map<String, Lambda> classMethods = new HashMap<String, Lambda>();
						// we want to know which methods are not uniquely named but are overloaded
						// we want to generate additional aliases for them
						// for example ArrayList.add, it has a version with only the thing to add and one with an index
						// it is _random_ which order they appear in (or may just depend on java version etc) so we can't actually know which version we will get when we just ask for the "add" method
						// assuming the overload is only on amount of parameters and not types, we want to inject additional aliases for those overloaded methods that can be used to get the correct one
						// like add(1) and add(2)
						// if we ever want to support overloads with types we might need additional aliases like add(int,string)
						Map<String, Integer> methodCounts = new HashMap<String, Integer>();
						for (Method method : clazz.getMethods()) {
							if (!methodCounts.containsKey(method.getName())) {
								methodCounts.put(method.getName(), 1);
							}
							else {
								methodCounts.put(method.getName(), methodCounts.get(method.getName()) + 1);
							}
						}
						for (Method method : clazz.getMethods()) {
							classMethods.put(method.getName(), toLambda(method));
							if (methodCounts.get(method.getName()) > 1) {
								classMethods.put(method.getName() + "(" + method.getParameterCount() + ")", toLambda(method));
							}
						}
						methods.put(clazz, classMethods);
					}
				}
			}
			return methods.get(clazz);
		}

		private Lambda toLambda(Method method) {
			List<ParameterDescription> inputs = new ArrayList<ParameterDescription>();
			Parameter[] parameters = method.getParameters();
			Annotation[][] parameterAnnotations = method.getParameterAnnotations();
			Class<?>[] parameterTypes = method.getParameterTypes();
			for (int i = 0; i < parameters.length; i++) {
				String name = parameters[i].getName();
				String description = null;
				for (int j = 0; j < parameterAnnotations[i].length; j++) {
					if (parameterAnnotations[i][j] instanceof GlueParam) {
						String setName = ((GlueParam) parameterAnnotations[i][j]).name();
						if (setName != null && !setName.isEmpty()) {
							name = setName;
						}
						description = ((GlueParam) parameterAnnotations[i][j]).description();
					}
				}
				inputs.add(new SimpleParameterDescription(name, description == null || description.isEmpty() ? null : description, parameterTypes[i].getName(), parameters[i].isVarArgs()));
			}
			MethodDescription description = new SimpleMethodDescription(method.getDeclaringClass().getName(), method.getName(), null, inputs, 
				Arrays.asList(new ParameterDescription[] { new SimpleParameterDescription("result", null, method.getReturnType().getName()) }));
			return new LambdaImpl(description, new LambdaMethodOperation(method, description), new HashMap<String, Object>());
		}
		
		@Override
		public Object get(Object context, String name) throws EvaluationException {
			if (parent != null && parent.has(context, name)) {
				return parent.get(context, name);	
			}
			else {
				Lambda lambda = getMethods(context.getClass()).get(name);
				if (lambda != null) {
					Map<String, Object> lambdaContext = new HashMap<String, Object>();
					lambdaContext.put("$instance", context);
					return new LambdaImpl(lambda.getDescription(), lambda.getOperation(), lambdaContext);
				}
			}
			return null;
		}

		@Override
		public Class<Object> getContextType() {
			return Object.class;
		}
	}
	
	public static class LambdaMethodOperation extends BaseMethodOperation<ExecutionContext> {

		private Method method;
		private MethodDescription description;

		public LambdaMethodOperation(Method method, MethodDescription description) {
			this.method = method;
			this.description = description;
		}

		@Override
		public void finish() throws ParseException {
			// do nothing
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			Object instance = context.getPipeline().get("$instance");
			List parameters = new ArrayList();
			Converter converter = ConverterFactory.getInstance().getConverter();
			int i = 0;
			for (ParameterDescription parameter : description.getParameters()) {
				Object e = context.getPipeline().get(parameter.getName());
				if (parameter.isVarargs() && e instanceof Iterable) {
					e = SeriesMethods.resolve((Iterable) e);
					// convert to the correct type!
					List result = new ArrayList();
					for (Object single : (Collection) e) {
						result.add(converter.convert(single, method.getParameters()[method.getParameterCount() - 1].getType().getComponentType()));
					}
					e = result;
					Object newInstance = Array.newInstance(method.getParameters()[method.getParameterCount() - 1].getType().getComponentType(), ((Collection) e).size());
					parameters.add(((Collection) e).toArray((Object[]) newInstance));
				}
				else {
					// if you passed in a lambda, we likely want to convert it to an interface, check if we can proxy it
					if (e instanceof Lambda && method.getParameters()[i].getType().isInterface()) {
						parameters.add(LambdaProxy.newInstance(method.getParameters()[i].getType(), (Lambda) e));
					}
					else {
						parameters.add(converter.convert(e, method.getParameters()[i].getType()));
					}
				}
				i++;
			}
			try {
				return method.invoke(instance, parameters.toArray());
			}
			catch (Exception e) {
				throw new EvaluationException(e);
			}
		}
		
	}
}
