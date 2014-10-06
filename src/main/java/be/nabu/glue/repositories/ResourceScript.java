package be.nabu.glue.repositories;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.Parser;
import be.nabu.glue.api.ResourceScriptRepository;
import be.nabu.glue.api.Script;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.utils.io.IOUtils;

public class ResourceScript implements Script {
	private ResourceScriptRepository repository;
	private ExecutorGroup root;
	private String name;
	private String namespace;
	private Parser parser;
	private Charset charset;

	ResourceScript(ResourceScriptRepository repository, Charset charset, String namespace, String name, ExecutorGroup root, Parser parser) {
		this.repository = repository;
		this.charset = charset;
		this.namespace = namespace;
		this.name = name;
		this.root = root;
		this.parser = parser;
	}

	@Override
	public ResourceScriptRepository getRepository() {
		return repository;
	}

	@Override
	public String getNamespace() {
		return namespace;
	}

	@Override
	public String getName() {
		// strip the extension
		return name.replaceAll("\\.[^.]+$", "");
	}

	@Override
	public ExecutorGroup getRoot() {
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

	String getPath(boolean includeExtension) {
		String path = includeExtension ? name : getName();
		if (namespace != null) {
			path = namespace.replace('.', '/') + "/" + path;
		}
		return path;
	}
	
	@Override
	public InputStream getSource() throws IOException {
		return IOUtils.toInputStream(((ReadableResource) repository.resolve(getPath(true))).getReadable());
	}

	@Override
	public InputStream getResource(String name) throws IOException {
		ReadableResource resource = (ReadableResource) repository.resolve(getPath(false) + "/" + name);
		return resource == null ? null : IOUtils.toInputStream(resource.getReadable());
	}
}