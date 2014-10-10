package be.nabu.glue;

import java.io.IOException;
import java.lang.Thread.State;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import be.nabu.glue.MultipleRepository;
import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.ScriptUtils;
import be.nabu.glue.api.DynamicMethodOperationProvider;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.MethodProvider;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.api.Script;
import be.nabu.glue.impl.SimpleExecutionEnvironment;
import be.nabu.glue.impl.operations.GlueOperationProvider;
import be.nabu.glue.impl.parsers.GlueParserProvider;
import be.nabu.glue.impl.providers.StaticJavaMethodProvider;
import be.nabu.glue.repositories.ScannableScriptRepository;
import be.nabu.glue.repositories.TargetedScriptRepository;
import be.nabu.glue.spi.SPIMethodProvider;
import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.api.ResourceContainer;

public class Main {
	
	public static void main(String...arguments) throws IOException, ParseException, URISyntaxException {
		DynamicMethodOperationProvider operationProvider = new GlueOperationProvider(new SPIMethodProvider(), new StaticJavaMethodProvider());
		Charset charset = Charset.forName(getArgument("charset", "UTF-8", arguments));
		String environment = getArgument("environment", "local", arguments);
		boolean debug = new Boolean(getArgument("debug", "false", arguments));
		boolean trace = new Boolean(getArgument("trace", "false", arguments));
		debug |= trace;
		
		MultipleRepository repository = new MultipleRepository(null);
		for (String path : getArgument("path", System.getenv("PATH"), arguments).split(System.getProperty("path.separator", ":"))) {
			URI uri = new URI("file:/" + path.replace("\\ ", " ").trim());
			repository.add(new ScannableScriptRepository(repository, (ResourceContainer<?>) ResourceFactory.getInstance().resolve(uri, null), new GlueParserProvider(), charset));
		}
		repository.add(new TargetedScriptRepository(repository, new URI("classpath:/"), null, new GlueParserProvider(), charset, "glue"));
		
		
		if (new Boolean(getArgument("-l", "false", arguments))) {
			Map<String, MethodDescription> sortedDescriptions = new TreeMap<String, MethodDescription>();
			for (MethodProvider provider : operationProvider.getMethodProviders()) {
				for (MethodDescription description : provider.getAvailableMethods()) {
					sortedDescriptions.put(description.getName(), description);
				}
			}
			for (String key : sortedDescriptions.keySet()) {
				MethodDescription sortedDescription = sortedDescriptions.get(key);
				System.out.println(key + "()");
				if (sortedDescription.getDescription() != null) {
					System.out.println("\t\t" +sortedDescription.getDescription().replaceAll(System.getProperty("line.separator"), System.getProperty("line.separator") + "\t\t"));
				}
				if (sortedDescription.getParameters().size() > 0) {
					for (ParameterDescription description : sortedDescription.getParameters()) {
						System.out.print("\t");
						if (description.getType() != null) {
							System.out.print(description.getType() + " ");
						}
						System.out.print(description.getName());
						if (description.getDescription() != null) {
							System.out.print(": " + description.getDescription());
						}
						System.out.println();
					}
				}
				System.out.println("---------------------------------------------------------------------------------------");
			}
		}
		else {
			List<String> commands = new ArrayList<String>();
			for (String argument : arguments) {
				if (!argument.contains("=")) {
					commands.add(argument);
				}
			}
			if (commands.isEmpty()) {
				throw new IllegalArgumentException("No command found");
			}
			Script script = repository.getScript(commands.get(0));
			if (script == null) {
				throw new IllegalArgumentException("No script found by the name of " + commands.get(0));
			}
			List<ParameterDescription> inputs = ScriptUtils.getInputs(script);
			Map<String, Object> parameters = new HashMap<String, Object>();
			for (int i = 1; i < commands.size(); i++) {
				if (i > inputs.size()) {
					throw new IllegalArgumentException("Too many arguments, expecting " + inputs.size());
				}
				parameters.put(inputs.get(i - 1).getName(), commands.get(i));
			}
			
			ScriptRuntime runtime = new ScriptRuntime(
				script,
				new SimpleExecutionEnvironment(environment), 
				debug,
				parameters
			);
			
			if (trace) {
				runtime.setInitialBreakpoint(script.getRoot().getChildren().get(0).getId());
			}
	
			if (trace) {
				System.out.println("---------------------------------");
				System.out.println("Trace commands:");
				System.out.println("\t* o: step over");
				System.out.println("\t* i: step into");
				System.out.println("\t* r: resume");
				System.out.println("\t* v: view variables");
				System.out.println("\t* q: quit");
				System.out.println("---------------------------------");
			}
			
			Thread thread = new Thread(runtime);
			thread.setDaemon(true);
			thread.start();
			while (thread.isAlive()) {
				if (thread.getState() == State.TIMED_WAITING && runtime.getExecutionContext().getBreakpoint() != null) {
					System.out.print("\tCommand: ");
					char [] response = System.console().readLine().toCharArray();
					if (response[0] == 'q') {
						break;
					}
					else if (response[0] == 'v') {
						System.out.println(runtime.getExecutionContext());
					}
					else {
						// when tracing, set the next breakpoint
						if (trace && response[0] != 'r') {
							Executor next = getNextStep(runtime.getExecutionContext().getCurrent(), response[0] == 'i');
							runtime.getExecutionContext().setBreakpoint(next != null ? next.getId() : null);
						}
						thread.interrupt();
						while (thread.getState() == State.TIMED_WAITING);
					}
				}
			}
		}
	}
	
	private static Executor getNextStep(Executor current, boolean goInto) {
		while (current.getParent() != null) {
			int currentIndex = current.getParent().getChildren().indexOf(current);
			if (currentIndex < current.getParent().getChildren().size() - 1) {
				Executor executor = current.getParent().getChildren().get(currentIndex + 1);
				if (executor instanceof ExecutorGroup) {
					if (goInto) {
						return getNextStep(((ExecutorGroup) executor).getChildren().get(0), goInto);
					}
					else {
						return getNextStep(executor, goInto);
					}
				}
				else {
					return executor;
				}
			}
			else {
				current = current.getParent();
			}
		}
		return null;
	}
	
	private static String getArgument(String name, String defaultValue, String...arguments) {
		for (String argument : arguments) {
			if (argument.trim().startsWith(name + "=")) {
				String value = argument.substring(name.length() + 1);
				if (value.isEmpty()) {
					throw new IllegalArgumentException("The parameter " + name + " is empty");
				}
				return value;
			}
			else if (argument.trim().equals(name)) {
				return "true";
			}
		}
		return defaultValue;
	}
}
