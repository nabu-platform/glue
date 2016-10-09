package be.nabu.glue.core.api;

import java.util.Map;

public interface EnclosedLambda extends Lambda {
	public Map<String, Object> getEnclosedContext();
	public boolean isMutable();
}
