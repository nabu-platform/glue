package be.nabu.glue;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.ParseException;

import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.Parser;
import be.nabu.glue.impl.EnvironmentLabelEvaluator;
import be.nabu.glue.impl.SimpleExecutionEnvironment;
import be.nabu.glue.impl.methods.ShellMethods;
import be.nabu.glue.impl.operations.GlueOperationProvider;
import be.nabu.glue.impl.parsers.GlueParser;
import be.nabu.glue.impl.parsers.GlueParserProvider;
import be.nabu.glue.impl.providers.SystemMethodProvider;
import be.nabu.glue.impl.providers.ScriptMethodProvider;
import be.nabu.glue.impl.providers.StaticJavaMethodProvider;
import be.nabu.glue.repositories.TargetedScriptRepository;
import be.nabu.glue.spi.SPIMethodProvider;

public class Shell {
	
	public static void main(String...arguments) throws IOException, URISyntaxException {
		Charset charset = Main.getCharset(arguments);
		MultipleRepository repository = Main.buildRepository(charset, arguments);
		repository.add(new TargetedScriptRepository(repository, new URI("classpath:/shell"), null, new GlueParserProvider(), charset, "glue"));
		SimpleExecutionEnvironment environment = new SimpleExecutionEnvironment(Main.getEnvironmentName(arguments));
		Main.setArguments(environment, arguments);
		
		Parser parser =  new GlueParser(new GlueOperationProvider(
			new StaticJavaMethodProvider(ShellMethods.class),
			new ScriptMethodProvider(repository),
			new SPIMethodProvider(),
			new StaticJavaMethodProvider(),
			new SystemMethodProvider()
		));
		
		String line = null;
		// register a runtime for this thread
		ScriptRuntime runtime = new ScriptRuntime(new DynamicScript(repository, new GlueParserProvider().newParser(repository, "dynamic.glue")), environment, false, null);
		runtime.setLabelEvaluator(new EnvironmentLabelEvaluator(Main.getLabel(arguments)));
		runtime.registerInThread();
		
		System.out.print(">> ");
		while ((line = Main.readLine()) != null) {
			if (line.trim().isEmpty()) {
				continue;
			}
			else if (line.trim().equals("quit")) {
				break;
			}
			try {
				parser.parse(new StringReader(line)).execute(runtime.getExecutionContext());
			}
			catch (ParseException e) {
				e.printStackTrace();
			}
			catch (ExecutionException e) {
				e.printStackTrace();
			}
			System.out.print(">> ");
		}
	}
}
