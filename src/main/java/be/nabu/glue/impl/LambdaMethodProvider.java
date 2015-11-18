package be.nabu.glue.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.Lambda;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.MethodProvider;
import be.nabu.libs.evaluator.api.Operation;

public class LambdaMethodProvider implements MethodProvider {

	@Override
	public Operation<ExecutionContext> resolve(String name) {
		Map<String, Lambda> lambdasInScope = getLambdasInScope();
		return lambdasInScope.containsKey(name) ? lambdasInScope.get(name).getOperation() : null;
	}

	@Override
	public List<MethodDescription> getAvailableMethods() {
		Map<String, Lambda> lambdasInScope = getLambdasInScope();
		List<MethodDescription> descriptions = new ArrayList<MethodDescription>();
		for (String name : lambdasInScope.keySet()) {
			MethodDescription description = lambdasInScope.get(name).getDescription();
			if (description != null) {
				descriptions.add(description);
			}
		}
		return descriptions;
	}

	private Map<String, Lambda> getLambdasInScope() {
		Map<String, Lambda> methods = new HashMap<String, Lambda>();
		if (ScriptRuntime.getRuntime() != null) {
			Map<String, Object> pipeline = ScriptRuntime.getRuntime().getExecutionContext().getPipeline();
			for (String key : pipeline.keySet()) {
				if (pipeline.get(key) instanceof Lambda) {
					methods.put(key, (Lambda) pipeline.get(key));
				}
			}
		}
		return methods;
	}
	
}
