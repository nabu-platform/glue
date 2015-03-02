package be.nabu.glue.impl;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionEnvironment;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.api.runs.ScriptResult;
import be.nabu.glue.api.runs.ScriptResultInterpretation;
import be.nabu.glue.api.runs.ScriptResultInterpreter;
import be.nabu.glue.api.runs.Validation.Level;
import be.nabu.glue.impl.formatters.GlueFormatter;
import be.nabu.glue.repositories.ResourceScript;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.WritableContainer;

public class GlueScriptResultInterpreter implements ScriptResultInterpreter {

	private ScriptRepository repository;
	private Map<ExecutionEnvironment, ExecutionContext> environments = new HashMap<ExecutionEnvironment, ExecutionContext>();
	private Map<ExecutionEnvironment, Script> scripts = new HashMap<ExecutionEnvironment, Script>();
	private boolean useNamespaces;
	private Converter converter = ConverterFactory.getInstance().getConverter();
	private double allowedVariance;
	private GlueFormatter formatter = new GlueFormatter();

	public GlueScriptResultInterpreter(ScriptRepository repository, boolean useNamespaces, double allowedVariance) {
		this.repository = repository;
		this.useNamespaces = useNamespaces;
		this.allowedVariance = allowedVariance;
	}
	
	@Override
	public ScriptResultInterpretation interpret(ScriptResult result) {
		if (result.getStopped() != null) {
			ExecutionContext context = getExpected(result.getEnvironment());
			if (context != null) {
				String namespace = result.getScript().getNamespace();
				String name = result.getScript().getName();
				String usedName = null;
				Object value = null;
				if (useNamespaces && namespace != null && context.getPipeline().containsKey(namespace + "." + name)) {
					usedName = namespace + "." + name;
					value = context.getPipeline().get(usedName);
				}
				else if (context.getPipeline().containsKey(name)) {
					usedName = name;
					value = context.getPipeline().get(name);
				}
				if (value == null) {
					// if there is no expected value just yet and this result is positive, set that
					if (result.getResultLevel() == Level.INFO) {
						addExpectation(result);
					}
				}
				else {
					Long expected = converter.convert(value, Long.class);
					if (expected == null) {
						throw new RuntimeException("Can not interpret results as the value can not be converted into an integer number: " + value);
					}
					else if (result.getStopped() != null) {
						double actual = result.getStopped().getTime() - result.getStarted().getTime();
						double actualVariance = (actual - expected) / expected;
						Object allowed = context.getPipeline().get(usedName + "_allowed");
						Double allowedVariance = allowed != null ? converter.convert(allowed, Double.class) : this.allowedVariance;
						if (allowedVariance == null) {
							throw new RuntimeException("Can not interpret results as the value can not be converted into an double number: " + allowed);	
						}
						return new SimpleScriptResultInterpretation(actualVariance, allowedVariance);
					}
				}
			}
		}
		return null;
	}
	
	private void addExpectation(ScriptResult result) {
		Script script = getScript(result.getEnvironment());
		if (script instanceof ResourceScript) {
			ReadableResource resource = ((ResourceScript) script).getResource();
			if (resource instanceof WritableResource) {
				try {
					String name = useNamespaces && result.getScript().getNamespace() != null 
						? result.getScript().getNamespace() + "." + result.getScript().getName() 
						: result.getScript().getName();
					ExecutorGroup parse = script.getParser().parse(new StringReader(name + " = " +  (result.getStopped().getTime() - result.getStarted().getTime())));
					script.getRoot().getChildren().add(parse.getChildren().get(0));
					StringWriter writer = new StringWriter();
					formatter.format(script.getRoot(), writer);
					String current = writer.toString();
					WritableContainer<ByteBuffer> writable = ((WritableResource) resource).getWritable();
					try {
						writable.write(IOUtils.wrap(current.getBytes(Charset.forName("UTF-8")), true));
					}
					finally {
						writable.close();
					}
				}
				catch (ParseException e) {
					throw new RuntimeException(e);
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	private ExecutionContext getExpected(ExecutionEnvironment environment) {
		if (!environments.containsKey(environment)) {
			synchronized(environments) {
				if (!environments.containsKey(environment)) {
					Script script = getScript(environment);
					if (script == null) {
						environments.put(environment, null);	
					}
					else {
						ScriptRuntime runtime = new ScriptRuntime(script, environment, false, null);
						runtime.run();
						if (runtime.getException() != null) {
							runtime.getException().printStackTrace();
							environments.put(environment, null);
						}
						else {
							environments.put(environment, runtime.getExecutionContext());
						}
					}
				}
			}
		}
		return environments.get(environment);
	}
	
	private Script getScript(ExecutionEnvironment environment) {
		if (!scripts.containsKey(environment)) {
			synchronized(scripts) {
				if (!scripts.containsKey(environment)) {
					try {
						scripts.put(environment, repository.getScript("references." + environment.getName().toLowerCase()));
					}
					catch (IOException e) {
						e.printStackTrace();
						scripts.put(environment, null);
					}
					catch (ParseException e) {
						e.printStackTrace();
						scripts.put(environment, null);
					}
				}
			}
		}
		return scripts.get(environment);
	}
}
