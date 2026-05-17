package com.gimprogresstracker.model;

import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class Section
{
	private String name;
	private List<Step> steps;

	public List<Step> getSteps()
	{
		return steps == null ? Collections.emptyList() : steps;
	}
}
