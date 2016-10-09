package be.nabu.glue.impl.methods.v2;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collection;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.glue.impl.methods.HTTPMethods.HTTPResult;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.evaluator.api.ListableContextAccessor;
import be.nabu.utils.io.IOUtils;

@MethodProviderClass(namespace = "http")
public class HTTPMethods {
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@GlueMethod(version = 2)
	public static HTTPResult http(@GlueParam(name = "method") String method, @GlueParam(name = "url") URI uri, @GlueParam(name = "content") Object request, @GlueParam(name = "headers") Object headers) throws MalformedURLException, IOException, EvaluationException {
		if (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https")) {
			throw new IllegalArgumentException("Can not perform an http call using the scheme: " + uri.getScheme());
		}
		HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
		connection.setRequestMethod(method);
		if (headers != null) {
			ListableContextAccessor accessor = (ListableContextAccessor) ContextAccessorFactory.getInstance().getAccessor(headers.getClass());
			if (accessor == null) {
				throw new RuntimeException("Can not access headers: " + headers);
			}
			Collection<String> keys = accessor.list(headers);
			for (String key : keys) {
				String value = ConverterFactory.getInstance().getConverter().convert(accessor.get(headers, key), String.class);
				connection.setRequestProperty(be.nabu.glue.impl.methods.HTTPMethods.fieldToHeader(key), value);
			}
		}
		if (request != null && (method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT"))) {
			connection.setDoOutput(true);
			connection.getOutputStream().write(be.nabu.glue.impl.methods.ScriptMethods.bytes(request));
		}
		InputStream stream = connection.getInputStream();
		try {
			byte [] response = IOUtils.toBytes(IOUtils.wrap(stream));
			return new HTTPResult(connection.getResponseCode(), connection.getHeaderFields(), response);
		}
		finally {
			stream.close();
		}
	}
}
