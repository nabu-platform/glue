package be.nabu.glue.core.impl;

import be.nabu.libs.evaluator.QueryParser;
import be.nabu.libs.evaluator.QueryPart.Type;

public class GlueQueryParser extends QueryParser {
	
	private static GlueQueryParser parser;

	public static GlueQueryParser getInstance() {
		if (parser == null)
			parser = new GlueQueryParser();
		return parser;
	}
	
	protected GlueQueryParser() {
		getParts().put(Type.NOT_IN, "!\\?|\\bnot in\\b");
		getParts().put(Type.IN, "\\?|\\bin\\b");
	}
}