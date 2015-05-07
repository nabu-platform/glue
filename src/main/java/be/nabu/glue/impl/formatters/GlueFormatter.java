package be.nabu.glue.impl.formatters;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.UnknownFormatConversionException;

import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.Formatter;
import be.nabu.glue.impl.executors.BreakExecutor;
import be.nabu.glue.impl.executors.EvaluateExecutor;
import be.nabu.glue.impl.executors.ForEachExecutor;
import be.nabu.glue.impl.executors.SequenceExecutor;
import be.nabu.glue.impl.executors.SwitchExecutor;
import be.nabu.glue.impl.executors.WhileExecutor;
import be.nabu.libs.evaluator.QueryPart;

public class GlueFormatter implements Formatter {

	@Override
	public void format(ExecutorGroup group, Writer writer) throws IOException {
		PrintWriter printer = new PrintWriter(writer);
		printComments(group, printer, 0);
		printAnnotations(group, printer, 0);
		format(group, printer, 0);
		printer.flush();
	}

	private void printAnnotations(Executor executor, Writer writer, int depth) throws IOException {
		if (executor.getContext() != null) {
			for (String key : executor.getContext().getAnnotations().keySet()) {
				pad(writer, depth);
				writer.append("@" + key);
				String value = executor.getContext().getAnnotations().get(key);
				if (value != null && !value.equalsIgnoreCase("true")) {
					writer.append(" " + value);
				}
				println(writer);
			}
		}
	}
	
	private void println(Writer writer) throws IOException {
		writer.append('\n');
	}
	
	private void printComments(Executor executor, Writer writer, int depth) throws IOException {
		if (executor.getContext() != null && executor.getContext().getComment() != null) {
			for (String comment : executor.getContext().getComment().split("[\n]+")) {
				pad(writer, depth);
				writer.append("# " + comment.trim());
				println(writer);
			}
		}
		if (executor.getContext() != null && executor.getContext().getDescription() != null) {
			for (String description : executor.getContext().getDescription().split("[\n]+")) {
				pad(writer, depth);
				writer.append("## " + description.trim());
				println(writer);
			}
		}
	}
	
	private void format(ExecutorGroup group, Writer writer, int depth) throws IOException {
		for (Executor executor : group.getChildren()) {
			// ignore generated executors
			if (executor.isGenerated()) {
				continue;
			}
			// before each group, add a line feed
			if (executor instanceof ExecutorGroup) {
				println(writer);
			}
			printComments(executor, writer, depth);
			printAnnotations(executor, writer, depth);
			pad(writer, depth);
			if (executor.getContext() != null && executor.getContext().getLabel() != null) {
				writer.append(executor.getContext().getLabel() + ": ");
			}
			if (executor instanceof EvaluateExecutor) {
				EvaluateExecutor evaluateExecutor = (EvaluateExecutor) executor;
				String stringToPrint = "";
				if (evaluateExecutor.getOptionalType() != null) {
					stringToPrint += evaluateExecutor.getOptionalType() + " ";
				}
				if (evaluateExecutor.getVariableName() != null) {
					stringToPrint += evaluateExecutor.getVariableName();
					if (evaluateExecutor.isOverwriteIfExists()) {
						stringToPrint += " = ";
					}
					else {
						stringToPrint += " ?= ";
					}
				}
				// if you have an evaluate executor with no variable name and no operation, just ignore it
				else if (evaluateExecutor.getOperation() == null) {
					continue;
				}
				stringToPrint += evaluateExecutor.getOperation() == null ? "null" : evaluateExecutor.getOperation().toString();
				// if the calculated string equals the metadata string EXCEPT for whitespace, take the metadata one, it will likely have better whitespacing (as dictated by the user)
				if (executor.getContext() != null && executor.getContext().getLine() != null) {
					if (stringToPrint.replaceAll("(?s)[\\s]+", "").equals(executor.getContext().getLine().replaceAll("(?s)[\\s]+", ""))) {
						stringToPrint = executor.getContext().getLine(); 
					}
				}
				writer.append(stringToPrint);
				println(writer);
			}
			else if (executor instanceof ForEachExecutor) {
				ForEachExecutor forEachExecutor = (ForEachExecutor) executor;
				writer.append("for (");
				if (forEachExecutor.getTemporaryVariable() != null) {
					writer.append(forEachExecutor.getTemporaryVariable() + " : ");
				}
				writer.append(forEachExecutor.getForEach() + ")");
				println(writer);
				format(forEachExecutor, writer, depth + 1);
			}
			else if (executor instanceof WhileExecutor) {
				WhileExecutor whileExecutor = (WhileExecutor) executor;
				writer.append("while (" + whileExecutor.getWhile() + ")");
				println(writer);
				format(whileExecutor, writer, depth + 1);
			}
			else if (executor instanceof BreakExecutor) {
				BreakExecutor breakExecutor = (BreakExecutor) executor;
				if (breakExecutor.getBreakCount() > 1) {
					writer.append("break " + breakExecutor.getBreakCount());
					println(writer);
				}
				else {
					writer.append("break");
					println(writer);
				}
			}
			else if (executor instanceof SwitchExecutor) {
				SwitchExecutor switchExecutor = (SwitchExecutor) executor;
				writer.append("switch");
				if (switchExecutor.getToMatch() != null) {
					writer.append(" (" + switchExecutor.getToMatch() + ")");
				}
				println(writer);
				for (Executor caseExecutor : switchExecutor.getChildren()) {
					SequenceExecutor sequenceExecutor = (SequenceExecutor) caseExecutor;
					pad(writer, depth + 1);
					// we have injected a "$value == (...)" in the case condition, remove it again
					// the default case
					if (sequenceExecutor.getCondition() == null) {
						writer.append("default");
						println(writer);
					}
					else {
						List<QueryPart> parts = sequenceExecutor.getCondition().getParts();
	//					writer.println("case (" + parts.subList(3, parts.size() - 2) + ")");
						writer.append("case (" + parts.get(2).getContent() + ")");
						println(writer);
					}
					format(sequenceExecutor, writer, depth + 2);
				}
			}
			else if (executor instanceof SequenceExecutor) {
				SequenceExecutor sequenceExecutor = (SequenceExecutor) executor;
				if (sequenceExecutor.getCondition() != null) {
					writer.append("if (" + sequenceExecutor.getCondition() + ")");
					println(writer);
					format(sequenceExecutor, writer, depth + 1);
				}
				else {
					writer.append("sequence");
					println(writer);
					format(sequenceExecutor, writer, depth + 1);
				}
			}
			else {
				throw new UnknownFormatConversionException("Can not format an executor of type " + executor.getClass());
			}
		}
	}

	private void pad(Writer writer, int depth) throws IOException {
		for (int i = 0; i < depth; i++) {
			writer.write("\t");
		}
	}
}
