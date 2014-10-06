package be.nabu.glue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.HashMap;

import junit.framework.TestCase;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.impl.SimpleExecutionEnvironment;
import be.nabu.glue.impl.operations.SimpleOperationProvider;
import be.nabu.glue.impl.repository.classpath.ClassPathScriptRepository;
import be.nabu.glue.spi.SPIMethodProvider;

public class TestScripts extends TestCase {
	public void test() throws IOException, ParseException {
		ScriptRepository repository = new ClassPathScriptRepository(new SimpleOperationProvider(new SPIMethodProvider()), Charset.forName("UTF-8"), "glue");
		Script script = repository.getScript("test1");
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
