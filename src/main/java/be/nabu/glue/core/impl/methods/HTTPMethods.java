/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.glue.core.impl.methods;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.glue.annotations.GlueMethod;
import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.evaluator.annotations.MethodProviderClass;

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
