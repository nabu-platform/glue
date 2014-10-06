package be.nabu.glue.repositories;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.Principal;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import be.nabu.glue.api.Parser;
import be.nabu.glue.api.ParserProvider;
import be.nabu.glue.api.ResourceScriptRepository;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceRoot;
import be.nabu.utils.io.IOUtils;

public class TargetedScriptRepository implements ResourceScriptRepository {

	private URI base;
	private String[] extensions;
	private Map<String, Script> scripts = new HashMap<String, Script>();
	private ResourceFactory resourceFactory = ResourceFactory.getInstance();
	private Principal principal;
	private ParserProvider parserProvider;
	private Charset charset;
	private ScriptRepository parent;

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
		return scripts.values().iterator();
	}

	@Override
	public Script getScript(String name) throws IOException, ParseException {
		if (!scripts.containsKey(name)) {
			for (String extension : extensions) {
				String path = name.replace('.', '/') + "." + extension;
				URI child = URIUtils.getChild(base, path);
				ResourceRoot resource = resourceFactory.resolve(child, principal);
				if (resource != null) {
					Parser parser = parserProvider.newParser(this, path);
					int index = name.lastIndexOf('.');
					scripts.put(name, new ResourceScript(this, charset, index >= 0 ? name.substring(0, index) : null, index >= 0 ? name.substring(index + 1) : name, parser.parse(IOUtils.toReader(IOUtils.wrapReadable(((ReadableResource) resource).getReadable(), charset))), parser));
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
}
