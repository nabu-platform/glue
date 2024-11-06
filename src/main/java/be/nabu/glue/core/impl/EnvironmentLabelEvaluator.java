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

import be.nabu.glue.api.ExecutionEnvironment;
import be.nabu.glue.api.LabelEvaluator;

public class EnvironmentLabelEvaluator implements LabelEvaluator {

	private String fieldToCheck;

	public EnvironmentLabelEvaluator(String fieldToCheck) {
		this.fieldToCheck = fieldToCheck;
	}
	
	@Override
	public boolean shouldExecute(String label, ExecutionEnvironment environment) {
		if (label == null) {
			return true;
		}
		else if (fieldToCheck == null) {
			return label.trim().equalsIgnoreCase(environment.getName());
		}
		else {
			// if the value does not exist for an environment, the label will never be executed
			String value = environment.getParameters().get(fieldToCheck);
			if (value == null) {
				return true;
			}
			else {
				return label.trim().equalsIgnoreCase(value.trim());
			}
		}
	}

}
