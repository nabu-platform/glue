/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

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
import be.nabu.libs.evaluator.impl.VariableOperation;

public class TestScripts extends TestCase {
	public void test() throws IOException, ParseException, URISyntaxException {
		System.setProperty("version", "1-2");
		VariableOperation.alwaysUseConcatenationForDollarIndex = false;
		VariableOperation.neverUseConcatenationForDollarIndex = false;
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
