package com.gimprogresstracker.model;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;

@Data
public class PlayerProgress
{
	private String playerName;
	private String guideName;
	private Set<Integer> completedStepIds = new LinkedHashSet<>();
	private Set<Integer> skippedStepIds = new LinkedHashSet<>();
	private String lastUpdated;

	public Set<Integer> getCompletedStepIds()
	{
		if (completedStepIds == null)
		{
			completedStepIds = new LinkedHashSet<>();
		}
		return completedStepIds;
	}

	public Set<Integer> getSkippedStepIds()
	{
		if (skippedStepIds == null)
		{
			skippedStepIds = new LinkedHashSet<>();
		}
		return skippedStepIds;
	}
}
