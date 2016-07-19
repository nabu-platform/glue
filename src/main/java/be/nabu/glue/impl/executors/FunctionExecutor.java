package be.nabu.glue.impl.executors;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.ScriptUtils;
import be.nabu.glue.api.AssignmentExecutor;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionException;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.Lambda;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.api.Parser;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.impl.ForkedExecutionContext;
import be.nabu.glue.impl.LambdaImpl;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.impl.SimpleParameterDescription;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.base.BaseMethodOperation;

public class FunctionExecutor extends BaseExecutor implements AssignmentExecutor, ExecutorGroup {

	private String variableName;
	private boolean overwriteIfExists;
	private List<Executor> children = new ArrayList<Executor>();
	
	// cached values
	private SequenceExecutor sequence;
	private List<ParameterDescription> inputs, outputs;
	
	private boolean useActualPipeline;

	public FunctionExecutor(ExecutorGroup parent, ExecutorContext context, Operation<ExecutionContext> condition, String variableName, boolean overwriteIfExists, Executor...children) {
		super(parent, context, condition);
		this.variableName = variableName;
		this.overwriteIfExists = overwriteIfExists;
		this.children.addAll(Arrays.asList(children));
	}

	@Override
	public boolean isOverwriteIfExists() {
		return overwriteIfExists;
	}

	@Override
	public String getVariableName() {
		return variableName;
	}

	@Override
	public String getOptionalType() {
		return "lambda";
	}

	@Override
	public boolean isList() {
		return false;
	}

	@Override
	public void execute(ExecutionContext context) throws ExecutionException {
		Script script = getScript(ScriptRuntime.getRuntime().getScript());
		try {
			Object object = context.getPipeline().get(variableName);
			if (overwriteIfExists || object == null) {
				Map<String, Object> captured = useActualPipeline ? context.getPipeline() : new HashMap<String, Object>(context.getPipeline());
				Lambda lambda = new LambdaImpl(
					new SimpleMethodDescription(script.getNamespace(), script.getName(), getContext().getComment(), getInputs(), getOutputs()), 
					new FunctionOperation(getSequence(), useActualPipeline ? context.getPipeline() : null), 
					captured,
					useActualPipeline
				);
				// for recursiveness...
				captured.put(variableName, lambda);
				context.getPipeline().put(variableName, lambda);
			}
		}
		catch (ParseException e) {
			throw new ExecutionException(e);
		}
		catch (IOException e) {
			throw new ExecutionException(e);
		}
	}
	
	/**
	 * The lambda operation does all the heavy lifting of the parameters etc
	 */
	public static class FunctionOperation extends BaseMethodOperation<ExecutionContext> {
		private Executor executor;
		private Map<String, Object> capturedContext;
		public FunctionOperation(Executor executor, Map<String, Object> capturedContext) {
			this.executor = executor;
			this.capturedContext = capturedContext;
		}
		@Override
		public void finish() throws ParseException {
			// do nothing
		}
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			ForkedExecutionContext forked = new ForkedExecutionContext(context, true);
			try {
				executor.execute(forked);
			}
			catch (ExecutionException e) {
				throw new EvaluationException(e);
			}
			Map<String, Object> resultMap = new HashMap<String, Object>();
			Map<String, Object> persistMap = new HashMap<String, Object>();
			buildReturn(resultMap, forked, executor, persistMap);
			if (capturedContext != null && !persistMap.isEmpty()) {
				synchronized(capturedContext) {
					capturedContext.putAll(persistMap);
				}
			}
			if (resultMap.isEmpty()) {
				return forked;
			}
			else if (resultMap.size() == 1) {
				return resultMap.values().iterator().next();
			}
			else {
				return resultMap;
			}
		}
		
		private void buildReturn(Map<String, Object> output, ExecutionContext result, Executor executor, Map<String, Object> persistMap) {
			if (executor instanceof AssignmentExecutor) {
				if (executor.getContext() != null && executor.getContext().getAnnotations() != null && executor.getContext().getAnnotations().containsKey("return")) {
					String variableName = ((AssignmentExecutor) executor).getVariableName();
					output.put(variableName, result.getPipeline().get(variableName));
				}
				if (capturedContext != null && executor.getContext() != null && executor.getContext().getAnnotations() != null && executor.getContext().getAnnotations().containsKey("persist")) {
					String variableName = ((AssignmentExecutor) executor).getVariableName();
					persistMap.put(variableName, result.getPipeline().get(variableName));
				}
			}
			else if (executor instanceof ExecutorGroup) {
				for (Executor child : ((ExecutorGroup) executor).getChildren()) {
					buildReturn(output, result, child, persistMap);
				}
			}
		}
		public Executor getExecutor() {
			return executor;
		}
	}

	private List<ParameterDescription> getInputs() throws ParseException, IOException {
		if (inputs == null) {
			inputs = ScriptUtils.getParameters(getSequence(), true, true);
			if (!inputs.isEmpty() && inputs.get(inputs.size() - 1).isList() && inputs.get(inputs.size() - 1) instanceof SimpleParameterDescription) {
				((SimpleParameterDescription) inputs.get(inputs.size() - 1)).setVarargs(true);
			}
		}
		return inputs;
	}
	
	private List<ParameterDescription> getOutputs() throws ParseException, IOException {
		if (outputs == null) {
			outputs = ScriptUtils.getParameters(getSequence(), true, false);
		}
		return outputs;
	}

	private Script getScript(final Script parent) {
		Script script = new Script() {
			@Override
			public Iterator<String> iterator() {
				return parent.iterator();
			}
			@Override
			public ScriptRepository getRepository() {
				return parent.getRepository();
			}
			@Override
			public String getNamespace() {
				return parent.getNamespace();
			}
			@Override
			public String getName() {
				return parent.getName() + "$" + variableName + ":" + hashCode();
			}
			@Override
			public ExecutorGroup getRoot() throws IOException, ParseException {
				return getSequence();
			}
			@Override
			public Charset getCharset() {
				return parent.getCharset();
			}
			@Override
			public Parser getParser() {
				return parent.getParser();
			}
			@Override
			public InputStream getSource() throws IOException {
				return null;
			}
			@Override
			public InputStream getResource(String name) throws IOException {
				return parent.getResource(name);
			}
		};
		return script;
	}

	private SequenceExecutor getSequence() {
		if (sequence == null) {
			sequence = new SequenceExecutor(null, getContext(), null);
			sequence.getChildren().addAll(children);
		}
		return sequence;
	}
	
	@Override
	public List<Executor> getChildren() {
		return children;
	}

	public boolean isUseActualPipeline() {
		return useActualPipeline;
	}

	public void setUseActualPipeline(boolean useActualPipeline) {
		this.useActualPipeline = useActualPipeline;
	}
	
}
