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

	private void printAnnotations(Executor executor, PrintWriter writer, int depth) throws IOException {
		if (executor.getContext() != null) {
			for (String key : executor.getContext().getAnnotations().keySet()) {
				pad(writer, depth);
				writer.print("@" + key);
				String value = executor.getContext().getAnnotations().get(key);
				if (value != null && !value.equalsIgnoreCase("true")) {
					writer.print(" " + value);
				}
				writer.println();
			}
		}
	}
	
	private void printComments(Executor executor, PrintWriter printer, int depth) throws IOException {
		if (executor.getContext() != null && executor.getContext().getComment() != null) {
			for (String comment : executor.getContext().getComment().split("[\n]+")) {
				pad(printer, depth);
				printer.println("# " + comment.trim());
			}
		}
		if (executor.getContext() != null && executor.getContext().getDescription() != null) {
			for (String description : executor.getContext().getDescription().split("[\n]+")) {
				pad(printer, depth);
				printer.println("## " + description.trim());
			}
		}
	}
	
	private void format(ExecutorGroup group, PrintWriter writer, int depth) throws IOException {
		for (Executor executor : group.getChildren()) {
			// before each group, add a line feed
			if (executor instanceof ExecutorGroup) {
				writer.println();
			}
			printComments(executor, writer, depth);
			printAnnotations(executor, writer, depth);
			pad(writer, depth);
			if (executor.getContext() != null && executor.getContext().getLabel() != null) {
				writer.print(executor.getContext().getLabel() + ": ");
			}
			if (executor instanceof EvaluateExecutor) {
				EvaluateExecutor evaluateExecutor = (EvaluateExecutor) executor;
				if (evaluateExecutor.getVariableName() != null) {
					writer.print(evaluateExecutor.getVariableName());
					if (evaluateExecutor.isOverwriteIfExists()) {
						writer.print(" = ");
					}
					else {
						writer.print(" ?= ");
					}
				}
				writer.println(evaluateExecutor.getOperation());
			}
			else if (executor instanceof ForEachExecutor) {
				ForEachExecutor forEachExecutor = (ForEachExecutor) executor;
				writer.print("for (");
				if (forEachExecutor.getTemporaryVariable() != null) {
					writer.print(forEachExecutor.getTemporaryVariable() + " : ");
				}
				writer.println(forEachExecutor.getForEach() + ")");
				format(forEachExecutor, writer, depth + 1);
			}
			else if (executor instanceof WhileExecutor) {
				WhileExecutor whileExecutor = (WhileExecutor) executor;
				writer.println("while (" + whileExecutor.getWhile() + ")");
				format(whileExecutor, writer, depth + 1);
			}
			else if (executor instanceof BreakExecutor) {
				BreakExecutor breakExecutor = (BreakExecutor) executor;
				if (breakExecutor.getBreakCount() > 1) {
					writer.println("break " + breakExecutor.getBreakCount());
				}
				else {
					writer.println("break");
				}
			}
			else if (executor instanceof SwitchExecutor) {
				SwitchExecutor switchExecutor = (SwitchExecutor) executor;
				writer.print("switch");
				if (switchExecutor.getToMatch() != null) {
					writer.println(" (" + switchExecutor.getToMatch() + ")");
				}
				else {
					writer.println();
				}
				for (Executor caseExecutor : switchExecutor.getChildren()) {
					SequenceExecutor sequenceExecutor = (SequenceExecutor) caseExecutor;
					pad(writer, depth + 1);
					// we have injected a "$value == (...)" in the case condition, remove it again
					// the default case
					if (sequenceExecutor.getCondition() == null) {
						writer.println("default");
					}
					else {
						List<QueryPart> parts = sequenceExecutor.getCondition().getParts();
	//					writer.println("case (" + parts.subList(3, parts.size() - 2) + ")");
						writer.println("case (" + parts.get(2).getContent() + ")");
					}
					format(sequenceExecutor, writer, depth + 2);
				}
			}
			else if (executor instanceof SequenceExecutor) {
				SequenceExecutor sequenceExecutor = (SequenceExecutor) executor;
				if (sequenceExecutor.getCondition() != null) {
					writer.println("if (" + sequenceExecutor.getCondition() + ")");
					format(sequenceExecutor, writer, depth + 1);
				}
				else {
					writer.println("sequence");
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
