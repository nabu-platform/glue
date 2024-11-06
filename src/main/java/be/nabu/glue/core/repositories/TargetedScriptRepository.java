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
import java.net.URI;
import java.nio.charset.Charset;
import java.security.Principal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import be.nabu.glue.api.Parser;
import be.nabu.glue.api.ParserProvider;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.core.api.GroupedScriptRepository;
import be.nabu.glue.core.api.ResourceScriptRepository;
import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;

public class TargetedScriptRepository implements ResourceScriptRepository, GroupedScriptRepository {

	private URI base;
	private String[] extensions;
	private Map<String, Script> scripts = new HashMap<String, Script>();
	private ResourceFactory resourceFactory = ResourceFactory.getInstance();
	private Principal principal;
	private ParserProvider parserProvider;
	private Charset charset;
	private ScriptRepository parent;
	private String group;

	public TargetedScriptRepository(ScriptRepository parent, URI base, Principal principal, ParserProvider parserProvider, Charset charset, String...extensions) {
		this.parent = parent;
		this.base = base;
		this.principal = principal;
		this.parserProvider = parserProvider;
		this.charset = charset;
		this.extensions = extensions;
	}
	
	@Override
	public Iterator<Script> iterator() {
		return new ArrayList<Script>(scripts.values()).iterator();
	}

	@Override
	public Script getScript(String name) throws IOException, ParseException {
		if (!scripts.containsKey(name)) {
			for (String extension : extensions) {
				String path = name.replace('.', '/') + "." + extension;
				URI child = URIUtils.getChild(base, path);
				Resource resource = resourceFactory.resolve(child, principal);
				if (resource != null) {
					Parser parser = parserProvider.newParser(this, path);
					int index = name.lastIndexOf('.');
					scripts.put(name, new ResourceScript(this, charset, index >= 0 ? name.substring(0, index) : null, resource.getName(), (ReadableResource) resource, parser));
					break;
				}
			}
		}
		return scripts.get(name);
	}

	@Override
	public ParserProvider getParserProvider() {
		return parserProvider;
	}

	@Override
	public Resource resolve(String name) throws IOException {
		return resourceFactory.resolve(URIUtils.getChild(base, name), principal);
	}

	@Override
	public ScriptRepository getParent() {
		return parent;
	}

	@Override
	public void refresh() throws IOException {
		scripts.clear();
	}

	@Override
	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}
	
}
