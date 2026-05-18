package com.gimprogresstracker;

import com.gimprogresstracker.model.Guide;
import com.gimprogresstracker.model.ItemStatus;
import com.gimprogresstracker.model.Location;
import com.gimprogresstracker.model.RequiredItem;
import com.gimprogresstracker.model.StepEntry;
import com.gimprogresstracker.ui.GIMProgressTrackerPanel;
import com.gimprogresstracker.ui.PanelIcons;
import com.gimprogresstracker.ui.TeammatesPanel;
import com.gimprogresstracker.util.GuideImporter;
import com.gimprogresstracker.util.PluginPaths;
import com.gimprogresstracker.util.ProgressStore;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.Notifier;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
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

	@Inject
	private Notifier notifier;

	@Inject
	private EventBus eventBus;

	private GIMProgressTrackerPanel panel;
	private TeammatesPanel teammatesPanel;
	private NavigationButton navButton;
	private NavigationButton teammatesNavButton;
	private WorldMapPoint mapPoint;
	private WorldMapPoint teammateMapPoint;
	private Runnable trackerListener;
	private BufferedImage mapPointIcon;

	// Item names resolved on the client thread and cached so the EDT can render
	// item rows without touching client APIs.
	private final ConcurrentHashMap<Integer, String> itemNameCache = new ConcurrentHashMap<>();

	private volatile boolean isGroupIronman = false;

	// Pre-canonicalized item ID → quantity maps, built on the client thread so
	// checkItemStatus can do pure map lookups from any thread (including the EDT).
	private volatile Map<Integer, Integer> cachedInventory = Collections.emptyMap();
	private volatile Map<Integer, Integer> cachedEquipment = Collections.emptyMap();
	private volatile Map<Integer, Integer> cachedBank = Collections.emptyMap();
	private volatile Map<Integer, Integer> cachedGimBank = Collections.emptyMap();

	// Skill name → real level, updated on login and StatChanged. Keyed by Skill.name()
	// so the EDT can read it without touching the client API.
	private final ConcurrentHashMap<String, Integer> cachedSkillLevels = new ConcurrentHashMap<>();

	@Override
	protected void startUp() throws Exception
	{
		mapPointIcon = buildMapPointIcon(config.highlightColor());

		panel = new GIMProgressTrackerPanel(tracker, this::loadGuideFromFile, this::resetProgress,
			this::isQuestHelperInstalled, this::openQuestHelperForQuest, this::openWikiForQuest,
			itemManager, this::checkItemStatus,
			id -> itemNameCache.getOrDefault(id, "Item #" + id),
			() -> !cachedBank.isEmpty(),
			() -> !cachedGimBank.isEmpty(),
			() -> isGroupIronman,
			skill -> cachedSkillLevels.getOrDefault(skill.name(), 1),
			this::openWorldMapAt);

		navButton = NavigationButton.builder()
			.tooltip("Vibe Steps Progress Tracker")
			.icon(PanelIcons.navIcon(GIMProgressTrackerPlugin.class))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		teammatesPanel = new TeammatesPanel(tracker, progressStore, config::sharedFolderPath,
			this::showTeammateMapPoint);
		teammatesNavButton = NavigationButton.builder()
			.tooltip("Vibe Steps – Teammates")
			.icon(PanelIcons.teammatesIcon())
			.priority(8)
			.panel(teammatesPanel)
			.build();
		clientToolbar.addNavigation(teammatesNavButton);

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
		if (teammatesNavButton != null)
		{
			clientToolbar.removeNavigation(teammatesNavButton);
			teammatesNavButton = null;
		}
		teammatesPanel = null;
		overlayManager.remove(overlay);

		if (trackerListener != null)
		{
			tracker.removeListener(trackerListener);
			trackerListener = null;
		}
		clearWorldMapPoint();
		clearTeammateMapPoint();
		if (config.useShortestPath())
		{
			clearShortestPath();
		}

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
			// Varbit 1777 (ACCOUNT_TYPE): 4=Group Ironman, 5=Hardcore Group Ironman, 6=Unranked Group Ironman
			int acctType = client.getVarbitValue(Varbits.ACCOUNT_TYPE);
			isGroupIronman = acctType == 4 || acctType == 5 || acctType == 6;
			cacheAllSkillLevels();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		if (skill != null && skill != Skill.OVERALL)
		{
			cachedSkillLevels.put(skill.name(), event.getLevel());
		}
	}

	private void cacheAllSkillLevels()
	{
		clientThread.invoke(() ->
		{
			for (Skill skill : Skill.values())
			{
				if (skill != Skill.OVERALL)
				{
					cachedSkillLevels.put(skill.name(), client.getRealSkillLevel(skill));
				}
			}
		});
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
			case "useShortestPath":
				if (config.useShortestPath())
				{
					if (!isShortestPathInstalled())
					{
						configManager.setConfiguration(GIMProgressTrackerConfig.GROUP, "useShortestPath", false);
						SwingUtilities.invokeLater(() ->
							javax.swing.JOptionPane.showMessageDialog(panel,
								"The Shortest Path plugin must be installed and enabled before this toggle can be turned on.\n"
									+ "Install it from the Plugin Hub, enable it, then re-enable this option.",
								"Vibe Steps Progress Tracker", javax.swing.JOptionPane.WARNING_MESSAGE));
						break;
					}
					maybeSendShortestPathTarget();
				}
				else
				{
					clearShortestPath();
				}
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
		maybeSendShortestPathTarget();
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

	private static final String DEFAULT_GUIDE_RESOURCE = "/guides/bruhsailer-guide.json";

	private void loadGuideFromConfig()
	{
		String pathStr = config.guideFilePath();
		if (pathStr == null || pathStr.trim().isEmpty())
		{
			loadDefaultGuide();
			return;
		}
		loadGuideFromFile(Paths.get(pathStr.trim()));
	}

	private void loadDefaultGuide()
	{
		try
		{
			Guide guide = guideImporter.loadFromResource(DEFAULT_GUIDE_RESOURCE);
			tracker.setGuide(guide);
			log.debug("Loaded bundled default guide '{}'", guide.getGuideName());
		}
		catch (IOException e)
		{
			log.warn("Failed to load bundled default guide: {}", e.getMessage());
		}
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

	private void openWorldMapAt(Location loc)
	{
		refreshWorldMapPoint();
		WorldPoint wp = new WorldPoint(loc.getX(), loc.getY(), loc.getPlane());
		clientThread.invoke(() ->
		{
			// Center the world map on the step tile.
			client.getWorldMap().setWorldMapPositionTarget(wp);
			// Open the world map if it is not already visible.
			Widget mapRoot = client.getWidget(InterfaceID.Worldmap.UNIVERSE);
			if (mapRoot == null || mapRoot.isHidden())
			{
				client.menuAction(1, InterfaceID.Orbs.ORB_WORLDMAP, MenuAction.CC_OP, -1, -1, "", "");
			}
		});
	}

	private void showTeammateMapPoint(Location loc)
	{
		clearTeammateMapPoint();
		WorldPoint wp = new WorldPoint(loc.getX(), loc.getY(), loc.getPlane());
		teammateMapPoint = WorldMapPoint.builder()
			.worldPoint(wp)
			.image(buildMapPointIcon(new Color(60, 140, 230)))
			.snapToEdge(true)
			.jumpOnClick(true)
			.name("Teammate step location")
			.build();
		worldMapPointManager.add(teammateMapPoint);
	}

	private void clearTeammateMapPoint()
	{
		if (teammateMapPoint != null)
		{
			worldMapPointManager.remove(teammateMapPoint);
			teammateMapPoint = null;
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

		// Snapshot current step so teammates can display it regardless of which guide they have loaded.
		Optional<StepEntry> currentStep = tracker.getCurrentStep();
		if (currentStep.isPresent())
		{
			StepEntry entry = currentStep.get();
			tracker.getProgress().setCurrentStepBreadcrumb(
				entry.getChapter().getName() + " › " + entry.getSection().getName());
			tracker.getProgress().setCurrentStepTldr(entry.getStep().getTldr());
			tracker.getProgress().setCurrentStepDescription(entry.getStep().getDescription());
			tracker.getProgress().setCurrentStepLocation(entry.getStep().getLocation());
		}
		else
		{
			tracker.getProgress().setCurrentStepBreadcrumb(null);
			tracker.getProgress().setCurrentStepTldr(null);
			tracker.getProgress().setCurrentStepDescription(null);
			tracker.getProgress().setCurrentStepLocation(null);
		}

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
		boolean relevant = false;
		if (id == InventoryID.INVENTORY.getId())
		{
			cachedInventory = buildItemMap(event.getItemContainer().getItems());
			relevant = true;
		}
		if (id == InventoryID.EQUIPMENT.getId())
		{
			cachedEquipment = buildItemMap(event.getItemContainer().getItems());
			relevant = true;
		}
		if (id == InventoryID.BANK.getId())
		{
			cachedBank = buildItemMap(event.getItemContainer().getItems());
			relevant = true;
		}
		if (id == InventoryID.GROUP_STORAGE.getId())
		{
			cachedGimBank = buildItemMap(event.getItemContainer().getItems());
			relevant = true;
		}
		if (relevant && panel != null)
		{
			panel.refresh();
		}
	}

	private Map<Integer, Integer> buildItemMap(Item[] items)
	{
		if (items == null)
		{
			return Collections.emptyMap();
		}
		Map<Integer, Integer> map = new HashMap<>();
		for (Item item : items)
		{
			if (item.getId() == -1)
			{
				continue;
			}
			int canonical = itemManager.canonicalize(item.getId());
			map.merge(canonical, item.getQuantity(), Integer::sum);
		}
		return map;
	}

	private ItemStatus checkItemStatus(RequiredItem required)
	{
		int itemId = required.getItemId();
		int needed = required.getQuantity();
		if (cachedInventory.getOrDefault(itemId, 0) >= needed)
		{
			return ItemStatus.IN_INVENTORY;
		}
		if (cachedEquipment.getOrDefault(itemId, 0) >= needed)
		{
			return ItemStatus.IN_EQUIPMENT;
		}
		if (cachedBank.getOrDefault(itemId, 0) >= needed)
		{
			return ItemStatus.IN_BANK;
		}
		if (cachedGimBank.getOrDefault(itemId, 0) >= needed)
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

	// --- Shortest Path integration (experimental) ---------------------------
	// Sends the current step's destination tile to the Shortest Path plugin via
	// its inter-plugin PluginMessage API. Shortest Path auto-clears its own path
	// when the player reaches the tile, so we only need to push a new target
	// when our step advances and clear when the feature is disabled.

	private static final String SHORTEST_PATH_NAMESPACE = "shortestpath";
	private static final String SHORTEST_PATH_MSG_PATH = "path";
	private static final String SHORTEST_PATH_MSG_CLEAR = "clear";
	private static final String SHORTEST_PATH_KEY_TARGET = "target";

	private boolean isShortestPathInstalled()
	{
		return pluginManager.getPlugins().stream()
			.anyMatch(p -> "ShortestPathPlugin".equals(p.getClass().getSimpleName())
				&& pluginManager.isPluginEnabled(p));
	}

	private void maybeSendShortestPathTarget()
	{
		if (!config.useShortestPath() || !isShortestPathInstalled())
		{
			return;
		}
		Optional<StepEntry> currentOpt = tracker.getCurrentStep();
		Location loc = currentOpt.map(s -> s.getStep().getLocation()).orElse(null);
		if (loc == null)
		{
			clearShortestPath();
			return;
		}
		Map<String, Object> data = new HashMap<>();
		data.put(SHORTEST_PATH_KEY_TARGET, new WorldPoint(loc.getX(), loc.getY(), loc.getPlane()));
		eventBus.post(new PluginMessage(SHORTEST_PATH_NAMESPACE, SHORTEST_PATH_MSG_PATH, data));
	}

	private void clearShortestPath()
	{
		if (!isShortestPathInstalled())
		{
			return;
		}
		eventBus.post(new PluginMessage(SHORTEST_PATH_NAMESPACE, SHORTEST_PATH_MSG_CLEAR));
	}

	private void openWikiForQuest(String questName)
	{
		String encoded = questName.replace(" ", "_").replace("'", "%27");
		LinkBrowser.browse("https://oldschool.runescape.wiki/w/" + encoded);
	}

	private void openQuestHelperForQuest(String questName)
	{
		// RuneLite's ClientUI.openPanel is package-private so we can't call it directly.
		// Best effort: copy the quest name to the clipboard so the user can paste it
		// into Quest Helper's search box, and show a notification as a nudge.
		Toolkit.getDefaultToolkit()
			.getSystemClipboard()
			.setContents(new StringSelection(questName), null);
		notifier.notify("Vibe Steps: search for \"" + questName + "\" in Quest Helper");
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
