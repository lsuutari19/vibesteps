package com.gimprogresstracker.model;

import lombok.Value;

/**
 * Flattened pointer to a step within a guide: chapter / section indices alongside the step itself.
 * Used so callers can navigate a guide as a linear list without re-traversing the tree.
 */
@Value
public class StepEntry
{
	int chapterIndex;
	int sectionIndex;
	int stepIndexInSection;
	int globalIndex;
	Chapter chapter;
	Section section;
	Step step;
}
