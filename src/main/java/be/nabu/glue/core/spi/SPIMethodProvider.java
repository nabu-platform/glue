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

package be.nabu.glue.core.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.core.api.MethodProvider;
import be.nabu.libs.evaluator.api.Operation;

public class SPIMethodProvider implements MethodProvider {

	private List<MethodProvider> methodProviders;

	@Override
	public Operation<ExecutionContext> resolve(String name) {
		for (MethodProvider provider : getMethodProviders()) {
			Operation<ExecutionContext> operation = provider.resolve(name);
			if (operation != null) {
				return operation;
			}
		}
		return null;
	}
	
	private List<MethodProvider> getMethodProviders() {
		if (methodProviders == null) {
			synchronized(this) {
				if (methodProviders == null) {
					List<MethodProvider> methodProviders = new ArrayList<MethodProvider>();
					for (MethodProvider provider : ServiceLoader.load(MethodProvider.class)) {
						methodProviders.add(provider);
					}
					this.methodProviders = methodProviders;
				}
			}
		}
		return methodProviders;
	}

	@Override
	public List<MethodDescription> getAvailableMethods() {
		List<MethodDescription> methods = new ArrayList<MethodDescription>();
		for (MethodProvider methodProvider : getMethodProviders()) {
			methods.addAll(methodProvider.getAvailableMethods());
		}
		return methods;
	}
}