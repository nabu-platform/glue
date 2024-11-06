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

package be.nabu.glue.core.impl.parsers;

import be.nabu.glue.api.Parser;
import be.nabu.glue.api.ParserProvider;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.core.api.MethodProvider;
import be.nabu.glue.core.api.SandboxableMethodProvider;
import be.nabu.glue.core.impl.LambdaMethodProvider;
import be.nabu.glue.core.impl.methods.v2.ControlMethodProvider;
import be.nabu.glue.core.impl.operations.GlueOperationProvider;
import be.nabu.glue.core.impl.providers.ScriptMethodProvider;
import be.nabu.glue.core.impl.providers.StaticJavaMethodProvider;
import be.nabu.glue.core.impl.providers.SystemMethodProvider;
import be.nabu.glue.core.spi.SPIMethodProvider;

public class GlueParserProvider implements ParserProvider {

	private boolean sandboxed;
	private MethodProvider[] methodProviders;

	public GlueParserProvider(MethodProvider...methodProviders) {
		this.methodProviders = methodProviders;
	}
	
	@Override
	public Parser newParser(ScriptRepository repository, String name) {
		// make sure we have the root repository which should have access to all the other repositories
		while (repository.getParent() != null) {
			repository = repository.getParent();
		}
		if (name.endsWith(".glue") && !name.startsWith(".")) {
			return new GlueParser(repository, newOperationProvider(repository));
		}
		else if (name.endsWith(".eglue") && !name.startsWith(".")) {
			return new EmbeddedGlueParser(repository, newOperationProvider(repository));
		}
		return null;
	}

	public GlueOperationProvider newOperationProvider(ScriptRepository repository) {
		return new GlueOperationProvider(getMethodProviders(repository));
	}
	
	public MethodProvider[] getMethodProviders(ScriptRepository repository) {
		MethodProvider [] providers = new MethodProvider[methodProviders.length + 6];
		providers[0] = new LambdaMethodProvider();
		for (int i = 0; i < methodProviders.length; i++) {
			providers[i + 1] = methodProviders[i];
		}
		providers[providers.length - 5] = new ScriptMethodProvider(repository);
		providers[providers.length - 4] = new SPIMethodProvider();
		providers[providers.length - 3] = new StaticJavaMethodProvider();
		providers[providers.length - 2] = new SystemMethodProvider();
		providers[providers.length - 1] = new ControlMethodProvider();
		
		// set sandbox mode if relevant
		for (int i = 0; i < providers.length; i++) {
			if (providers[i] instanceof SandboxableMethodProvider) {
				((SandboxableMethodProvider) providers[i]).setSandboxed(sandboxed);
			}
		}
		return providers;
	}

	public boolean isSandboxed() {
		return sandboxed;
	}

	public void setSandboxed(boolean sandboxed) {
		this.sandboxed = sandboxed;
	}
}
