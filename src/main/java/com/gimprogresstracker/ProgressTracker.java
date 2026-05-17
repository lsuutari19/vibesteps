package com.gimprogresstracker;

import com.gimprogresstracker.model.Chapter;
import com.gimprogresstracker.model.Guide;
import com.gimprogresstracker.model.PlayerProgress;
import com.gimprogresstracker.model.Section;
import com.gimprogresstracker.model.Step;
import com.gimprogresstracker.model.StepEntry;
import com.gimprogresstracker.util.ProgressStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Holds the active guide + the local player's progress and exposes mutation methods.
 *
 * Listeners are notified on every state change so the panel and overlays can refresh
 * without polling.
 */
@Slf4j
@Singleton
public class ProgressTracker
{
	private final ProgressStore progressStore;

	@Nullable
	private Guide guide;
	private List<StepEntry> flattened = Collections.emptyList();

	@Nullable
	private PlayerProgress progress;

	@Nullable
	private String playerName;

	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	@Inject
	public ProgressTracker(ProgressStore progressStore)
	{
		this.progressStore = progressStore;
	}

	public void addListener(Runnable l)
	{
		listeners.add(l);
	}

	public void removeListener(Runnable l)
	{
		listeners.remove(l);
	}

	private void fire()
	{
		for (Runnable l : listeners)
		{
			try
			{
				l.run();
			}
			catch (Exception e)
			{
				log.debug("Listener threw", e);
			}
		}
	}

	public void setPlayerName(@Nullable String name)
	{
		if (java.util.Objects.equals(this.playerName, name))
		{
			return;
		}
		this.playerName = name;
		if (name != null && guide != null)
		{
			this.progress = progressStore.loadOrCreate(name, guide.getGuideName());
		}
		fire();
	}

	@Nullable
	public String getPlayerName()
	{
		return playerName;
	}

	public void setGuide(@Nullable Guide newGuide)
	{
		this.guide = newGuide;
		this.flattened = newGuide == null ? Collections.emptyList() : flatten(newGuide);
		if (newGuide != null && playerName != null)
		{
			this.progress = progressStore.loadOrCreate(playerName, newGuide.getGuideName());
		}
		else if (newGuide == null)
		{
			this.progress = null;
		}
		fire();
	}

	@Nullable
	public Guide getGuide()
	{
		return guide;
	}

	public List<StepEntry> getAllSteps()
	{
		return flattened;
	}

	@Nullable
	public PlayerProgress getProgress()
	{
		return progress;
	}

	public int getCompletedCount()
	{
		return progress == null ? 0 : progress.getCompletedStepIds().size();
	}

	public int getSkippedCount()
	{
		return progress == null ? 0 : progress.getSkippedStepIds().size();
	}

	public int getTotalCount()
	{
		return flattened.size();
	}

	public boolean isCompleted(int stepId)
	{
		return progress != null && progress.getCompletedStepIds().contains(stepId);
	}

	public boolean isSkipped(int stepId)
	{
		return progress != null && progress.getSkippedStepIds().contains(stepId);
	}

	public boolean isTodo(int stepId)
	{
		return progress != null && progress.getTodoStepIds().contains(stepId);
	}

	public boolean isResolved(int stepId)
	{
		return isCompleted(stepId) || isSkipped(stepId) || isTodo(stepId);
	}

	public int getTodoCount()
	{
		return progress == null ? 0 : progress.getTodoStepIds().size();
	}

	public List<StepEntry> getTodoSteps()
	{
		if (progress == null)
		{
			return Collections.emptyList();
		}
		List<StepEntry> out = new ArrayList<>();
		for (StepEntry e : flattened)
		{
			if (isTodo(e.getStep().getId()))
			{
				out.add(e);
			}
		}
		return out;
	}

	public Optional<StepEntry> getCurrentStep()
	{
		for (StepEntry e : flattened)
		{
			if (!isResolved(e.getStep().getId()))
			{
				return Optional.of(e);
			}
		}
		return Optional.empty();
	}

	public Optional<StepEntry> getCurrentStepFor(com.gimprogresstracker.model.PlayerProgress p)
	{
		if (p == null || flattened.isEmpty())
		{
			return Optional.empty();
		}
		java.util.Set<Integer> completed = p.getCompletedStepIds();
		java.util.Set<Integer> skipped = p.getSkippedStepIds();
		java.util.Set<Integer> todo = p.getTodoStepIds();
		for (StepEntry e : flattened)
		{
			int id = e.getStep().getId();
			if (!completed.contains(id) && !skipped.contains(id) && !todo.contains(id))
			{
				return Optional.of(e);
			}
		}
		return Optional.empty();
	}

	public List<StepEntry> getUpcoming(int max)
	{
		List<StepEntry> out = new ArrayList<>();
		boolean foundCurrent = false;
		for (StepEntry e : flattened)
		{
			if (!foundCurrent)
			{
				if (isResolved(e.getStep().getId()))
				{
					continue;
				}
				foundCurrent = true;
				continue; // skip the current step itself; we render it separately
			}
			if (isResolved(e.getStep().getId()))
			{
				continue;
			}
			out.add(e);
			if (out.size() >= max)
			{
				break;
			}
		}
		return out;
	}

	public void markCompleted(int stepId)
	{
		if (progress == null)
		{
			return;
		}
		progress.getSkippedStepIds().remove(stepId);
		progress.getTodoStepIds().remove(stepId);
		if (progress.getCompletedStepIds().add(stepId))
		{
			persist();
			fire();
		}
	}

	public void markSkipped(int stepId)
	{
		if (progress == null)
		{
			return;
		}
		progress.getCompletedStepIds().remove(stepId);
		progress.getTodoStepIds().remove(stepId);
		if (progress.getSkippedStepIds().add(stepId))
		{
			persist();
			fire();
		}
	}

	public void moveToPreviousStep()
	{
		if (progress == null)
		{
			return;
		}
		// Find the current step's position in the flattened list.
		int currentIndex = flattened.size();
		Optional<StepEntry> current = getCurrentStep();
		if (current.isPresent())
		{
			int currentId = current.get().getStep().getId();
			for (int i = 0; i < flattened.size(); i++)
			{
				if (flattened.get(i).getStep().getId() == currentId)
				{
					currentIndex = i;
					break;
				}
			}
		}
		// Walk backwards and unmark the nearest completed or skipped step.
		for (int i = currentIndex - 1; i >= 0; i--)
		{
			int stepId = flattened.get(i).getStep().getId();
			if (isCompleted(stepId) || isSkipped(stepId))
			{
				unmark(stepId);
				return;
			}
		}
	}

	public void markTodo(int stepId)
	{
		if (progress == null)
		{
			return;
		}
		progress.getCompletedStepIds().remove(stepId);
		progress.getSkippedStepIds().remove(stepId);
		if (progress.getTodoStepIds().add(stepId))
		{
			persist();
			fire();
		}
	}

	public void unmark(int stepId)
	{
		if (progress == null)
		{
			return;
		}
		boolean changed = progress.getCompletedStepIds().remove(stepId);
		changed |= progress.getSkippedStepIds().remove(stepId);
		changed |= progress.getTodoStepIds().remove(stepId);
		if (changed)
		{
			persist();
			fire();
		}
	}

	public void reset()
	{
		if (progress == null)
		{
			return;
		}
		progress.getCompletedStepIds().clear();
		progress.getSkippedStepIds().clear();
		progress.getTodoStepIds().clear();
		persist();
		fire();
	}

	public void persist()
	{
		if (progress != null && playerName != null)
		{
			progressStore.save(progress);
		}
	}

	private static List<StepEntry> flatten(Guide guide)
	{
		List<StepEntry> out = new ArrayList<>();
		int global = 0;
		List<Chapter> chapters = guide.getChapters();
		for (int ci = 0; ci < chapters.size(); ci++)
		{
			Chapter chapter = chapters.get(ci);
			List<Section> sections = chapter.getSections();
			for (int si = 0; si < sections.size(); si++)
			{
				Section section = sections.get(si);
				List<Step> steps = section.getSteps();
				for (int sti = 0; sti < steps.size(); sti++)
				{
					Step step = steps.get(sti);
					out.add(new StepEntry(ci, si, sti, global++, chapter, section, step));
				}
			}
		}
		return out;
	}
}
