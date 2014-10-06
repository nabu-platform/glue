package be.nabu.glue.api;

import java.io.IOException;

import be.nabu.libs.resources.api.Resource;

public interface ResourceScriptRepository extends ScriptRepository {
	public Resource resolve(String name) throws IOException;
}
