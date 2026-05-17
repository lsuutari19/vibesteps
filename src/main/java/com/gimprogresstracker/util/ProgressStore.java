package com.gimprogresstracker.util;

import com.gimprogresstracker.model.PlayerProgress;
import com.gimprogresstracker.model.TeammateProgress;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ProgressStore
{
	private final Gson gson;

	@Inject
	public ProgressStore(Gson gson)
	{
		this.gson = gson;
	}

	public PlayerProgress loadOrCreate(String playerName, String guideName)
	{
		Path file;
		try
		{
			file = progressFile(playerName);
		}
		catch (IOException e)
		{
			log.warn("Could not resolve progress directory", e);
			return blank(playerName, guideName);
		}

		if (!Files.isRegularFile(file))
		{
			return blank(playerName, guideName);
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
		{
			PlayerProgress progress = gson.fromJson(reader, PlayerProgress.class);
			if (progress == null)
			{
				return blank(playerName, guideName);
			}
			// If the saved progress is for a different guide, start fresh — completion IDs
			// only have meaning within the guide they were collected in.
			if (guideName != null && !guideName.equals(progress.getGuideName()))
			{
				return blank(playerName, guideName);
			}
			progress.setPlayerName(playerName);
			return progress;
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Failed to read progress file {}", file, e);
			return blank(playerName, guideName);
		}
	}

	public void save(PlayerProgress progress)
	{
		if (progress.getPlayerName() == null || progress.getPlayerName().isEmpty())
		{
			return;
		}
		progress.setLastUpdated(Instant.now().toString());

		Path file;
		try
		{
			file = progressFile(progress.getPlayerName());
		}
		catch (IOException e)
		{
			log.warn("Could not resolve progress directory", e);
			return;
		}

		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8))
		{
			gson.toJson(progress, writer);
		}
		catch (IOException e)
		{
			log.warn("Failed to write progress file {}", file, e);
		}
	}

	public void exportTo(PlayerProgress progress, Path target) throws IOException
	{
		progress.setLastUpdated(Instant.now().toString());
		try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8))
		{
			gson.toJson(progress, writer);
		}
	}

	public TeammateProgress readTeammate(Path file) throws IOException
	{
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
		{
			TeammateProgress tp = gson.fromJson(reader, TeammateProgress.class);
			if (tp == null)
			{
				throw new IOException("Teammate progress file is empty");
			}
			return tp;
		}
		catch (JsonParseException e)
		{
			throw new IOException("Malformed teammate progress JSON: " + e.getMessage(), e);
		}
	}

	public PlayerProgress readPlayerProgress(Path file) throws IOException
	{
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
		{
			PlayerProgress p = gson.fromJson(reader, PlayerProgress.class);
			if (p == null)
			{
				throw new IOException("Progress file is empty");
			}
			return p;
		}
		catch (JsonParseException e)
		{
			throw new IOException("Malformed progress JSON: " + e.getMessage(), e);
		}
	}

	private Path progressFile(String playerName) throws IOException
	{
		return PluginPaths.progressDir().resolve(PluginPaths.sanitizeForFilename(playerName) + "_progress.json");
	}

	private PlayerProgress blank(String playerName, String guideName)
	{
		PlayerProgress p = new PlayerProgress();
		p.setPlayerName(playerName);
		p.setGuideName(guideName);
		return p;
	}
}
