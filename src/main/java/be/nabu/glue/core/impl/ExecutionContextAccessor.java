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

import java.util.ArrayList;
import java.util.Collection;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.ListableContextAccessor;
import be.nabu.libs.evaluator.api.WritableContextAccessor;

public class ExecutionContextAccessor implements ListableContextAccessor<ExecutionContext>, WritableContextAccessor<ExecutionContext> {

	@Override
	public Class<ExecutionContext> getContextType() {
		return ExecutionContext.class;
	}

	@Override
	public boolean has(ExecutionContext context, String name) throws EvaluationException {
		return context.getPipeline().containsKey(name);
	}

	@Override
	public Object get(ExecutionContext context, String name) throws EvaluationException {
		return context.getPipeline().get(name);
	}

	@Override
	public Collection<String> list(ExecutionContext context) {
		return new ArrayList<String>(context.getPipeline().keySet());
	}

	@Override
	public void set(ExecutionContext context, String name, Object value) throws EvaluationException {
		context.getPipeline().put(name, value);
	}

}
