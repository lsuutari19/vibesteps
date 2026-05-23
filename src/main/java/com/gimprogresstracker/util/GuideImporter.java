package com.gimprogresstracker.util;

import com.gimprogresstracker.model.Chapter;
import com.gimprogresstracker.model.Guide;
import com.gimprogresstracker.model.Section;
import com.gimprogresstracker.model.Step;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GuideImporter
{
	@Data
	public static class BundledGuide
	{
		private final String displayName;
		private final String author;
		private final String resourcePath;
	}

	public static final List<BundledGuide> BUNDLED_GUIDES = Collections.unmodifiableList(Arrays.asList(
		new BundledGuide("BRUHsailer Complete Guide", "BRUHsailer", "/guides/bruhsailer-guide.json")
	));
	private final Gson gson;

	@Inject
	public GuideImporter(Gson gson)
	{
		this.gson = gson;
	}

	public Guide loadFromFile(Path path) throws IOException
	{
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			return parse(reader);
		}
	}

	public Guide loadFromResource(String resourcePath) throws IOException
	{
		InputStream stream = GuideImporter.class.getResourceAsStream(resourcePath);
		if (stream == null)
		{
			throw new IOException("Bundled guide resource not found: " + resourcePath);
		}
		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
		{
			return parse(reader);
		}
	}

	private Guide parse(Reader reader) throws IOException
	{
		try
		{
			Guide guide = gson.fromJson(reader, Guide.class);
			if (guide == null)
			{
				throw new IOException("Guide file is empty or malformed");
			}
			validate(guide);
			return guide;
		}
		catch (JsonParseException e)
		{
			throw new IOException("Failed to parse guide JSON: " + e.getMessage(), e);
		}
	}

	private void validate(Guide guide) throws IOException
	{
		if (guide.getGuideName() == null || guide.getGuideName().isEmpty())
		{
			throw new IOException("Guide is missing 'guideName'");
		}
		if (guide.getChapters().isEmpty())
		{
			throw new IOException("Guide has no chapters");
		}

		Set<Integer> seenIds = new HashSet<>();
		int totalSteps = 0;
		for (Chapter chapter : guide.getChapters())
		{
			for (Section section : chapter.getSections())
			{
				for (Step step : section.getSteps())
				{
					totalSteps++;
					if (!seenIds.add(step.getId()))
					{
						throw new IOException("Duplicate step id in guide: " + step.getId());
					}
					if (step.getDescription() == null || step.getDescription().isEmpty())
					{
						throw new IOException("Step " + step.getId() + " has no description");
					}
				}
			}
		}
		if (totalSteps == 0)
		{
			throw new IOException("Guide contains no steps");
		}
		log.debug("Loaded guide '{}' with {} steps", guide.getGuideName(), totalSteps);
	}
}
