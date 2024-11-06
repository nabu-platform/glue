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
