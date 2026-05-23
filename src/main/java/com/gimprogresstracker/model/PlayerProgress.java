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
	private Set<Integer> todoStepIds = new LinkedHashSet<>();
	private String lastUpdated;

	// Snapshot written at export time so teammates can display your current step
	// even if they have a different guide loaded. Ignored for local progress tracking.
	private String currentStepBreadcrumb;
	private String currentStepTldr;
	private String currentStepDescription;
	private Location currentStepLocation;

	// Live location — written periodically when the player has location sharing enabled.
	// Null when sharing is off or the player is not logged in.
	private Location liveLocation;
	private String liveLocationUpdated;

	// Optional short status message set by the player and visible to teammates.
	private String status;

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

	public Set<Integer> getTodoStepIds()
	{
		if (todoStepIds == null)
		{
			todoStepIds = new LinkedHashSet<>();
		}
		return todoStepIds;
	}
}
