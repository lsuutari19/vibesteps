package com.gimprogresstracker;

import com.gimprogresstracker.model.Guide;
import com.gimprogresstracker.model.Location;
import com.gimprogresstracker.model.StepEntry;
import com.gimprogresstracker.ui.GIMProgressTrackerPanel;
import com.gimprogresstracker.ui.PanelIcons;
import com.gimprogresstracker.util.GuideImporter;
import com.gimprogresstracker.util.PluginPaths;
import com.gimprogresstracker.util.ProgressStore;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

@Slf4j
@PluginDescriptor(
	name = "VibeSteps GIM",
	description = "Step-by-step Group Ironman progress tracker with importable guides",
	tags = {"group", "ironman", "gim", "guide", "tracker", "progress"}
)
public class GIMProgressTrackerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private WorldMapPointManager worldMapPointManager;

	@Inject
	private GIMProgressTrackerConfig config;

	@Inject
	private GIMProgressTrackerOverlay overlay;

	@Inject
	private ProgressTracker tracker;

	@Inject
	private ProgressStore progressStore;

	@Inject
	private GuideImporter guideImporter;

	@Inject
	private ConfigManager configManager;

	private GIMProgressTrackerPanel panel;
	private NavigationButton navButton;
	private WorldMapPoint mapPoint;
	private Runnable trackerListener;
	private BufferedImage mapPointIcon;

	@Override
	protected void startUp() throws Exception
	{
		mapPointIcon = buildMapPointIcon(config.highlightColor());

		panel = new GIMProgressTrackerPanel(tracker, this::loadGuideFromFile, this::resetProgress);

		navButton = NavigationButton.builder()
			.tooltip("GIM Progress Tracker")
			.icon(PanelIcons.navIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlayManager.add(overlay);

		trackerListener = this::onTrackerChanged;
		tracker.addListener(trackerListener);

		// Try to pull a player name immediately in case the client is already logged in.
		String name = currentPlayerName();
		if (name != null)
		{
			tracker.setPlayerName(name);
		}

		// Load the configured guide, if any.
		loadGuideFromConfig();
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		overlayManager.remove(overlay);

		if (trackerListener != null)
		{
			tracker.removeListener(trackerListener);
			trackerListener = null;
		}
		clearWorldMapPoint();

		// Persist a final time, then drop in-memory state.
		tracker.persist();
		tracker.setGuide(null);
		panel = null;
	}

	@Provides
	GIMProgressTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GIMProgressTrackerConfig.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			String name = currentPlayerName();
			if (name != null)
			{
				tracker.setPlayerName(name);
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!GIMProgressTrackerConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		switch (event.getKey())
		{
			case "guideFilePath":
				loadGuideFromConfig();
				break;
			case "highlightColor":
				mapPointIcon = buildMapPointIcon(config.highlightColor());
				refreshWorldMapPoint();
				break;
			case "showWorldMapArrow":
				refreshWorldMapPoint();
				break;
			default:
				// other keys are read on demand by the overlay / panel; no action needed
		}
	}

	private void onTrackerChanged()
	{
		if (panel != null)
		{
			panel.refresh();
		}
		refreshWorldMapPoint();
		maybeAutoExport();
	}

	private void loadGuideFromConfig()
	{
		String pathStr = config.guideFilePath();
		if (pathStr == null || pathStr.trim().isEmpty())
		{
			return;
		}
		loadGuideFromFile(Paths.get(pathStr.trim()));
	}

	private void loadGuideFromFile(Path file)
	{
		try
		{
			Guide guide = guideImporter.loadFromFile(file);
			tracker.setGuide(guide);
			log.debug("Loaded guide '{}' from {}", guide.getGuideName(), file);

			// Persist the path so the plugin reloads this guide on next startup. The
			// equality check prevents a feedback loop with our own onConfigChanged.
			String resolved = file.toAbsolutePath().toString();
			if (!resolved.equals(config.guideFilePath()))
			{
				configManager.setConfiguration(GIMProgressTrackerConfig.GROUP, "guideFilePath", resolved);
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to load guide from {}: {}", file, e.getMessage());
			SwingUtilities.invokeLater(() ->
				javax.swing.JOptionPane.showMessageDialog(panel,
					"Could not load guide:\n" + e.getMessage(),
					"GIM Progress Tracker", javax.swing.JOptionPane.ERROR_MESSAGE));
		}
	}

	private void resetProgress()
	{
		tracker.reset();
	}

	private void refreshWorldMapPoint()
	{
		clearWorldMapPoint();
		if (!config.showWorldMapArrow())
		{
			return;
		}
		Optional<StepEntry> currentOpt = tracker.getCurrentStep();
		if (!currentOpt.isPresent())
		{
			return;
		}
		Location loc = currentOpt.get().getStep().getLocation();
		if (loc == null)
		{
			return;
		}
		WorldPoint wp = new WorldPoint(loc.getX(), loc.getY(), loc.getPlane());
		mapPoint = WorldMapPoint.builder()
			.worldPoint(wp)
			.image(mapPointIcon)
			.snapToEdge(true)
			.jumpOnClick(true)
			.name("GIM step " + currentOpt.get().getStep().getId())
			.build();
		worldMapPointManager.add(mapPoint);
	}

	private void clearWorldMapPoint()
	{
		if (mapPoint != null)
		{
			worldMapPointManager.remove(mapPoint);
			mapPoint = null;
		}
	}

	private void maybeAutoExport()
	{
		if (!config.autoExportProgress())
		{
			return;
		}
		String folder = config.sharedFolderPath();
		if (folder == null || folder.trim().isEmpty())
		{
			return;
		}
		if (tracker.getProgress() == null)
		{
			return;
		}
		Path dir = Paths.get(folder.trim());
		if (!Files.isDirectory(dir))
		{
			log.debug("Shared folder does not exist: {}", dir);
			return;
		}
		String fname = PluginPaths.sanitizeForFilename(tracker.getProgress().getPlayerName()) + "_progress.json";
		try
		{
			progressStore.exportTo(tracker.getProgress(), dir.resolve(fname));
		}
		catch (IOException e)
		{
			log.debug("Failed to export progress to shared folder", e);
		}
	}

	private String currentPlayerName()
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}
		String name = local.getName();
		return (name == null || name.isEmpty()) ? null : name;
	}

	private static BufferedImage buildMapPointIcon(Color base)
	{
		int size = 18;
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(base);
			g.fillOval(2, 2, size - 4, size - 4);
			g.setColor(Color.BLACK);
			g.drawOval(2, 2, size - 5, size - 5);
			g.setColor(Color.WHITE);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
			g.drawString("!", size / 2 - 2, size / 2 + 4);
		}
		finally
		{
			g.dispose();
		}
		return img;
	}
}
