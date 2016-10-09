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
