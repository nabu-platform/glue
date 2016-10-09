package be.nabu.glue.core.repositories;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.Parser;
import be.nabu.glue.api.Script;
import be.nabu.glue.core.api.ResourceScriptRepository;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.TimestampedResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;

public class ResourceScript implements Script {
	
	private ResourceScriptRepository repository;
	private ExecutorGroup root;
	private String name;
	private String namespace;
	private Parser parser;
	private Charset charset;
	private ReadableResource resource;
	private Date lastModified;
	private List<String> resources;

	ResourceScript(ResourceScriptRepository repository, Charset charset, String namespace, String name, ReadableResource resource, Parser parser) {
		this.repository = repository;
		this.charset = charset;
		this.namespace = namespace;
		this.name = name;
		this.resource = resource;
		this.parser = parser;
		if (resource instanceof TimestampedResource) {
			lastModified = ((TimestampedResource) resource).getLastModified();
		}
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
	public ExecutorGroup getRoot() throws IOException, ParseException {
		if (root == null) {
			ReadableContainer<ByteBuffer> readable = resource.getReadable();
			try {
				root = getParser().parse(IOUtils.toReader(IOUtils.wrapReadable(readable, charset)));
			}
			finally {
				readable.close();
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

	public void setRoot(ExecutorGroup root) {
		this.root = root;
	}
	
	public ReadableResource getResource() {
		return resource;
	}
	
	public boolean refresh() {
		if (resource instanceof TimestampedResource) {
			Date newLastModified = ((TimestampedResource) resource).getLastModified();
			if (newLastModified.after(lastModified)) {
				root = null;
				lastModified = newLastModified;
				return true;
			}
		}
		return false;
	}

	@Override
	public Iterator<String> iterator() {
		if (resources == null) {
			synchronized(this) {
				List<String> resources = new ArrayList<String>();
				try {
					Resource resourceDirectory = repository.resolve(getPath(false));
					if (resourceDirectory instanceof ResourceContainer) {
						scanResources((ResourceContainer<?>) resourceDirectory, null, resources);
					}
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
				this.resources = resources;
			}
		}
		return resources.iterator();
	}
	
	private void scanResources(ResourceContainer<?> directory, String path, List<String> resources) {
		for (Resource child : directory) {
			if (child.getName().startsWith(".")) {
				continue;
			}
			String childPath = path == null ? child.getName() : path + "/" + child.getName();
			if (child instanceof ReadableResource) {
				resources.add(childPath);
			}
			if (child instanceof ResourceContainer) {
				scanResources((ResourceContainer<?>) child, childPath, resources);
			}
		}
	}
	
	@Override
	public String toString() {
		return (namespace == null ? "" : namespace + ".") + getName();
	}
}