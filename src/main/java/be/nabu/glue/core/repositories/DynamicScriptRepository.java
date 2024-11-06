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

package be.nabu.glue.core.repositories;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import be.nabu.glue.api.ParserProvider;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.core.api.GroupedScriptRepository;
import be.nabu.glue.utils.ScriptUtils;

public class DynamicScriptRepository implements GroupedScriptRepository {

	private ParserProvider provider;
	private Map<String, Script> scripts;
	private String group;
	
	public DynamicScriptRepository(ParserProvider provider) {
		this.provider = provider;
	}
	
	@Override
	public Iterator<Script> iterator() {
		return getScripts().values().iterator();
	}

	@Override
	public Script getScript(String name) throws IOException, ParseException {
		return getScripts().get(name);
	}
	
	private Map<String, Script> getScripts() {
		if (scripts == null) {
			scripts = new HashMap<String, Script>();
		}
		return scripts;
	}

	@Override
	public ParserProvider getParserProvider() {
		return provider;
	}

	@Override
	public ScriptRepository getParent() {
		return null;
	}

	@Override
	public void refresh() throws IOException {
		scripts = null;
	}

	public void add(Script script) {
		getScripts().put(ScriptUtils.getFullName(script), script);
	}
	
	public void remove(Script script) {
		getScripts().remove(ScriptUtils.getFullName(script));
	}

	@Override
	public String getGroup() {
		return group;
	}
	public void setGroup(String group) {
		this.group = group;
	}
	
}
