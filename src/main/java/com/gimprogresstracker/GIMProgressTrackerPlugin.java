package com.gimprogresstracker;

import com.gimprogresstracker.model.Guide;
import com.gimprogresstracker.model.ItemStatus;
import com.gimprogresstracker.model.Location;
import com.gimprogresstracker.model.RequiredItem;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AccountType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.LinkBrowser;

@Slf4j
@PluginDescriptor(
	name = "Vibe Steps Progress Tracker",
	description = "Step-by-step Group Ironman progress tracker with importable guides",
	tags = {"vibe", "steps", "group", "ironman", "gim", "guide", "tracker", "progress"}
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

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientThread clientThread;

	private GIMProgressTrackerPanel panel;
	private NavigationButton navButton;
	private WorldMapPoint mapPoint;
	private Runnable trackerListener;
	private BufferedImage mapPointIcon;

	// Item names resolved on the client thread and cached so the EDT can render
	// item rows without touching client APIs.
	private final ConcurrentHashMap<Integer, String> itemNameCache = new ConcurrentHashMap<>();

	private volatile boolean isGroupIronman = false;

	// All three containers are cached so checkItemStatus can be called from any
	// thread without touching the client API (which requires the client thread).
	private volatile Item[] cachedInventoryItems = new Item[0];
	private volatile Item[] cachedBankItems = new Item[0];
	private volatile Item[] cachedGimBankItems = new Item[0];

	@Override
	protected void startUp() throws Exception
	{
		mapPointIcon = buildMapPointIcon(config.highlightColor());

		panel = new GIMProgressTrackerPanel(tracker, this::loadGuideFromFile, this::resetProgress,
			this::isQuestHelperInstalled, this::openQuestGuide, itemManager, this::checkItemStatus,
			id -> itemNameCache.getOrDefault(id, "Item #" + id),
			() -> cachedBankItems.length > 0,
			() -> cachedGimBankItems.length > 0,
			() -> isGroupIronman);

		navButton = NavigationButton.builder()
			.tooltip("Vibe Steps Progress Tracker")
			.icon(PanelIcons.navIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlayManager.add(overlay);

		trackerListener = this::onTrackerChanged;
		tracker.addListener(trackerListener);

		// Try to pull a player name immediately in case the client is already logged in.
		// Fall back to a "default" profile so progress tracking works pre-login (e.g. in the
		// dev client). Once the real account logs in, setPlayerName will swap to that profile.
		String name = currentPlayerName();
		tracker.setPlayerName(name != null ? name : "default");

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
			AccountType type = client.getAccountType();
			isGroupIronman = type == AccountType.GROUP_IRONMAN
				|| type == AccountType.HARDCORE_GROUP_IRONMAN
				|| type == AccountType.UNRANKED_GROUP_IRONMAN;
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
		scheduleItemNameCaching();
		if (panel != null)
		{
			panel.refresh();
		}
		refreshWorldMapPoint();
		maybeAutoExport();
	}

	private void scheduleItemNameCaching()
	{
		Optional<StepEntry> stepOpt = tracker.getCurrentStep();
		if (!stepOpt.isPresent())
		{
			return;
		}
		List<RequiredItem> items = stepOpt.get().getStep().getRequiredItems();
		if (items.isEmpty())
		{
			return;
		}
		boolean allCached = items.stream().allMatch(i -> itemNameCache.containsKey(i.getItemId()));
		if (allCached)
		{
			return;
		}
		clientThread.invoke(() ->
		{
			boolean anyNew = false;
			for (RequiredItem item : items)
			{
				if (!itemNameCache.containsKey(item.getItemId()))
				{
					try
					{
						net.runelite.api.ItemComposition comp = itemManager.getItemComposition(item.getItemId());
						String name = comp.getName();
						if (name != null && !name.equals("null") && !name.isEmpty())
						{
							itemNameCache.put(item.getItemId(), name);
							anyNew = true;
						}
					}
					catch (Exception ignored)
					{
					}
				}
			}
			if (anyNew && panel != null)
			{
				panel.refresh();
			}
		});
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
					"Vibe Steps Progress Tracker", javax.swing.JOptionPane.ERROR_MESSAGE));
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
			.name("Vibe Steps step " + currentOpt.get().getStep().getId())
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

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.INVENTORY.getId())
		{
			Item[] items = event.getItemContainer().getItems();
			cachedInventoryItems = items != null ? items : new Item[0];
		}
		if (id == InventoryID.BANK.getId())
		{
			Item[] items = event.getItemContainer().getItems();
			cachedBankItems = items != null ? items : new Item[0];
		}
		if (id == InventoryID.GROUP_STORAGE.getId())
		{
			Item[] items = event.getItemContainer().getItems();
			cachedGimBankItems = items != null ? items : new Item[0];
		}
		if (id == InventoryID.INVENTORY.getId() || id == InventoryID.BANK.getId()
			|| id == InventoryID.GROUP_STORAGE.getId())
		{
			if (panel != null)
			{
				panel.refresh();
			}
		}
	}

	private ItemStatus checkItemStatus(RequiredItem required)
	{
		int itemId = required.getItemId();
		int needed = required.getQuantity();

		int invCount = 0;
		for (Item item : cachedInventoryItems)
		{
			if (item.getId() == itemId)
			{
				invCount += item.getQuantity();
			}
		}
		if (invCount >= needed)
		{
			return ItemStatus.IN_INVENTORY;
		}

		int bankCount = 0;
		for (Item item : cachedBankItems)
		{
			if (item.getId() != -1 && itemManager.canonicalize(item.getId()) == itemId)
			{
				bankCount += item.getQuantity();
			}
		}
		if (bankCount >= needed)
		{
			return ItemStatus.IN_BANK;
		}

		int gimCount = 0;
		for (Item item : cachedGimBankItems)
		{
			if (item.getId() != -1 && itemManager.canonicalize(item.getId()) == itemId)
			{
				gimCount += item.getQuantity();
			}
		}
		if (gimCount >= needed)
		{
			return ItemStatus.IN_GIM_BANK;
		}

		return ItemStatus.NOT_FOUND;
	}

	private boolean isQuestHelperInstalled()
	{
		return pluginManager.getPlugins().stream()
			.anyMatch(p -> "QuestHelperPlugin".equals(p.getClass().getSimpleName())
				&& pluginManager.isPluginEnabled(p));
	}

	private void openQuestGuide(String questName)
	{
		String encoded = questName.replace(" ", "_").replace("'", "%27");
		LinkBrowser.browse("https://oldschool.runescape.wiki/w/" + encoded);
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
