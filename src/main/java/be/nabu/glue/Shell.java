package be.nabu.glue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.Parser;
import be.nabu.glue.api.Script;
import be.nabu.glue.impl.EnvironmentLabelEvaluator;
import be.nabu.glue.impl.SimpleExecutionEnvironment;
import be.nabu.glue.impl.formatters.GlueFormatter;
import be.nabu.glue.impl.methods.ShellMethods;
import be.nabu.glue.impl.methods.StringMethods;
import be.nabu.glue.impl.operations.GlueOperationProvider;
import be.nabu.glue.impl.parsers.GlueParser;
import be.nabu.glue.impl.parsers.GlueParserProvider;
import be.nabu.glue.impl.providers.SystemMethodProvider;
import be.nabu.glue.impl.providers.ScriptMethodProvider;
import be.nabu.glue.impl.providers.StaticJavaMethodProvider;
import be.nabu.glue.repositories.TargetedScriptRepository;
import be.nabu.glue.spi.SPIMethodProvider;

public class Shell {
	
	private static List<String> fullScript = new ArrayList<String>();
	
	public static void main(String...arguments) throws IOException, URISyntaxException, ParseException, ExecutionException {
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
		
		// if you have given a script, execute that already and pop up the shell afterwards, this allows you to pick in on a certain point
		// especially combined with the available $script variable, this basically allows you to craft a script as you go along
		List<String> commands = Main.getCommands(arguments);
		if (commands.size() > 0) {
			Script script = repository.getScript(commands.get(0));
			Map<String, Object> parameters = Main.getParameters(script, arguments);
			// set initial parameters
			runtime.getExecutionContext().getPipeline().putAll(parameters);
			// run the script
			script.getRoot().execute(runtime.getExecutionContext());
			GlueFormatter formatter = new GlueFormatter();
			StringWriter writer = new StringWriter();
			formatter.format(script.getRoot(), writer);
			fullScript.addAll(Arrays.asList(StringMethods.lines(writer.toString())));
			runtime.getExecutionContext().getPipeline().put("$script", StringMethods.join(System.getProperty("line.separator"), fullScript.toArray(new String[0])));
		}
		
		System.out.print(">> ");
		List<String> bufferedLines = new ArrayList<String>();
		while ((line = Main.readLine()) != null) {
			if (line.trim().isEmpty()) {
				continue;
			}
			else if (line.trim().equals("quit")) {
				break;
			}
			// allow multiline creation
			else if (line.trim().endsWith("/")) {
				bufferedLines.add(line.trim().replaceAll("[/]+$", "").replaceAll(";", System.getProperty("line.separator")));
			}
			else {
				try {
					bufferedLines.add(line.replaceAll(";", System.getProperty("line.separator")));
					String script = StringMethods.join(System.getProperty("line.separator"), bufferedLines.toArray(new String[0]));
					parser.parse(new StringReader(script)).execute(runtime.getExecutionContext());
					
					// make sure you have access to the full script, but do it _after_ your call, that way your current line is not added
					fullScript.addAll(bufferedLines);
					runtime.getExecutionContext().getPipeline().put("$script", StringMethods.join(System.getProperty("line.separator"), fullScript.toArray(new String[0])));
					// add the last buffer as well, that way you can basically inspect your own last line (-sequence)
					runtime.getExecutionContext().getPipeline().put("$buffer", StringMethods.join(System.getProperty("line.separator"), bufferedLines.toArray(new String[0])));
				}
				catch (ParseException e) {
					e.printStackTrace();
				}
				catch (ExecutionException e) {
					e.printStackTrace();
				}
				bufferedLines.clear();
			}
			System.out.print(">> ");
		}
	}
}
