package be.nabu.glue.core;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.HashMap;

import junit.framework.TestCase;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.core.impl.parsers.GlueParserProvider;
import be.nabu.glue.core.repositories.TargetedScriptRepository;
import be.nabu.glue.impl.SimpleExecutionEnvironment;
import be.nabu.glue.utils.ScriptRuntime;

public class TestScripts extends TestCase {
	public void test() throws IOException, ParseException, URISyntaxException {
		System.setProperty("version", "1-2");
		ScriptRepository repository = new TargetedScriptRepository(null, new URI("classpath:/scripts"), null, new GlueParserProvider(), Charset.forName("UTF-8"), "glue");
		Script script = repository.getScript("testAll");
		ScriptRuntime runtime = new ScriptRuntime(
			script,
			new SimpleExecutionEnvironment("LOCAL"), 
			false,
			new HashMap<String, Object>()
		);
		runtime.run();
		System.out.println(runtime.getExecutionContext());
	}
}
