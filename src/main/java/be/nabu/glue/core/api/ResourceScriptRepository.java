package be.nabu.glue.core.api;

import java.io.IOException;

import be.nabu.glue.api.ScriptRepository;
import be.nabu.libs.resources.api.Resource;

public interface ResourceScriptRepository extends ScriptRepository {
	public Resource resolve(String name) throws IOException;
}
