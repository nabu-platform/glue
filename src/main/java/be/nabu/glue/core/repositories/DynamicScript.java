package be.nabu.glue.core.repositories;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.Parser;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.core.impl.executors.SequenceExecutor;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.DetachableResource;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.utils.io.IOUtils;

public class DynamicScript implements Script {

	private ScriptRepository repository;
	private String namespace;
	private String name;
	private String content;
	private Charset charset;
	private ExecutorGroup root;
	private Parser parser;
	private ResourceContainer<?> resources;
	private List<String> result;
	
	public DynamicScript(String namespace, String name, ScriptRepository repository, Charset charset, ResourceContainer<?> resources) {
		this.namespace = namespace;
		this.name = name;
		this.repository = repository;
		this.charset = charset;
		// detach from parent if possible for security reasons
		this.resources = resources instanceof DetachableResource ? (ResourceContainer<?>) ((DetachableResource) resources).detach() : resources;
		this.parser = repository.getParserProvider().newParser(repository, name + ".glue");
	}
	
	@Override
	public Iterator<String> iterator() {
		if (result == null) {
			synchronized(this) {
				if (result == null) {
					List<String> result = new ArrayList<String>();
					for (Resource resource : resources) {
						result.add(resource.getName());
					}
					this.result = result;
				}
			}
		}
		return result.iterator();
	}

	@Override
	public ScriptRepository getRepository() {
		return repository;
	}

	@Override
	public String getNamespace() {
		return namespace;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ExecutorGroup getRoot() throws IOException, ParseException {
		if (root == null) {
			synchronized(this) {
				if (root == null) {
					if (content != null) {
						this.root = parser.parse(new StringReader(content));
					}
					else {
						root = new SequenceExecutor(null, null, null);
					}
				}
			}
		}
		return root;
	}

	@Override
	public Charset getCharset() {
		return charset;
	}

	@Override
	public Parser getParser() {
		return parser;
	}

	@Override
	public InputStream getSource() {
		return content == null ? null : new ByteArrayInputStream(content.getBytes(charset));
	}

	@Override
	public InputStream getResource(String name) throws IOException {
		Resource resolve = ResourceUtils.resolve(resources, name);
		return resolve instanceof ReadableResource ? IOUtils.toInputStream(((ReadableResource) resolve).getReadable()) : null;
	}

	public void setContent(String content) throws IOException, ParseException {
		this.content = content;
		// reset root so it is reparsed when necessary
		this.root = null;
	}
}
