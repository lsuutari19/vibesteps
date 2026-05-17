package com.gimprogresstracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GIMProgressTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GIMProgressTrackerPlugin.class);
		RuneLite.main(args);
	}
}
