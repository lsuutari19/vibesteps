package com.gimprogresstracker.model;

import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class Step
{
	private int id;
	private String description;
	private Location location;
	private List<RequiredItem> requiredItems;
	private boolean skipIfBanked;
	private String questHelperLink;

	public List<RequiredItem> getRequiredItems()
	{
		return requiredItems == null ? Collections.emptyList() : requiredItems;
	}
}
