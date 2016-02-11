package be.nabu.glue.impl.parsers;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import be.nabu.glue.DynamicScript;
import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.VirtualScript;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.Parser;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.glue.impl.GlueQueryParser;
import be.nabu.glue.impl.SimpleExecutorContext;
import be.nabu.glue.impl.executors.BreakExecutor;
import be.nabu.glue.impl.executors.EvaluateExecutor;
import be.nabu.glue.impl.executors.ForEachExecutor;
import be.nabu.glue.impl.executors.SequenceExecutor;
import be.nabu.glue.impl.executors.SwitchExecutor;
import be.nabu.glue.impl.executors.WhileExecutor;
import be.nabu.glue.impl.formatters.SimpleOutputFormatter;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.PathAnalyzer;
import be.nabu.libs.evaluator.api.Analyzer;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.api.OperationProvider;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.CharBuffer;
import be.nabu.utils.io.api.DelimitedCharContainer;
import be.nabu.utils.io.api.PushbackContainer;
import be.nabu.utils.io.api.ReadableContainer;

public class GlueParser implements Parser {
	
	private Analyzer<ExecutionContext> analyzer;
	private OperationProvider<ExecutionContext> operationProvider;
	private ScriptRepository repository;
	
	/**
	 * If explicit header is turned on, anything at the top of a script is by default bound to the first line of code _except_ if an explicit empty line is added to separate script headers from first line headers
	 * If set to false (default), anything at the top of the script is by default bound to the script level and not the first line of code unless explicit empty line is used
	 */
	private boolean defaultHeaderToScript = Boolean.parseBoolean(System.getProperty("glue.defaultHeaderToScript", "false"));

	public GlueParser(ScriptRepository repository, OperationProvider<ExecutionContext> operationProvider) {
		this.repository = repository;
		this.operationProvider = operationProvider;
		this.analyzer = new PathAnalyzer<ExecutionContext>(operationProvider);
	}
	
	private String readLine(ReadableContainer<CharBuffer> reader) throws IOException {
		DelimitedCharContainer delimited = IOUtils.delimit(reader, "\n");
		String line = IOUtils.toString(delimited).replace("\r", "");
		return line.isEmpty() && !delimited.isDelimiterFound() ? null : line;
	}
	
	@Override
	public ExecutorGroup parse(Reader reader) throws IOException, ParseException {
		ReadableContainer<CharBuffer> container = IOUtils.wrap(reader);
		container = IOUtils.bufferReadable(container, IOUtils.newCharBuffer(409600, true));
		String line = null;
		int lineNumber = -1;
		Stack<ExecutorGroup> executorGroups = new Stack<ExecutorGroup>();
		StringBuilder lineComment = new StringBuilder();
		StringBuilder lineDescription = new StringBuilder();
		boolean codeHasBegun = false;
		PushbackContainer<CharBuffer> pushback = IOUtils.pushback(container);
		// note that how the parsing is done now, you can NOT set annotations on the first line!
		// the annotations before the first line will be set on the root instead
		Map<String, String> annotations = new HashMap<String, String>();
		int lastPosition = 0;
		// keeps track of the "offset" of the depth over the script
		int depthOffset = -1;
		try {
			while ((line = readLine(pushback)) != null) {
				// add 1 for the linefeed that separates the last line and this line except if we are at the start
				// the line feeds are generated deterministically by the glue formatter, they are not system dependent
				int currentPosition = lastPosition + (lastPosition == 0 ? 0 : 1) + line.length();
				
				lineNumber++;
				// don't reduce depth if it is empty or a comment 
				if (line.trim().isEmpty() && codeHasBegun) {
					continue;
				}
				else if (line.trim().startsWith("#")) {
					String comment = line.trim().substring(1);
					// double hashtags indicate a description
					if (comment.startsWith("#")) {
						if (!lineDescription.toString().isEmpty()) {
							lineDescription.append(System.getProperty("line.separator"));
						}
						lineDescription.append(comment.substring(1).trim());
					}
					// if there is no code yet, this is assumed to the a comment block for the root, this allows you to add a description of the script
					else {
						if (!lineComment.toString().isEmpty()) {
							lineComment.append(System.getProperty("line.separator"));
						}
						lineComment.append(comment.trim());
					}
					continue;
				}
				// this allows you to set annotations on the next executor context
				else if (line.trim().startsWith("@")) {
					line = line.trim().substring(1).trim();
					String [] parts = line.split("[\\s=]+", 2);
					annotations.put(parts[0], parts.length >= 2 ? parts[1] : null);
					continue;
				}
				int depth = getDepth(line);
				// you can reduce the depth
				if (depthOffset < 0 || depthOffset > depth) {
					depthOffset = depth;
				}
				// note that the root executor is the sequence itself
				while(depth - depthOffset < executorGroups.size() - 1) {
					executorGroups.pop();
				}
				if (!codeHasBegun) {
					// if we always want to bind to the script or the line is empty (denoting an explicit script binding)
					// bind it all at the script level
					if (defaultHeaderToScript || line.trim().isEmpty()) {
						executorGroups.push(new SequenceExecutor(null, new SimpleExecutorContext(lineNumber, null, lineComment.toString().isEmpty() ? null : lineComment.toString(), lineDescription.toString().isEmpty() ? null : lineDescription.toString(), null, annotations), null));
						annotations.clear();
						// clear the initial comment/description so it doesn't end up on the first line
						lineComment = new StringBuilder();
						lineDescription = new StringBuilder();
					}
					// leave it for the first line
					else {
						executorGroups.push(new SequenceExecutor(null, new SimpleExecutorContext(lineNumber, null, null, null, null, new HashMap<String, String>()), null));
					}
				}
				codeHasBegun = true;
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				String label = null;
				String variableName = null;
				String comment = null;
				boolean overwriteIfExists = true;
				
				// check if there is a comment on the line
				int index = -1;
				while ((index = line.indexOf('#', index)) >= 0) {
					// check for escape character
					if (index > 0 && line.charAt(index - 1) == '\\') {
						line = line.substring(0, index - 1) + line.substring(index);
					}
					else {
						comment = line.substring(index + 1);
						// double hashtags indicate a description
						if (comment.startsWith("#")) {
							if (!lineDescription.toString().isEmpty()) {
								lineDescription.append(System.getProperty("line.separator"));
							}
							lineDescription.append(comment.substring(1).trim());
						}
						// if there is no code yet, this is assumed to the a comment block for the root, this allows you to add a description of the script
						else {
							if (!lineComment.toString().isEmpty()) {
								lineComment.append(System.getProperty("line.separator"));
							}
							lineComment.append(comment.trim());
						}
						line = line.substring(0, index).trim();
					}
				}
				// check if there is a label on the line
				if (line.matches("^[\\w\\s.]+:.*")) {
					index = line.indexOf(':');
					if (index >= 0) {
						label = line.substring(0, index).trim();
						line = line.substring(index + 1).trim();
					}
				}
				SimpleExecutorContext context = new SimpleExecutorContext(lineNumber, label, lineComment.toString().isEmpty() ? null : lineComment.toString(), lineDescription.toString().isEmpty() ? null : lineDescription.toString(), line, annotations);
				context.setStartPosition(lastPosition);
				context.setEndPosition(currentPosition);
				// clear it for the next comment/description
				lineComment = new StringBuilder();
				lineDescription = new StringBuilder();
				annotations.clear();
				if (line.matches("^if[\\s]*\\(.*\\)$")) {
					line = line.replaceAll("^if[\\s]*\\((.*)\\)$", "$1");
					SequenceExecutor sequenceExecutor = new SequenceExecutor(executorGroups.peek(), context, analyzer.analyze(GlueQueryParser.getInstance().parse(line)));
					sequenceExecutor.setOperationProvider(operationProvider);
					executorGroups.peek().getChildren().add(sequenceExecutor);
					executorGroups.push(sequenceExecutor);
				}
				else if (line.equals("sequence")) {
					SequenceExecutor sequenceExecutor = new SequenceExecutor(executorGroups.peek(), context, null);
					sequenceExecutor.setOperationProvider(operationProvider);
					executorGroups.peek().getChildren().add(sequenceExecutor);
					executorGroups.push(sequenceExecutor);
				}
				else if (line.matches("^while[\\s]*\\(.*\\)$")) {
					line = line.replaceAll("^while[\\s]*\\((.*)\\)$", "$1");
					Operation<ExecutionContext> operation = analyzer.analyze(GlueQueryParser.getInstance().parse(line));
					WhileExecutor whileExecutor = new WhileExecutor(executorGroups.peek(), context, null, operation);
					whileExecutor.setOperationProvider(operationProvider);
					executorGroups.peek().getChildren().add(whileExecutor);
					executorGroups.push(whileExecutor);
				}
				else if (line.matches("^for[\\s]*\\(.*\\)$")) {
					line = line.replaceAll("^for[\\s]*\\((.*)\\)$", "$1");
					index = line.indexOf(':');
					variableName = "$value";
					String indexName = "$index";
					if (index >= 0) {
						variableName = line.substring(0, index).trim();
						line = line.substring(index + 1).trim();
					}
					Operation<ExecutionContext> operation = analyzer.analyze(GlueQueryParser.getInstance().parse(line));
					ForEachExecutor forEachExecutor = new ForEachExecutor(executorGroups.peek(), context, null, operation, variableName, indexName);
					forEachExecutor.setOperationProvider(operationProvider);
					executorGroups.peek().getChildren().add(forEachExecutor);
					executorGroups.push(forEachExecutor);
				}
				else if (line.matches("^break[\\s]*[0-9]+$") || line.equals("break")) {
					int breakCount =  line.matches("^break[\\s]*[0-9]+$") ? Integer.parseInt(line.replaceAll("^break[\\s]*([0-9]+)$", "$1")) : 1; 
					BreakExecutor breakExecutor = new BreakExecutor(executorGroups.peek(), context, null, breakCount);
					breakExecutor.setOperationProvider(operationProvider);
					executorGroups.peek().getChildren().add(breakExecutor);
				}
				else if (line.matches("^switch[\\s]*\\(.*\\)$") || line.equals("switch")) {
					variableName = "$value";
					Operation<ExecutionContext> operation = null;
					if (!line.equals("switch")) {
						line = line.replaceAll("^switch[\\s]*\\((.*)\\)$", "$1");
						operation = analyzer.analyze(GlueQueryParser.getInstance().parse(line));
					}
					SwitchExecutor switchExecutor = new SwitchExecutor(executorGroups.peek(), context, null, variableName, operation);
					switchExecutor.setOperationProvider(operationProvider);
					executorGroups.peek().getChildren().add(switchExecutor);
					executorGroups.push(switchExecutor);
				}
				else if (line.matches("^case[\\s]*\\(.*\\)$")) {
					line = line.replaceAll("^case[\\s]*\\((.*)\\)$", "$1");
					if (!(executorGroups.peek() instanceof SwitchExecutor)) {
						throw new ParseException("A case can only exist inside a switch", 0);
					}
					Operation<ExecutionContext> operation = analyzer.analyze(GlueQueryParser.getInstance().parse("$value == (" + line + ")"));
					SequenceExecutor sequenceExecutor = new SequenceExecutor(executorGroups.peek(), context, operation);
					sequenceExecutor.setOperationProvider(operationProvider);
					executorGroups.peek().getChildren().add(sequenceExecutor);
					executorGroups.push(sequenceExecutor);
				}
				else if (line.equals("default")) {
					if (!(executorGroups.peek() instanceof SwitchExecutor)) {
						throw new ParseException("A default can only exist inside a switch", 0);
					}
					SequenceExecutor sequenceExecutor = new SequenceExecutor(executorGroups.peek(), context, null);
					sequenceExecutor.setOperationProvider(operationProvider);
					executorGroups.peek().getChildren().add(sequenceExecutor);
					executorGroups.push(sequenceExecutor);
				}
				else {
					// you can use multiline if you use a depth greater than the one on this line and there is no comment on this line
					String nextLine = null;
					while (comment == null && (nextLine = readLine(pushback)) != null) {
						if (getDepth(nextLine) > depth || nextLine.trim().isEmpty()) {
							// a comment will stop the appending
							index = -1;
							while ((index = nextLine.indexOf('#', index)) >= 0) {
								// check for escape character
								if (index > 0 && nextLine.charAt(index - 1) == '\\') {
									nextLine = nextLine.substring(0, index - 1) + nextLine.substring(index);
								}
								else if (index >= 0) {
									comment = nextLine.substring(index + 1).trim();
									if (comment.startsWith("#")) {
										comment = comment.substring(1).trim();
										if (context.getDescription() == null) {
											context.setDescription(comment);
										}
										else {
											context.setDescription(context.getDescription() + System.getProperty("line.separator") + comment);
										}
									}
									else {
										if (context.getComment() == null) {
											context.setComment(comment.trim());
										}
										else {
											context.setComment(context.getComment() + System.getProperty("line.separator") + comment.trim());
										}
									}
									nextLine = nextLine.substring(0, index).trim();
								}
							}
							// append with spaces intact, this should not bother the query parser but may allow reconstruction of multilines later on
							line += "\n" + nextLine;
							// increase the position as well
							currentPosition += 1 + nextLine.length();
							context.setEndPosition(currentPosition);
							// up the line number so it matches
							lineNumber++;
						}
						else {
							pushback.pushback(IOUtils.wrap(nextLine + "\n"));
							break;
						}
					}
					context.setLine(line);
					String type = null;
					boolean mustBeArray = false;
					// check if there is a variable assignment on the line
					// the first regex checks only for the variable name while the second allows for an optional type
					if (line.matches("(?s)^[\\w]+[\\s?]*=.*") || line.matches("(?s)^[\\w.]*([\\s]*\\[\\]|)[\\s]+[\\w]+[\\s?]*=.*")) {
						index = line.indexOf('=');
						variableName = line.substring(0, index).trim();
						line = line.substring(index + 1).trim();
						// if the variablename ends with a "?" you wrote something like "myvar ?= test" which means only overwrite it if it doesn't exist yet
						if (variableName.endsWith("?")) {
							overwriteIfExists = false;
							variableName = variableName.substring(0, variableName.length() - 1).trim();
						}
						// if there is a space, you are probably defining a type
						index = variableName.lastIndexOf(' ');
						if (index > 0) {
							type = variableName.substring(0, index).trim();
							if (type.endsWith("[]")) {
								type = type.substring(0, type.length() - 2).trim();
								if (type.isEmpty()) {
									type = null;
								}
								mustBeArray = true;
							}
							variableName = variableName.substring(index + 1);
						}
					}
					Operation<ExecutionContext> operation = analyzer.analyze(GlueQueryParser.getInstance().parse(line));
					EvaluateExecutor evaluateExecutor = new EvaluateExecutor(executorGroups.peek(), context, repository, null, variableName, type, operation, overwriteIfExists);
					evaluateExecutor.setOperationProvider(operationProvider);
					evaluateExecutor.setList(mustBeArray);
					executorGroups.peek().getChildren().add(evaluateExecutor);
				}
				lastPosition = currentPosition;
			}
		}
		catch (ParseException e) {
			throw new ParseException("Could not parse line " + (lineNumber + 1) + " [" + line + "]: " + e.getMessage(), lineNumber);
		}
		// there can be multiple elements on the stack at the end if you stopped the script in for example an "if" element
		ExecutorGroup root = executorGroups.isEmpty() ? new SequenceExecutor(null, null, null) : null;
		while (executorGroups.size() > 0) {
			root = executorGroups.pop();
		}
		return root;
	}
	
	public Operation<ExecutionContext> analyze(String line) throws ParseException {
		return line == null || line.isEmpty() ? null : analyzer.analyze(GlueQueryParser.getInstance().parse(line));
	}
	
	public static int getDepth(String line) {
		int depth = 0;
		for (int i = 0; i < line.length(); i++) {
			if (line.charAt(i) == '\t' || line.charAt(i) == ' ') {
				depth++;
			}
			else {
				break;
			}
		}
		return depth;
	}

	@Override
	public String substitute(String value, ExecutionContext context, boolean allowNull) {
		value = substituteScripts(value, context, allowNull);
		value = substituteOperations(value, context, allowNull);
		return value;
	}

	private String substituteOperations(String value, ExecutionContext context, boolean allowNull) {
		Pattern pattern = Pattern.compile("(?<!\\\\)\\$\\{");
		Matcher matcher = pattern.matcher(value);
		try {
			Converter converter = ConverterFactory.getInstance().getConverter();
			String target = value;
			while (matcher.find()) {
				int depth = 0;
				String query = null;
				// we start after the capturing group (we always capture ${ which as a length of 2)
				int contentStart = matcher.start() + 2;
				for (int i = contentStart; i < value.length(); i++) {
					if (value.charAt(i) == '{') {
						depth++;
					}
					else if (value.charAt(i) == '}') {
						if (depth == 0) {
							query = value.substring(contentStart, i);
							break;
						}
						else {
							depth--;
						}
					}
				}
				if (query == null) {
					throw new IllegalArgumentException("The opening ${ is missing an end tag");
				}
				Operation<ExecutionContext> operation = analyzer.analyze(GlueQueryParser.getInstance().parse(query));
				Object evaluatedResult = operation.evaluate(context);
				if (!allowNull && evaluatedResult == null) {
					throw new IllegalArgumentException("Can not replace the query " + query);
				}
				String result = converter.convert(evaluatedResult, String.class);
				if (result == null && evaluatedResult != null) {
					throw new RuntimeException("Can not convert the result of the query to string: " + query);
				}
				if (result != null) {
					target = target.replaceAll(Pattern.quote("${" + query + "}"), Matcher.quoteReplacement(result));
				}
			}
			// unescape explicitly escaped embeds
			return target.replaceAll("\\\\(\\$\\{)", "$1");
		}
		catch (ParseException e) {
			throw new IllegalArgumentException(e);
		}
		catch (EvaluationException e) {
			throw new RuntimeException(e);
		}
	}
	
	private String substituteScripts(String value, ExecutionContext context, boolean allowNull) {
		Pattern pattern = Pattern.compile("(?s)(?<!\\\\)\\$\\{\\{[\\s]*");
		Matcher matcher = pattern.matcher(value);
		String target = value;
		try {
			while (matcher.find()) {
				int depth = 0;
				String script = null;
				// we start after the capturing group (we always capture ${{ which as a length of 3)
				int contentStart = matcher.start() + 3;
				for (int i = contentStart; i < value.length() - 1; i++) {
					if (value.charAt(i) == '{') {
						depth++;
					}
					else if (value.charAt(i) == '}') {
						// we need a double } to end
						if (depth == 0 && value.charAt(i + 1) == '}') {
							script = value.substring(contentStart, i);
							break;
						}
						else if (depth > 0) {
							depth--;
						}
					}
				}
				if (script == null) {
					throw new IllegalArgumentException("The opening ${{ is missing an end tag");
				}
				// remove empty lines at front, they might throw off the depthOffset!
				script = script.replaceAll("(?s)^[\r\n]+", "");
				ScriptRuntime fork = ScriptRuntime.getRuntime() != null
					? ScriptRuntime.getRuntime().fork(new VirtualScript(ScriptRuntime.getRuntime().getScript(), script))
					: new ScriptRuntime(new DynamicScript(repository, this, script), context, new HashMap<String, Object>());
				StringWriter log = new StringWriter();
				SimpleOutputFormatter buffer = new SimpleOutputFormatter(log, false);
				if (ScriptRuntime.getRuntime() != null) {
					buffer.setParent(ScriptRuntime.getRuntime().getFormatter());
				}
				fork.setFormatter(buffer);
				fork.run();
				if (fork.getException() != null) {
					throw new RuntimeException(fork.getException());
				}
				target = target.replaceAll("\\$\\{\\{[\\s]*" + Pattern.quote(script) + "[\\s]*\\}\\}", Matcher.quoteReplacement(log.toString()));
			}
			return target;
		}
		catch (ParseException e) {
			throw new IllegalArgumentException(e);
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Can not parse embedded script", e);
		}
	}

	public OperationProvider<ExecutionContext> getOperationProvider() {
		return operationProvider;
	}

	public ScriptRepository getRepository() {
		return repository;
	}
	
}