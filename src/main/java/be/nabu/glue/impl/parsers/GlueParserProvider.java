package be.nabu.glue.impl.parsers;

import be.nabu.glue.api.MethodProvider;
import be.nabu.glue.api.Parser;
import be.nabu.glue.api.ParserProvider;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.impl.LambdaMethodProvider;
import be.nabu.glue.impl.methods.v2.ControlMethodProvider;
import be.nabu.glue.impl.operations.GlueOperationProvider;
import be.nabu.glue.impl.providers.SystemMethodProvider;
import be.nabu.glue.impl.providers.ScriptMethodProvider;
import be.nabu.glue.impl.providers.StaticJavaMethodProvider;
import be.nabu.glue.spi.SPIMethodProvider;

public class GlueParserProvider implements ParserProvider {

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
		for (int i = 1; i <= methodProviders.length; i++) {
			providers[i] = methodProviders[i];
		}
		providers[providers.length - 5] = new ScriptMethodProvider(repository);
		providers[providers.length - 4] = new SPIMethodProvider();
		providers[providers.length - 3] = new StaticJavaMethodProvider();
		providers[providers.length - 2] = new SystemMethodProvider();
		providers[providers.length - 1] = new ControlMethodProvider();
		return providers;
	}

}
