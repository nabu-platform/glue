package be.nabu.glue.repositories;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import be.nabu.glue.ScriptUtils;
import be.nabu.glue.api.Parser;
import be.nabu.glue.api.ParserProvider;
import be.nabu.glue.api.ResourceScriptRepository;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;

public class ScannableScriptRepository implements ResourceScriptRepository {

	private ParserProvider parserProvider;
	private ResourceContainer<?> root;
	private Map<String, Script> scripts;
	private Charset charset;
	private ScriptRepository parent;

	public ScannableScriptRepository(ScriptRepository parent, ResourceContainer<?> root, ParserProvider parserProvider, Charset charset) throws IOException {
		this.parent = parent;
		this.root = root;
		this.parserProvider = parserProvider;
		this.charset = charset;
		scripts = scan(root, null);
	}
	
	private Map<String, Script> scan(ResourceContainer<?> folder, String namespace) throws IOException {
		Map<String, Script> scripts = new HashMap<String, Script>();
		for (Resource child : folder) {
			if (child instanceof ResourceContainer) {
				scripts.putAll(scan((ResourceContainer<?>) child, namespace == null ? child.getName() : namespace + "." + child.getName()));
			}
			else {
				Parser parser = parserProvider.newParser(this, child.getName());
				if (parser != null) {
					Script script = new ResourceScript(this, charset, namespace, child.getName(), (ReadableResource) child, parser);
					scripts.put(ScriptUtils.getFullName(script), script);
				}
			}
		}
		return scripts;
	}
	
	@Override
	public Iterator<Script> iterator() {
		return getScripts().values().iterator();
	}

	@Override
	public Script getScript(String name) {
		return getScripts().get(name);
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
		scripts = scan(root, null);
	}
}
