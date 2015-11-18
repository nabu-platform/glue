package be.nabu.glue.api;

import java.util.Map;

public interface EnclosedLambda extends Lambda {
	public Map<String, Object> getEnclosedContext();
}
