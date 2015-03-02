package be.nabu.glue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.xml.bind.JAXBException;

import be.nabu.glue.api.runs.ScriptResult;
import be.nabu.glue.api.runs.ScriptResultInterpreter;
import be.nabu.glue.api.runs.ScriptRunner;
import be.nabu.glue.impl.EnvironmentLabelEvaluator;
import be.nabu.glue.impl.GlueScriptResultInterpreter;
import be.nabu.glue.impl.MultithreadedScriptRunner;
import be.nabu.glue.impl.SimpleExecutionEnvironment;
import be.nabu.glue.impl.TestCaseFilter;
import be.nabu.glue.impl.formatted.FormattedDashboard;
import be.nabu.glue.impl.formatted.FormattedScriptResult;
import be.nabu.glue.impl.parsers.GlueParserProvider;
import be.nabu.glue.repositories.ScannableScriptRepository;
import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.WritableContainer;

public class Runner {

	public static void main(String...arguments) throws URISyntaxException, IOException, JAXBException {
		Charset charset = Charset.forName(getArgument("charset", "UTF-8", arguments));
		String environmentName = getArgument("environment", "local", arguments);
		String label = getArgument("label", null, arguments);
		int poolSize = new Integer(getArgument("poolSize", "1", arguments));
		String resultPath = getArgument("results", new File("results").toURI().toString(), arguments);
		boolean useNamespaces = Boolean.parseBoolean(getArgument("useNamespaces", "false", arguments));
		double allowedVariance = Double.parseDouble(getArgument("allowedVariance", "0.4", arguments));
		
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd/HHmmss");
		ManageableContainer<?> resultContainer = (ManageableContainer<?>) ResourceUtils.mkdir(new URI(URIUtils.encodeURI(resultPath + "/" + environmentName + "/" + formatter.format(new Date()))), null);
		
		MultipleRepository repository = new MultipleRepository(null);
		for (String path : getArgument("path", System.getenv("PATH"), arguments).split(System.getProperty("path.separator", ":"))) {
			URI uri = new URI(URIUtils.encodeURI("file:/" + path.replace("\\ ", " ").trim().replace('\\', '/')));
			ResourceContainer<?> container = (ResourceContainer<?>) ResourceFactory.getInstance().resolve(uri, null);
			if (container == null) {
				System.err.println("The directory " + uri + " does not exist");
			}
			else {
				repository.add(new ScannableScriptRepository(repository, container, new GlueParserProvider(), charset));
			}
		}
		
		SimpleExecutionEnvironment environment = new SimpleExecutionEnvironment(environmentName);
		environment.getParameters().put("runtime.label", getArgument("label", null, arguments));
		environment.getParameters().put("runtime.environment", getArgument("environment", null, arguments));
		environment.getParameters().put("runtime.charset", getArgument("charset", null, arguments));
		environment.getParameters().put("runtime.debug", getArgument("debug", null, arguments));
		environment.getParameters().put("runtime.trace", getArgument("trace", null, arguments));
		environment.getParameters().put("runtime.duration", getArgument("duration", null, arguments));
		environment.getParameters().put("runtime.path", getArgument("path", null, arguments));
		
		ScriptRunner runner = new MultithreadedScriptRunner(poolSize);
		
		ScriptResultInterpreter interpreter = new GlueScriptResultInterpreter(repository, useNamespaces, allowedVariance);
		List<ScriptResult> results = runner.run(environment, repository, new TestCaseFilter(), new EnvironmentLabelEvaluator(label));
		for (ScriptResult result : results) {
			try {
				FormattedScriptResult formatted = FormattedScriptResult.format(result, interpreter.interpret(result));
				WritableResource writable = (WritableResource) resultContainer.create(formatted.getName() + ".xml", "application/xml");
				WritableContainer<ByteBuffer> output = writable.getWritable();
				try {
					formatted.marshal(IOUtils.toOutputStream(output));
				}
				finally {
					output.close();
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		// write dashboard file
		FormattedDashboard dashboard = FormattedDashboard.format(interpreter, results.toArray(new ScriptResult[results.size()]));
		WritableResource writable = (WritableResource) resultContainer.create("dashboard.xml", "application/xml");
		WritableContainer<ByteBuffer> output = writable.getWritable();
		try {
			dashboard.marshal(IOUtils.toOutputStream(output));
		}
		finally {
			output.close();
		}
		// write it to the root as well to both identify environments and to have the "latest" dashboard quickly accessible per environment
		WritableContainer<ByteBuffer> writableContainer = ResourceUtils.toWritableContainer(new URI(URIUtils.encodeURI(resultPath + "/dashboard." + environmentName + ".xml")), null);
		try {
			dashboard.marshal(IOUtils.toOutputStream(writableContainer));
		}
		finally {
			writableContainer.close();
		}
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
