package com.gimprogresstracker;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(GIMProgressTrackerConfig.GROUP)
public interface GIMProgressTrackerConfig extends Config
{
	String GROUP = "gim-progress-tracker";

	@ConfigSection(
		name = "Display",
		description = "What the plugin draws on-screen",
		position = 0
	)
	String displaySection = "display";

	@ConfigSection(
		name = "Integrations",
		description = "Optional features that integrate with other plugins or the GIM group",
		position = 1,
		closedByDefault = true
	)
	String integrationsSection = "integrations";

	@ConfigSection(
		name = "Team sync",
		description = "Local file-based sync with GIM teammates",
		position = 2,
		closedByDefault = true
	)
	String syncSection = "sync";

	@ConfigSection(
		name = "Developer (experimental)",
		description = "Experimental features that are not fully tested. Use at your own risk.",
		position = 3,
		closedByDefault = true
	)
	String developerSection = "developer";

	@ConfigItem(
		keyName = "guideFilePath",
		name = "Guide file path",
		description = "Path to a guide .json file. Prefer the panel's Import button.",
		position = 0,
		section = displaySection
	)
	default String guideFilePath()
	{
		return "";
	}

	@ConfigItem(
		keyName = "showMinimapArrow",
		name = "Show minimap arrow",
		description = "Display a marker on the minimap pointing to the current step",
		position = 1,
		section = displaySection
	)
	default boolean showMinimapArrow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showWorldMapArrow",
		name = "Show world map arrow",
		description = "Display a marker on the world map at the current step's location",
		position = 2,
		section = displaySection
	)
	default boolean showWorldMapArrow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSceneHighlight",
		name = "Highlight destination tile",
		description = "Outline the current step's destination tile in the game world when it is in view",
		position = 3,
		section = displaySection
	)
	default boolean showSceneHighlight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightColor",
		name = "Highlight color",
		description = "Color used for the destination tile highlight",
		position = 4,
		section = displaySection
	)
	default Color highlightColor()
	{
		return new Color(0, 200, 220);
	}

	@ConfigItem(
		keyName = "autoSkipBanked",
		name = "Auto-skip banked items",
		description = "Automatically mark a step skippable when its required items are present in the group storage (Phase 2)",
		position = 1,
		section = integrationsSection
	)
	default boolean autoSkipBanked()
	{
		return false;
	}

	@ConfigItem(
		keyName = "useShortestPath",
		name = "Shortest path to next step (experimental)",
		description = "Send the current step's destination to the Shortest Path plugin. The path clears when you arrive and reappears on the next step. Works with any of Shortest Path's display styles.",
		warning = "Experimental and not fully tested. Requires the Shortest Path plugin from the Plugin Hub. Writes to that plugin via its inter-plugin messaging API.",
		position = 0,
		section = developerSection
	)
	default boolean useShortestPath()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sharedFolderPath",
		name = "Shared folder path",
		description = "Folder used to share progress with teammates (e.g. a Dropbox subfolder)",
		position = 0,
		section = syncSection
	)
	default String sharedFolderPath()
	{
		return "";
	}

	@ConfigItem(
		keyName = "autoExportProgress",
		name = "Auto-export progress",
		description = "Write a copy of your progress to the shared folder whenever a step changes",
		position = 1,
		section = syncSection
	)
	default boolean autoExportProgress()
	{
		return false;
	}
}
