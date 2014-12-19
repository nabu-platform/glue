package be.nabu.glue.impl.parsers;

import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import be.nabu.glue.ScriptRuntime;
import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.Parser;
import be.nabu.glue.impl.GlueQueryParser;
import be.nabu.glue.impl.SimpleExecutorContext;
import be.nabu.glue.impl.executors.EvaluateExecutor;
import be.nabu.glue.impl.executors.ForEachExecutor;
import be.nabu.glue.impl.executors.SequenceExecutor;
import be.nabu.glue.impl.executors.SwitchExecutor;
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

	public GlueParser(OperationProvider<ExecutionContext> operationProvider) {
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
		StringBuilder rootComment = new StringBuilder();
		boolean codeHasBegun = false;
		PushbackContainer<CharBuffer> pushback = IOUtils.pushback(container);
		// note that how the parsing is done now, you can NOT set annotations on the first line!
		// the annotations before the first line will be set on the root instead
		Map<String, String> annotations = new HashMap<String, String>();
		try {
			while ((line = readLine(pushback)) != null) {
				lineNumber++;
				// don't reduce depth if it is empty or a comment 
				if (line.trim().isEmpty()) {
					continue;
				}
				else if (line.trim().startsWith("#")) {
					// if there is no code yet, this is assumed to the a comment block for the root, this allows you to add a description of the script
					if (!codeHasBegun) {
						if (!rootComment.toString().isEmpty()) {
							rootComment.append(System.getProperty("line.separator"));
						}
						rootComment.append(line.trim().substring(1).trim());
					}
					continue;
				}
				// this allows you to set annotations on the next executor context
				else if (line.trim().startsWith("@")) {
					line = line.trim().substring(1).trim();
					int index = line.indexOf('=');
					line.indexOf('=');
					annotations.put(index >= 0 ? line.substring(0, index).trim() : line, index >= 0 ? line.substring(index + 1).trim() : "true");
					continue;
				}
				int depth = getDepth(line);
				int originalSize = executorGroups.size();
				for (int i = depth; i < originalSize; i++) {
					executorGroups.pop();
				}
				if (!codeHasBegun) {
					executorGroups.push(new SequenceExecutor(null, new SimpleExecutorContext(lineNumber, null, rootComment.toString().isEmpty() ? null : rootComment.toString(), null, annotations), null));
					annotations.clear();
				}
				codeHasBegun = true;
				line = line.trim();
				String label = null;
				String variableName = null;
				String comment = null;
				boolean overwriteIfExists = true;
				
				// check if there is a comment on the line
				int index = line.indexOf('#');
				if (index >= 0) {
					// check for escape character
					if (line.charAt(index - 1) == '\\') {
						line = line.substring(0, index - 1) + line.substring(index);
					}
					else {
						comment = line.substring(index + 1).trim();
						line = line.substring(0, index).trim();
					}
				}
				// check if there is a label on the line
				if (line.matches("^[\\w\\s]+:.*")) {
					index = line.indexOf(':');
					if (index >= 0) {
						label = line.substring(0, index).trim();
						line = line.substring(index + 1).trim();
					}
				}
				SimpleExecutorContext context = new SimpleExecutorContext(lineNumber, label, comment, line, annotations);
				annotations.clear();
				if (line.matches("^if[\\s]*\\(.*\\)$")) {
					line = line.replaceAll("^if[\\s]*\\((.*)\\)$", "$1");
					SequenceExecutor sequenceExecutor = new SequenceExecutor(executorGroups.peek(), context, analyzer.analyze(GlueQueryParser.getInstance().parse(line)));
					executorGroups.peek().getChildren().add(sequenceExecutor);
					executorGroups.push(sequenceExecutor);
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
							nextLine = nextLine.trim();
							// a comment will stop the appending
							index = nextLine.indexOf('#');
							if (index >= 0) {
								comment = nextLine.substring(index + 1).trim();
								nextLine = nextLine.substring(0, index).trim();
							}
							// append with one space, removing other whitespace
							line += " " + nextLine.trim();
						}
						else {
							pushback.pushback(IOUtils.wrap(nextLine + "\n"));
							break;
						}
					}
					
					// check if there is a variable assignment on the line
					if (line.matches("^[\\w]+[\\s?]*=.*")) {
						index = line.indexOf('=');
						variableName = line.substring(0, index).trim();
						line = line.substring(index + 1).trim();
						// if the variablename ends with a "?" you wrote something like "myvar ?= test" which means only overwrite it if it doesn't exist yet
						if (variableName.endsWith("?")) {
							overwriteIfExists = false;
							variableName = variableName.substring(0, variableName.length() - 1).trim();
						}
					}
					Operation<ExecutionContext> operation = analyzer.analyze(GlueQueryParser.getInstance().parse(line));
					executorGroups.peek().getChildren().add(new EvaluateExecutor(executorGroups.peek(), context, null, variableName, operation, overwriteIfExists));
				}
			}
		}
		catch (ParseException e) {
			throw new ParseException("Could not parse line " + (lineNumber + 1) + " [" + e.getMessage() + "]: " + line, lineNumber);
		}
		// there can be multiple elements on the stack at the end if you stopped the script in for example an "if" element
		ExecutorGroup root = null;
		while (executorGroups.size() > 0) {
			root = executorGroups.pop();
		}
		return root;
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
	public String substitute(String value, ExecutionContext context) {
		Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");
		Matcher matcher = pattern.matcher(value);
		try {
			ScriptRuntime runtime = ScriptRuntime.getRuntime();
			while (matcher.find()) {
				String query = matcher.group().replaceAll(pattern.pattern(), "$1");
				Operation<ExecutionContext> operation = analyzer.analyze(GlueQueryParser.getInstance().parse(query));
				String result = runtime.getConverter().convert(operation.evaluate(context), String.class);
				// don't allow empty results, they are likely due to an oversight
				// if you really need an empty string, you can still force it
				if (result == null) {
					throw new IllegalArgumentException("Can not replace the query " + query);
				}
				value = value.replaceAll(Pattern.quote(matcher.group()), Matcher.quoteReplacement(result));
			}
			return value;
		}
		catch (ParseException e) {
			throw new IllegalArgumentException(e);
		}
		catch (EvaluationException e) {
			throw new RuntimeException(e);
		}
	}
}