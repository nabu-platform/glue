package be.nabu.glue.impl;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.impl.executors.EvaluateExecutor;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.evaluator.QueryPart.Type;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider.OperationType;

public class ReferenceManager {
	
	public Map<String, List<Script>> calculateDependencies(ScriptRepository repository) throws IOException, ParseException {
		Map<String, List<Script>> dependencies = new HashMap<String, List<Script>>();
		Map<Script, Set<String>> references = calculateReferences(repository);
		for (Script script : references.keySet()) {
			for (String reference : references.get(script)) {
				if (!dependencies.containsKey(reference)) {
					dependencies.put(reference, new ArrayList<Script>());
				}
				dependencies.get(reference).add(script);
			}
		}
		return dependencies;
	}
	
	public Map<Script, Set<String>> calculateReferences(ScriptRepository repository) throws IOException {
		Map<Script, Set<String>> references = new HashMap<Script, Set<String>>();
		for (Script script : repository) {
			try {
				references.put(script, analyze(script.getRoot()));
			}
			catch (Exception e) {
				e.printStackTrace(System.err);
			}
		}
		return references;
	}
	
	private Set<String> analyze(ExecutorGroup group) {
		Set<String> references = new HashSet<String>();
		for (Executor child : group.getChildren()) {
			if (child instanceof EvaluateExecutor) {
				EvaluateExecutor executor = ((EvaluateExecutor) child);
				references.addAll(analyze(executor.getOperation()));
			}
		}
		return references;
	}
	
	private Set<String> analyze(Operation<?> operation) {
		Set<String> methods = new HashSet<String>();
		if (operation.getType() == OperationType.METHOD) {
			methods.add(operation.getParts().get(0).getContent().toString());
		}
		for (QueryPart part : operation.getParts()) {
			if (part.getType() == Type.OPERATION) { 
				methods.addAll(analyze((Operation<?>) part.getContent()));
			}
		}
		return methods;
	}
}
