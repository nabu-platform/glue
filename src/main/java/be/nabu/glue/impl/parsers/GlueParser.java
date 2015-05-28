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

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.VirtualScript;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.OutputFormatter;
import be.nabu.glue.api.Parser;
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

	public GlueParser(OperationProvider<ExecutionContext> operationProvider) {
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
					annotations.put(parts[0], parts.length >= 2 ? parts[1] : "true");
					continue;
				}
				int depth = getDepth(line);
				int originalSize = executorGroups.size();
				for (int i = depth; i < originalSize; i++) {
					executorGroups.pop();
				}
				if (!codeHasBegun) {
					executorGroups.push(new SequenceExecutor(null, new SimpleExecutorContext(lineNumber, null, lineComment.toString().isEmpty() ? null : lineComment.toString(), lineDescription.toString().isEmpty() ? null : lineDescription.toString(), null, annotations), null));
					annotations.clear();
					// clear the initial comment/description so it doesn't end up on the first line
					lineComment = new StringBuilder();
					lineDescription = new StringBuilder();
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
					if (line.charAt(index - 1) == '\\') {
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
					executorGroups.peek().getChildren().add(sequenceExecutor);
					executorGroups.push(sequenceExecutor);
				}
				else if (line.equals("sequence")) {
					SequenceExecutor sequenceExecutor = new SequenceExecutor(executorGroups.peek(), context, null);
					executorGroups.peek().getChildren().add(sequenceExecutor);
					executorGroups.push(sequenceExecutor);
				}
				else if (line.matches("^while[\\s]*\\(.*\\)$")) {
					line = line.replaceAll("^while[\\s]*\\((.*)\\)$", "$1");
					Operation<ExecutionContext> operation = analyzer.analyze(GlueQueryParser.getInstance().parse(line));
					WhileExecutor whileExecutor = new WhileExecutor(executorGroups.peek(), context, null, operation);
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
					executorGroups.peek().getChildren().add(forEachExecutor);
					executorGroups.push(forEachExecutor);
				}
				else if (line.matches("^break[\\s]*[0-9]+$") || line.equals("break")) {
					int breakCount =  line.matches("^break[\\s]*[0-9]+$") ? Integer.parseInt(line.replaceAll("^break[\\s]*([0-9]+)$", "$1")) : 1; 
					BreakExecutor breakExecutor = new BreakExecutor(executorGroups.peek(), context, null, breakCount);
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
					executorGroups.peek().getChildren().add(sequenceExecutor);
					executorGroups.push(sequenceExecutor);
				}
				else if (line.equals("default")) {
					if (!(executorGroups.peek() instanceof SwitchExecutor)) {
						throw new ParseException("A default can only exist inside a switch", 0);
					}
					SequenceExecutor sequenceExecutor = new SequenceExecutor(executorGroups.peek(), context, null);
					executorGroups.peek().getChildren().add(sequenceExecutor);
					executorGroups.push(sequenceExecutor);
				}
				else {
					// you can use multiline if you use a depth greater than the one on this line and there is no comment on this line
					String nextLine = null;
					while (comment == null && (nextLine = readLine(pushback)) != null) {
						if (getDepth(nextLine) > depth) {
							// no labels on appended lines
							if (nextLine.trim().matches("^[\\w\\s]+:.*")) {
								pushback.pushback(IOUtils.wrap(nextLine + "\n"));
								break;
							}
							// a comment will stop the appending
							index = nextLine.indexOf('#');
							if (index >= 0) {
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
					// check if there is a variable assignment on the line
					if (line.matches("(?s)^[\\w ]+[\\s?]*=.*")) {
						index = line.indexOf('=');
						variableName = line.substring(0, index).trim();
						line = line.substring(index + 1).trim();
						// if the variablename ends with a "?" you wrote something like "myvar ?= test" which means only overwrite it if it doesn't exist yet
						if (variableName.endsWith("?")) {
							overwriteIfExists = false;
							variableName = variableName.substring(0, variableName.length() - 1).trim();
						}
						// if there is a space, you are probably defining a type
						index = variableName.indexOf(' ');
						if (index > 0) {
							type = variableName.substring(0, index);
							variableName = variableName.substring(index + 1);
						}
					}
					Operation<ExecutionContext> operation = analyzer.analyze(GlueQueryParser.getInstance().parse(line));
					EvaluateExecutor evaluateExecutor = new EvaluateExecutor(executorGroups.peek(), context, operationProvider, null, variableName, type, operation, overwriteIfExists);
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
	
	private int getDepth(String line) {
		int depth = 1;
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
		Pattern pattern = Pattern.compile("(?<!\\\\)\\$\\{([^}]+)\\}");
		Matcher matcher = pattern.matcher(value);
		try {
			Converter converter = ConverterFactory.getInstance().getConverter();
			while (matcher.find()) {
				String query = matcher.group().replaceAll(pattern.pattern(), "$1");
				Operation<ExecutionContext> operation = analyzer.analyze(GlueQueryParser.getInstance().parse(query));
				Object evaluatedResult = operation.evaluate(context);
				if (!allowNull && evaluatedResult == null) {
					throw new IllegalArgumentException("Can not replace the query " + query);
				}
				String result = converter.convert(evaluatedResult, String.class);
				if (result == null && evaluatedResult != null) {
					throw new RuntimeException("Can not convert the result of the query to string: " + query);
				}
				// don't allow empty results, they are likely due to an oversight
				// if you really need an empty string, you can still force it
				if (result != null) {
					value = value.replaceAll(Pattern.quote(matcher.group()), Matcher.quoteReplacement(result));
				}
			}
			return value.replaceAll("\\\\(\\$\\{[^}]+\\})", "$1");
		}
		catch (ParseException e) {
			throw new IllegalArgumentException(e);
		}
		catch (EvaluationException e) {
			throw new RuntimeException(e);
		}
	}
	
	private String substituteScripts(String value, ExecutionContext context, boolean allowNull) {
		Pattern pattern = Pattern.compile("(?s)(?<!\\\\)\\$\\{\\{(.*?)\\}\\}");
		Matcher matcher = pattern.matcher(value);
		try {
			while (matcher.find()) {
				String script = matcher.group().replaceAll(pattern.pattern(), "$1");
				ScriptRuntime fork = ScriptRuntime.getRuntime().fork(new VirtualScript(ScriptRuntime.getRuntime().getScript(), script));
				StringWriter log = new StringWriter();
				OutputFormatter buffer = new SimpleOutputFormatter(log, false);
				fork.setFormatter(buffer);
				fork.run();
				if (fork.getException() != null) {
					throw new RuntimeException(fork.getException());
				}
				value = value.replaceAll(Pattern.quote(matcher.group()), Matcher.quoteReplacement(log.toString()));
			}
			return value;
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
	
}