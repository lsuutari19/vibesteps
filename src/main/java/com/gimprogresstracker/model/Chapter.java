package com.gimprogresstracker.model;

import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class Chapter
{
	private String name;
	private List<Section> sections;

	public List<Section> getSections()
	{
		return sections == null ? Collections.emptyList() : sections;
	}
}
