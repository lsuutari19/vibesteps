package com.gimprogresstracker.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.runelite.client.RuneLite;

public final class PluginPaths
{
	private static final String PLUGIN_DIR = "gim-progress-tracker";

	private PluginPaths()
	{
	}

	public static Path baseDir()
	{
		return RuneLite.RUNELITE_DIR.toPath().resolve(PLUGIN_DIR);
	}

	public static Path progressDir() throws IOException
	{
		return ensure(baseDir().resolve("progress"));
	}

	public static Path guidesDir() throws IOException
	{
		return ensure(baseDir().resolve("guides"));
	}

	public static Path teammatesDir() throws IOException
	{
		return ensure(baseDir().resolve("teammates"));
	}

	private static Path ensure(Path p) throws IOException
	{
		if (!Files.isDirectory(p))
		{
			Files.createDirectories(p);
		}
		return p;
	}

	/**
	 * Replace OS-illegal filename characters so a player name can be used as a filename safely.
	 */
	public static String sanitizeForFilename(String name)
	{
		if (name == null || name.isEmpty())
		{
			return "unknown";
		}
		return name.replaceAll("[^A-Za-z0-9._-]", "_");
	}
}
