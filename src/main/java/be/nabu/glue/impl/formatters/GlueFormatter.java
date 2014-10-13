package be.nabu.glue.impl.formatters;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.UnknownFormatConversionException;

import be.nabu.glue.api.Executor;
import be.nabu.glue.api.ExecutorGroup;
import be.nabu.glue.api.Formatter;
import be.nabu.glue.impl.executors.EvaluateExecutor;
import be.nabu.glue.impl.executors.ForEachExecutor;
import be.nabu.glue.impl.executors.SequenceExecutor;
import be.nabu.glue.impl.executors.SwitchExecutor;
import be.nabu.libs.evaluator.QueryPart;

public class GlueFormatter implements Formatter {

	@Override
	public void format(ExecutorGroup group, Writer writer) throws IOException {
		PrintWriter printer = new PrintWriter(writer);
		if (group.getContext().getComment() != null) {
			for (String comment : group.getContext().getComment().split("[\n]+")) {
				printer.println("# " + comment.trim());
			}
		}
		printAnnotations(group, printer, 0);
		format(group, printer, 0);
		printer.flush();
	}

	private void printAnnotations(Executor executor, PrintWriter writer, int depth) throws IOException {
		for (String key : executor.getContext().getAnnotations().keySet()) {
			pad(writer, depth);
			writer.print("@" + key);
			String value = executor.getContext().getAnnotations().get(key);
			if (value != null && !value.equalsIgnoreCase("true")) {
				writer.print(" = " + value);
			}
			writer.println();
		}
	}
	
	private void format(ExecutorGroup group, PrintWriter writer, int depth) throws IOException {
		for (Executor executor : group.getChildren()) {
			printAnnotations(executor, writer, depth);
			pad(writer, depth);
			if (executor.getContext().getLabel() != null) {
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
				writer.print(evaluateExecutor.getOperation());
				if (executor.getContext().getComment() != null) {
					writer.println(" # " + executor.getContext().getComment());
				}
				else {
					writer.println();
				}
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
					List<QueryPart> parts = sequenceExecutor.getCondition().getParts();
					writer.println("case (" + parts.subList(3, parts.size() - 2) + ")");
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
					format(sequenceExecutor, writer, depth);
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
