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

package be.nabu.glue.core.impl.methods.v2;

import be.nabu.libs.evaluator.ContextAccessorFactory;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.ContextAccessor;
import be.nabu.libs.evaluator.api.WritableContextAccessor;

public class ModifyMethods {
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void modify(Object content, String path, Object value) throws EvaluationException {
		if (content != null) {
			ContextAccessor accessor = ContextAccessorFactory.getInstance().getAccessor(content.getClass());
			if (accessor instanceof WritableContextAccessor) {
				((WritableContextAccessor) accessor).set(content, path, value);
			}
		}
	}
	
}
