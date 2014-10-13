package be.nabu.glue.impl;

import be.nabu.glue.api.ExecutionEnvironment;
import be.nabu.glue.api.LabelEvaluator;

public class EnvironmentLabeLEvaluator implements LabelEvaluator {

	private String fieldToCheck;

	public EnvironmentLabeLEvaluator(String fieldToCheck) {
		this.fieldToCheck = fieldToCheck;
	}
	
	@Override
	public boolean shouldExecute(String label, ExecutionEnvironment environment) {
		if (label == null) {
			return true;
		}
		else if (fieldToCheck == null) {
			return label.equalsIgnoreCase(environment.getName());
		}
		else {
			// if the value does not exist for an environment, the label will never be executed
			String value = environment.getParameters().get(fieldToCheck);
			return label.equalsIgnoreCase(value);
		}
	}

}
