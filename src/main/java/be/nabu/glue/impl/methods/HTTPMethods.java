package be.nabu.glue.impl.methods;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.glue.annotations.GlueParam;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;
import be.nabu.libs.evaluator.api.ListableContextAccessor;

@MethodProviderClass(namespace = "http")
public class HTTPMethods {
	
	@GlueMethod(version = 1)
	public static HTTPResult http(String method, URI uri, Object request, Object...parameters) throws MalformedURLException, IOException {
		if (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https")) {
			throw new IllegalArgumentException("Can not perform an http call using the scheme: " + uri.getScheme());
		}
		HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
		connection.setRequestMethod(method);
		if (parameters != null) {
			for (Object parameter : parameters) {
				if (!(parameter instanceof List)) {
					throw new IllegalArgumentException("The parameters must be tuples of key,value");
				}
				List<?> list = (List<?>) parameter;
				if (!(list.size() == 2)) {
					throw new IllegalArgumentException("The parameter " + parameter + " is of the incorrect format, it should be a tuple of key,value");
				}
				String key = ConverterFactory.getInstance().getConverter().convert(list.get(0), String.class);
				String value = ConverterFactory.getInstance().getConverter().convert(list.get(1), String.class);
				connection.setRequestProperty(key, value);
			}
		}
		if (request != null && (method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT"))) {
			connection.setDoOutput(true);
			connection.getOutputStream().write(ScriptMethods.bytes(request));
		}
		byte [] response = ScriptMethods.bytes(connection.getInputStream());
		return new HTTPResult(connection.getResponseCode(), connection.getHeaderFields(), response);
	}
	
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
				connection.setRequestProperty(fieldToHeader(key), value);
			}
		}
		if (request != null && (method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT"))) {
			connection.setDoOutput(true);
			connection.getOutputStream().write(ScriptMethods.bytes(request));
		}
		InputStream stream = connection.getInputStream();
		try {
			byte [] response = ScriptMethods.bytes(stream);
			return new HTTPResult(connection.getResponseCode(), connection.getHeaderFields(), response);
		}
		finally {
			stream.close();
		}
	}
	
	public static String getHTTPResultEncoding(HTTPResult result) {
		for (String key : result.getHeaders().keySet()) {
			if (key != null && key.equalsIgnoreCase("Content-Type")) {
				for (String value : result.getHeaders().get(key)) {
					for (String part : value.split("[\\s]*;[\\\\s]*")) {
						if (part.trim().matches("(?i)^charset[\\s]*=(.*)")) {
							return part.trim().replaceAll("(?i)^charset[\\s]*=(.*)", "$1").trim();
						}
					}
				}
			}
		}
		return Charset.defaultCharset().name();
	}
	
	public static class HTTPResult {
		
		private Map<String, List<String>> headers;
		private byte [] content;
		private int code;

		public HTTPResult(int code, Map<String, List<String>> headers, byte [] content) {
			this.code = code;
			this.headers = new HashMap<String, List<String>>();
			for (String key : headers.keySet()) {
				if (key != null) {
					this.headers.put(headerToField(key), headers.get(key));
				}
			}
			this.content = content;
		}
		
		public Map<String, List<String>> getHeaders() {
			return headers;
		}
		public void setHeaders(Map<String, List<String>> headers) {
			this.headers = headers;
		}
		public byte[] getContent() {
			return content;
		}
		public void setContent(byte[] content) {
			this.content = content;
		}

		public int getCode() {
			return code;
		}

		public void setCode(int code) {
			this.code = code;
		}
	}
	
	public static String headerToField(String headerName) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < headerName.length(); i++) {
			if (i == 0) {
				builder.append(headerName.substring(i, i + 1).toLowerCase());
			}
			else if (headerName.charAt(i) == '-') {
				builder.append(headerName.substring(i + 1, i + 2).toUpperCase());
				i++;
			}
			else {
				builder.append(headerName.substring(i, i + 1).toLowerCase());
			}
		}
		return builder.toString();
	}
	
	public static String fieldToHeader(String fieldName) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < fieldName.length(); i++) {
			if (i == 0) {
				builder.append(fieldName.substring(i, i + 1).toUpperCase());
			}
			else if (!fieldName.substring(i, i + 1).equals(fieldName.substring(i, i + 1).toLowerCase())) {
				builder.append("-").append(fieldName.substring(i, i + 1).toUpperCase());
			}
			else {
				builder.append(fieldName.substring(i, i + 1).toLowerCase());
			}
		}
		return builder.toString();
	}
}
