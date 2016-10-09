package be.nabu.glue.core.impl.methods.v2;

import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.api.WritableContextAccessor;

public class ModifyMethods {
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void modify(Object content, String path, Object value) {
		if (content != null) {
			ContextAccessor accessor = ContextAccessorFactory.getInstance().getAccessor(content.getClass());
			if (accessor instanceof WritableContextAccessor) {
				((WritableContextAccessor) accessor).set(content, path, value);
			}
		}
	}
	
}
