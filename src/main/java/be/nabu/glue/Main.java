package be.nabu.glue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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

import be.nabu.glue.api.DynamicMethodOperationProvider;
import be.nabu.glue.api.ExecutionEnvironment;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.MethodProvider;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.api.Parser;
import be.nabu.glue.api.Script;
import be.nabu.glue.impl.EnvironmentLabelEvaluator;
import be.nabu.glue.impl.SimpleExecutionEnvironment;
import be.nabu.glue.impl.formatters.MarkdownOutputFormatter;
import be.nabu.glue.impl.methods.ScriptMethods;
import be.nabu.glue.impl.methods.TestMethods;
import be.nabu.glue.impl.methods.GlueValidationImpl;
import be.nabu.glue.impl.operations.GlueOperationProvider;
import be.nabu.glue.impl.parsers.GlueParserProvider;
import be.nabu.glue.impl.providers.ScriptMethodProvider;
import be.nabu.glue.impl.providers.StaticJavaMethodProvider;
import be.nabu.glue.repositories.ScannableScriptRepository;
import be.nabu.glue.repositories.TargetedScriptRepository;
import be.nabu.glue.spi.SPIMethodProvider;
import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.ResourceContainer;

public class Main {
	
	@SuppressWarnings("unchecked")
	public static void main(String...arguments) throws IOException, ParseException, URISyntaxException {
		Charset charset = getCharset(arguments);
		String environmentName = getEnvironmentName(arguments);
		String label = getLabel(arguments);
		boolean debug = new Boolean(getArgument("debug", "false", arguments));
		boolean trace = new Boolean(getArgument("trace", "false", arguments));
		boolean duration = new Boolean(getArgument("duration", "false", arguments));
		boolean printReport = new Boolean(getArgument("report", "false", arguments));
		boolean useMarkdown = new Boolean(getArgument("markdown", "false", arguments));
		
		// currently we default to version 2
		String allVersion = getArgument("version", "1-2", arguments);
		
		if (System.getProperty("version") == null) {
			System.setProperty("version", allVersion);
		}
		
//		debug |= trace;
		MultipleRepository repository = buildRepository(charset, arguments);
		repository.add(new TargetedScriptRepository(repository, new URI("classpath:/shell"), null, new GlueParserProvider(), charset, "glue"));
		
		List<String> commands = getCommands(arguments);
		if (new Boolean(getArgument("man", "false", arguments))) {
			String nameToMatch = "(?i)" + commands.get(1).replace("*", ".*");
			for (Script script : repository) {
				if (script.getName().matches(nameToMatch)) {
					System.out.println("> " + script.getName());
					if (script.getRoot().getContext().getComment() != null) {
						System.out.println("\t\t* " + script.getRoot().getContext().getComment().replace("\n", "\n\t\t* "));
					}
					for(ParameterDescription parameter : ScriptUtils.getInputs(script)) {
						System.out.print("\t- ");
						if (parameter.getType() != null) {
							System.out.print(parameter.getType() + " ");
						}
						System.out.print(parameter.getName());
						if (parameter.getDescription() != null) {
							System.out.print(": " + parameter.getDescription());
						}
						System.out.println();
					}
				}
			}
		}
		else if (new Boolean(getArgument("-l", "false", arguments))) {
			DynamicMethodOperationProvider operationProvider = new GlueOperationProvider(new ScriptMethodProvider(repository), new SPIMethodProvider(), new StaticJavaMethodProvider());

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
					System.out.println("\t\t" + sortedDescription.getDescription().replaceAll(System.getProperty("line.separator"), System.getProperty("line.separator") + "\t\t"));
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
			Script script = null;
			// assume you are streaming a glue script
			if (commands.isEmpty()) {
				Parser parser = repository.getParserProvider().newParser(repository, "temp.glue");
				DynamicScript dynamicScript = new DynamicScript(repository, parser);
				dynamicScript.setRoot(parser.parse(new BufferedReader(new InputStreamReader(System.in))));
				script = dynamicScript;
			}
			else {
				script = repository.getScript(commands.get(0));
			}
			if (script == null) {
				throw new IllegalArgumentException("No script found by the name of " + commands.get(0));
			}
			
			Map<String, Object> parameters = getParameters(script, arguments);
			
			SimpleExecutionEnvironment environment = new SimpleExecutionEnvironment(environmentName);
			setArguments(environment, arguments);
			ScriptRuntime runtime = new ScriptRuntime(
				script,
				environment, 
				debug,
				parameters
			);
			runtime.setTrace(trace);
			if (useMarkdown) {
				runtime.setFormatter(new MarkdownOutputFormatter(new OutputStreamWriter(System.out)));
			}
			
			// this is the field the label is checked against in your environment list
			runtime.setLabelEvaluator(new EnvironmentLabelEvaluator(label));
			
			if (trace) {
				runtime.addBreakpoint(script.getRoot().getChildren().get(0).getId());
			}
	
			if (trace) {
				System.out.println("---------------------------------");
				System.out.println("Trace commands:");
				System.out.println("\t* c: check label");
				System.out.println("\t* o: step over");
				System.out.println("\t* b: remove current breakpoint");
				System.out.println("\t* i: step into");
				System.out.println("\t* r: resume until next breakpoint");
				System.out.println("\t* s: remove all breakpoints and resume");
				System.out.println("\t* v: view variables");
				System.out.println("\t* q: quit");
				System.out.println("\t* [number]: skip to this line");
				System.out.println("\t* [script]:[number]: skip to this line in another script");
				System.out.println("---------------------------------");
				Thread thread = new Thread(runtime);
				thread.setDaemon(true);
				thread.start();
				while (thread.isAlive()) {
					if (thread.getState() == State.TIMED_WAITING && !runtime.getExecutionContext().getBreakpoints().isEmpty()) {
						System.out.print("\tCommand: ");
						String response = readLine().trim();
						if (response.length() == 1 && !response.matches("[0-9]")) {
							if (response.charAt(0) == 'q') {
								runtime.abort();
								break;
							}
							else if (response.charAt(0) == 'c') {
								String labelToCheck = getCurrent(runtime).getExecutionContext().getCurrent().getContext().getLabel();
								System.out.println("Label " + labelToCheck + ": " + 
										getCurrent(runtime).getExecutionContext().getLabelEvaluator().shouldExecute(labelToCheck, getCurrent(runtime).getExecutionContext().getExecutionEnvironment()));
							}
							else if (response.charAt(0) == 'v') {
								System.out.println(getCurrent(runtime).getExecutionContext());
							}
							else if (response.charAt(0) == 'b') {
								getCurrent(runtime).removeBreakpoint(getCurrent(runtime).getExecutionContext().getCurrent().getId());
							}
							else {
								if (trace && response.charAt(0) == 's') {
									getCurrent(runtime).getExecutionContext().setTrace(false);
								}
								// when tracing, set the next breakpoint
								else if (trace && response.charAt(0) != 'r') {
	//								Executor next = getNextStep(runtime.getExecutionContext().getCurrent(), response.charAt(0) == 'i');
									Executor next = getNextStep(runtime, response.charAt(0) == 'i');
									getCurrent(runtime).getExecutionContext().addBreakpoint(next != null ? next.getId() : null);
								}
								thread.interrupt();
								while (thread.getState() == State.TIMED_WAITING);
							}
						}
						// you want a breakpoint in a specific script
						else if (response.matches("[\\w.]+:[0-9]+")) {
							String scriptName = response.replaceAll(":.*", "");
							Script target = repository.getScript(scriptName);
							if (target == null) {
								System.err.println("Can not find target script: " + scriptName);
							}
							Executor next = getLine(target.getRoot(), new Integer(response.substring(scriptName.length() + 1)) - 1);
							if (next == null) {
								System.err.println("Can not find line number " + response + " in target script: " + scriptName);
							}
							else {
								getCurrent(runtime).getExecutionContext().addBreakpoint(next.getId());
								thread.interrupt();
								while (thread.getState() == State.TIMED_WAITING);
							}
						}
						// switch to line number in this script
						else if (response.matches("[0-9]+")) {
							Executor next = getLine(script.getRoot(), new Integer(response) - 1);
							if (next == null) {
								System.err.println("Can not find line number " + response);
							}
							else {
								getCurrent(runtime).getExecutionContext().addBreakpoint(next.getId());
								thread.interrupt();
								while (thread.getState() == State.TIMED_WAITING);
							}
						}
						else if (response.length() > 1) {
							try {
								getCurrent(runtime).fork(new VirtualScript(getCurrent(runtime).getScript(), response.replace(';', '\n'))).run();
							}
							catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
				}
			}
			else {
				runtime.run();
			}
			if (duration) {
				System.out.println("Executed in " + (runtime.getDuration() / 1000d) + "s");
			}
			if (printReport) {
				List<GlueValidationImpl> messages = (List<GlueValidationImpl>) runtime.getContext().get(TestMethods.VALIDATION);
				if (messages != null && !messages.isEmpty()) {
					for (GlueValidationImpl message : messages) {
						 System.out.println(message);
					}
				}
			}
		}
	}

	public static Map<String, Object> getParameters(Script script, String...arguments) throws ParseException, IOException {
		List<ParameterDescription> inputs = ScriptUtils.getInputs(script);
		Map<String, Object> parameters = new HashMap<String, Object>();
		List<String> commands = getCommands(arguments);
		for (int i = 1; i < commands.size(); i++) {
			if (i > inputs.size()) {
				if (!ScriptMethodProvider.ALLOW_VARARGS || inputs.isEmpty() || !inputs.get(inputs.size() - 1).isVarargs()) {
					throw new IllegalArgumentException("Too many arguments, expecting " + inputs.size());
				}
				else {
					parameters.put(inputs.get(inputs.size() - 1).getName(), ScriptMethods.array(parameters.get(inputs.get(inputs.size() - 1).getName()), commands.get(i)));
				}
			}
			else {
				parameters.put(inputs.get(i - 1).getName(), commands.get(i));
			}
		}
		return parameters;
	}

	public static List<String> getCommands(String... arguments) {
		List<String> commands = new ArrayList<String>();
		for (String argument : arguments) {
			if (!argument.contains("=")) {
				commands.add(argument);
			}
		}
		return commands;
	}

	public static String getLabel(String...arguments) {
		return getArgument("label", null, arguments);
	}

	public static String getEnvironmentName(String...arguments) {
		return getArgument("environment", "local", arguments);
	}

	public static Charset getCharset(String... arguments) {
		return Charset.forName(getArgument("charset", "UTF-8", arguments));
	}

	public static MultipleRepository buildRepository(Charset charset, String...arguments) throws IOException, URISyntaxException {
		return buildRepository(charset, true, arguments);
	}
	
	public static MultipleRepository buildRepository(Charset charset, boolean includeLocal, String...arguments) throws IOException, URISyntaxException {
		MultipleRepository repository = new MultipleRepository(null);
		if (ResourceFactory.getInstance().getResolver("file") != null) {
			// add the current directory so you can go to a directory and execute it there
			if (includeLocal) {
				ResourceContainer<?> localContainer = (ResourceContainer<?>) ResourceFactory.getInstance().resolve(new File("").toURI(), null);
				if (localContainer != null) {
					repository.add(new ScannableScriptRepository(repository, localContainer, new GlueParserProvider(), charset, false));
				}
			}
			// try a dedicated "GLUEPATH" variable first because the general "PATH" variable tends to be very big (at least when searching recursively) and slows down the startup of glue
			String systemPath = System.getenv("GLUEPATH");
			if (systemPath == null) {
				if (getArgument("path", null, arguments) == null) {
					System.out.println("You can set a 'GLUEPATH' system variable which points to the directories that contain scripts");
					System.out.println("If not set, glue will use the 'PATH' variable but this might slow down glue startup times");
				}
				systemPath = System.getenv("PATH");
			}
			for (String path : getArgument("path", systemPath, arguments).split(System.getProperty("path.separator", ":"))) {
				URI uri = new URI(URIUtils.encodeURI("file:/" + path.replace("\\ ", " ").trim().replace('\\', '/')));
				ResourceContainer<?> container = (ResourceContainer<?>) ResourceFactory.getInstance().resolve(uri, null);
				if (container == null) {
					System.err.println("The directory " + uri + " does not exist");
				}
				else {
					repository.add(new ScannableScriptRepository(repository, container, new GlueParserProvider(), charset));
				}
			}
		}
		else {
			System.out.println("WARNING: To pick up scripts on the file system you need to include the project 'resources-file'");
		}
		if (new Boolean(getArgument("classpath", "true", arguments))) {
			repository.add(new TargetedScriptRepository(repository, new URI("classpath:/scripts"), null, new GlueParserProvider(), charset, "glue"));
		}
		if (new Boolean(getArgument("remote", "false", arguments))) {
			// the default remote repository
			repository.add(new TargetedScriptRepository(repository, new URI("https://raw.githubusercontent.com/nablex/scripts/master"), null, new GlueParserProvider(), charset, "glue"));
		}
		// you can configure your own remote repositories
		String repositories = getArgument("repositories", null, arguments);
		if (repositories != null) {
			for (String remoteRepository : repositories.split(",")) {
				repository.add(new TargetedScriptRepository(repository, new URI(remoteRepository), null, new GlueParserProvider(), charset, "glue"));		
			}
		}
		return repository;
	}
	
	public static void setArguments(ExecutionEnvironment environment, String...arguments) {
		for (String argument : arguments) {
			int index = argument.indexOf('=');
			if (index >= 0) {
				String key = argument.substring(0, index);
				String value = argument.substring(index + 1);
				environment.getParameters().put(key, value);
			}
		}
	}
	
	public static String readLine() throws IOException {
		// cygwin does not expose a console()
		if (System.console() != null) {
			return System.console().readLine();
		}
		else {
			return new BufferedReader(new InputStreamReader(System.in)).readLine();
		}
	}
	
	public static ScriptRuntime getCurrent(ScriptRuntime runtime) {
		while (runtime.getChild() != null) {
			runtime = runtime.getChild();
		}
		return runtime;
	}
	
	private static Executor getLine(ExecutorGroup group, int line) {
		for (Executor child : group.getChildren()) {
			if (child.getContext().getLineNumber() == line) {
				return child;
			}
			else if (child instanceof ExecutorGroup) {
				Executor target = getLine((ExecutorGroup) child, line);
				if (target != null) {
					return target;
				}
			}
		}
		return null;
	}
	
	public static Executor getNextStep(ScriptRuntime runtime, boolean goInto) {
		while(runtime.getChild() != null) {
			runtime = runtime.getChild();
		}
		return getNextStep(runtime.getExecutionContext().getCurrent(), goInto);
	}
	
	public static Executor getNextStep(Executor current, boolean goInto) {
		int currentIndex = current.getParent().getChildren().indexOf(current);
		if (currentIndex < current.getParent().getChildren().size() - 1) {
			Executor executor = current.getParent().getChildren().get(currentIndex + 1);
			while (executor instanceof ExecutorGroup && goInto && !((ExecutorGroup) executor).getChildren().isEmpty()) {
				executor = ((ExecutorGroup) executor).getChildren().get(0);
			}
			// if it's still a group, just get out of it
			if (executor instanceof ExecutorGroup) {
				return getNextStep(current.getParent(), goInto);	
			}
			else {
				return executor;
			}
		}
		else {
			return getNextStep(current.getParent(), goInto);
		}
	}
	
	public static String getArgument(String name, String defaultValue, String...arguments) {
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
