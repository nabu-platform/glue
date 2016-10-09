package be.nabu.glue.core.api;

import be.nabu.glue.api.ScriptRepository;

public interface GroupedScriptRepository extends ScriptRepository {
	public String getGroup();
}
