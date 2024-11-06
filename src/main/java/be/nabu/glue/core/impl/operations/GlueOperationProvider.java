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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.core.api.DynamicMethodOperationProvider;
import be.nabu.glue.core.api.MethodProvider;
import be.nabu.glue.core.impl.providers.DynamicMethodOperation;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.impl.ClassicOperation;
import be.nabu.libs.evaluator.impl.NativeOperation;

public class GlueOperationProvider implements DynamicMethodOperationProvider {
	
	private List<MethodProvider> methodProviders = new ArrayList<MethodProvider>();

	public GlueOperationProvider(MethodProvider...methodProviders) {
		this.methodProviders.addAll(Arrays.asList(methodProviders));
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Operation<ExecutionContext> newOperation(OperationType type) {
		switch (type) {
			case CLASSIC:
				return new ClassicOperation<ExecutionContext>();
			case METHOD:
				return new DynamicMethodOperation(methodProviders.toArray(new MethodProvider[0]));
			case VARIABLE:
				return new ScriptVariableOperation<ExecutionContext>();
			case NATIVE:
				return new NativeOperation<ExecutionContext>();
		}
		throw new RuntimeException("Unknown operation type: " + type);
	}

	@Override
	public List<MethodProvider> getMethodProviders() {
		return methodProviders;
	}
}
