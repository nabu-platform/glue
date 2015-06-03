package be.nabu.glue.impl.parsers;

import be.nabu.glue.api.Parser;
import be.nabu.glue.api.ParserProvider;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.impl.operations.GlueOperationProvider;
import be.nabu.glue.impl.providers.SystemMethodProvider;
import be.nabu.glue.impl.providers.ScriptMethodProvider;
import be.nabu.glue.impl.providers.StaticJavaMethodProvider;
import be.nabu.glue.spi.SPIMethodProvider;

public class GlueParserProvider implements ParserProvider {

	@Override
	public Parser newParser(ScriptRepository repository, String name) {
		// make sure we have the root repository which should have access to all the other repositories
		while (repository.getParent() != null) {
			repository = repository.getParent();
		}
		return name.endsWith(".glue") && !name.startsWith(".") ? new GlueParser(new GlueOperationProvider(
			new ScriptMethodProvider(repository),
			new SPIMethodProvider(),
			new StaticJavaMethodProvider(),
			new SystemMethodProvider()
		)) : null;
	}

}
