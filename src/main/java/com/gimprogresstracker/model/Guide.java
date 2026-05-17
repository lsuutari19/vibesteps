package com.gimprogresstracker.model;

import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class Guide
{
	private String guideName;
	private String author;
	private String version;
	private List<Chapter> chapters;

	public List<Chapter> getChapters()
	{
		return chapters == null ? Collections.emptyList() : chapters;
	}
}
