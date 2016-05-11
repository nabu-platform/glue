package be.nabu.glue.impl.parsers;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.Parser;
import be.nabu.glue.api.ScriptRepository;
import be.nabu.libs.evaluator.api.OperationProvider;
import be.nabu.utils.io.IOUtils;

public class EmbeddedGlueParser extends GlueParser {

	public EmbeddedGlueParser(ScriptRepository repository, OperationProvider<ExecutionContext> operationProvider) {
		super(repository, operationProvider);
	}

	@Override
	public ExecutorGroup parse(Reader reader) throws IOException, ParseException {
		// we preprocess the content and basically invert it
		// all embedded glue become actual glue commands
		// all the text around it becomes a string to be outputted
		String content = IOUtils.toString(IOUtils.wrap(reader)).replace("\r", "");
		Pattern pattern = Pattern.compile("(?s)\\$\\{\\{.*?\\}\\}");
		Matcher matcher = pattern.matcher(content);
		StringBuilder result = new StringBuilder();
		int lastIndex = -1;
		// code depth is at which depth we are generating glue code (and embedded strings)
		int codeDepth = 0;
		while(matcher.find()) {
			// if there is text between the last match and this one, output it as string
			if (matcher.start() > lastIndex + 1) {
				// no code yet, add an initial echo()
				// if there is data after this block, add an echo
				result.append("\necho(template(\"");
				result.append(encodeString(content.substring(lastIndex + 1, matcher.start()), 1));
				// end whatever echo was echoing this
				result.append("\"))\n");
			}
			String code = matcher.group().replaceAll("^[\\s]+\n", "");
			// skip start and end ${{}}
			code = code.substring(3, code.length() - 2);
			if (!code.trim().isEmpty()) {
				// can be reset per block and within the block it can become less (never more)
				codeDepth = getDepth(code);
				for (String line : code.split("\n")) {
					if (line.trim().isEmpty()) {
						continue;
					}
					int startIndex = 0;
					for (int i = 0; i <= Math.min(codeDepth, line.length()); i++) {
						if (line.charAt(i) == '\t' || line.charAt(i) == ' ') {
							startIndex++;
						}
						else {
							codeDepth = startIndex;
							break;
						}
					}
					result.append(line.substring(codeDepth) + "\n");
				}
			}
			lastIndex = matcher.end();
		}
		if (lastIndex + 1 < content.length()) {
			String rest = content.substring(lastIndex + 1);
			if (!rest.trim().isEmpty()) {
				result.append("\necho(template(\"");
				result.append(encodeString(rest, 1));
				// end whatever echo was echoing this
				result.append("\"))\n");
			}
		}
		return super.parse(new StringReader(result.toString()));
	}

	private Object encodeString(String substring, int codeDepth) {
		String buffer = "";
		for (int i = 0; i < codeDepth; i++) {
			buffer += "\t";
		}
		return substring.replace("\"", "\\\"").replace("#", "\\#").replace("\n", "\n" + buffer);
	}

	@Override
	public Parser getSubstitutionParser() {
		return new GlueParser(getRepository(), getOperationProvider());
	}
}
