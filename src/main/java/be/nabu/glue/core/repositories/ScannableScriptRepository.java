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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.api.Parser;
import be.nabu.glue.api.ParserProvider;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.api.ScriptRepositoryWithDescriptions;
import be.nabu.glue.core.api.GroupedScriptRepository;
import be.nabu.glue.core.api.ResourceScriptRepository;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.utils.ScriptUtils;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.features.CacheableResource;

public class ScannableScriptRepository implements ResourceScriptRepository, GroupedScriptRepository, ScriptRepositoryWithDescriptions {

	private ParserProvider parserProvider;
	private ResourceContainer<?> root;
	private Map<String, Script> scripts;
	private Charset charset;
	private ScriptRepository parent;
	private boolean recurse;
	private String group;
	private Map<Script, MethodDescription> descriptions;

	public ScannableScriptRepository(ScriptRepository parent, ResourceContainer<?> root, ParserProvider parserProvider, Charset charset) throws IOException {
		this(parent, root, parserProvider, charset, true);
	}
	
	public ScannableScriptRepository(ScriptRepository parent, ResourceContainer<?> root, ParserProvider parserProvider, Charset charset, boolean recurse) throws IOException {
		this.parent = parent;
		this.root = root;
		this.parserProvider = parserProvider;
		this.charset = charset;
		this.recurse = recurse;
		refresh();
	}
	
	private Map<String, Script> scan(ResourceContainer<?> folder, String namespace, Map<Script, MethodDescription> descriptions) throws IOException {
		Map<String, Script> scripts = new HashMap<String, Script>();
		if (folder instanceof CacheableResource) {
			((CacheableResource) folder).resetCache();
		}
		for (Resource child : folder) {
			if (child instanceof ResourceContainer && recurse) {
				scripts.putAll(scan((ResourceContainer<?>) child, namespace == null ? child.getName() : namespace + "." + child.getName(), descriptions));
			}
			else {
				Parser parser = parserProvider.newParser(this, child.getName());
				if (parser != null) {
					String childName = (namespace == null ? null : namespace + ".") + child.getName().replaceAll("\\.[^.]+$", ""); 
					if (this.scripts != null && this.scripts.get(childName) instanceof ResourceScript) {
						// just take the original script, this is a performance optimization bypassing the need to always reparse/recalculate all scripts
						scripts.put(childName, this.scripts.get(childName));
						// but do trigger a refresh on it
						Script existing = scripts.get(childName);
						if (((ResourceScript) existing).refresh() || this.descriptions == null || !this.descriptions.containsKey(existing)) {
							buildDescription(descriptions, existing);
						}
						else {
							descriptions.put(existing, this.descriptions.get(existing));
						}
					}
					else if (child instanceof ReadableResource) {
						Script script = new ResourceScript(this, charset, namespace, child.getName(), (ReadableResource) child, parser);
						scripts.put(ScriptUtils.getFullName(script), script);
						buildDescription(descriptions, script);
					}
				}
			}
		}
		return scripts;
	}
	
	@Override
	public Iterator<Script> iterator() {
		return new ArrayList<Script>(getScripts().values()).iterator();
	}

	@Override
	public Script getScript(String name) {
		return getScripts() == null ? null : getScripts().get(name);
	}

	private Map<String, Script> getScripts() {
		return scripts;
	}

	@Override
	public ParserProvider getParserProvider() {
		return parserProvider;
	}

	Charset getCharset() {
		return charset;
	}

	@Override
	public Resource resolve(String name) throws IOException {
		return ResourceUtils.resolve(root, name);
	}

	@Override
	public ScriptRepository getParent() {
		return parent;
	}

	@Override
	public void refresh() throws IOException {
		Map<Script, MethodDescription> descriptions = new HashMap<Script, MethodDescription>();
		scripts = scan(root, null, descriptions);
		this.descriptions = descriptions;
	}

	@Override
	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public ResourceContainer<?> getRoot() {
		return root;
	}

	@Override
	public Collection<MethodDescription> getDescriptions() {
		return descriptions == null ? new ArrayList<MethodDescription>() : descriptions.values();
	}

	private void buildDescription(Map<Script, MethodDescription> descriptions, Script script) {
		try {
			descriptions.put(script, new SimpleMethodDescription(script.getNamespace(), script.getName(), 
				script.getRoot() == null || script.getRoot().getContext() == null ? null : script.getRoot().getContext().getComment(), 
				script.getRoot() == null ? new ArrayList<ParameterDescription>() : ScriptUtils.getInputs(script), 
				script.getRoot() == null ? new ArrayList<ParameterDescription>() : ScriptUtils.getOutputs(script)));
		}
		catch (Exception e) {
			System.err.println("Could not get description for: " + script.getNamespace() + "." + script.getName());
			e.printStackTrace();
		}
	}

}
