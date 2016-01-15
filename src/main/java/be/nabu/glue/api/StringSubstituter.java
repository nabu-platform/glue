package be.nabu.glue.api;

public interface StringSubstituter {
	public String substitute(String value, ExecutionContext context, boolean allowNull);
}
