package com.gimprogresstracker.model;

import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class TeammateProgress
{
	private String playerName;
	private String guideName;
	private Integer currentChapter;
	private Integer currentSection;
	private Integer currentStep;
	private List<Integer> completedSteps;
	private String lastUpdated;

	public List<Integer> getCompletedSteps()
	{
		return completedSteps == null ? Collections.emptyList() : completedSteps;
	}
}
