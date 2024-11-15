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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.core.api.MethodProvider;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.impl.SimpleParameterDescription;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.base.BaseMethodOperation;

public class ControlMethodProvider implements MethodProvider {

	@Override
	public Operation<ExecutionContext> resolve(String name) {
		if ("control.when".equals(name) || "when".equals(name)) {
			return new WhenOperation();
		}
		if ("control.all".equals(name) || "all".equals(name)) {
			return new AllOperation();
		}
		return null;
	}

	@Override
	public List<MethodDescription> getAvailableMethods() {
		List<MethodDescription> descriptions = new ArrayList<MethodDescription>();
		descriptions.add(new SimpleMethodDescription("control", "when", "This will conditionally execute the second or third argument depending on the result of the first argument: when(test, then, else)",
			Arrays.asList(new ParameterDescription [] { 
				new SimpleParameterDescription("test", "A test to see which one of the other arguments has to be executed", "boolean"),
				new SimpleParameterDescription("then", "If the test returns 'true', this part is executed", "object"),
				new SimpleParameterDescription("else", "Otherwise this part is executed", "object")
			}),
			new ArrayList<ParameterDescription>()));
		return descriptions;
	}
	
	private static class WhenOperation extends BaseMethodOperation<ExecutionContext> {

		@Override
		public void finish() throws ParseException {
			// do nothing
		}

		@SuppressWarnings("unchecked")
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			Object resolve = resolve((Operation<ExecutionContext>) getParts().get(1).getContent(), context);
			if (!(resolve instanceof Boolean)) {
				resolve = ConverterFactory.getInstance().getConverter().convert(resolve, Boolean.class);
			}
			Boolean result = (Boolean) resolve;
			if (result != null && result) {
				return resolve((Operation<ExecutionContext>) getParts().get(2).getContent(), context);
			}
			else {
				return getParts().size() > 3 ? resolve((Operation<ExecutionContext>) getParts().get(3).getContent(), context) : null;
			}
		}
		
		@SuppressWarnings({ "unchecked", "rawtypes" })
		private Object resolve(Object object, ExecutionContext context) throws EvaluationException {
			if (object instanceof Operation) {
				return ((Operation) object).evaluate(context);
			}
			else {
				return object;
			}
		}
	}

	private static class AllOperation extends BaseMethodOperation<ExecutionContext> {

		@Override
		public void finish() throws ParseException {
			// do nothing
		}

		@SuppressWarnings("unchecked")
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			List<Object> results = new ArrayList<Object>();
			// the first part is the method itself
			boolean first = true;
			for (QueryPart part : getParts()) {
				if (first) {
					first = false;
				}
				else {
					results.add(((Operation<ExecutionContext>) part.getContent()).evaluate(context));
				}
			}
			return results;
		}
		
	}
}
