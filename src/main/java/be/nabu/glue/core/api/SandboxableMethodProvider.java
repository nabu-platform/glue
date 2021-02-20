package be.nabu.glue.core.api;

public interface SandboxableMethodProvider extends MethodProvider {
	public boolean isSandboxed();
	public void setSandboxed(boolean sandboxed);
}
