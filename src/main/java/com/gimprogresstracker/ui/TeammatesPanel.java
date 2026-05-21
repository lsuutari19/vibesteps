package com.gimprogresstracker.ui;

import com.gimprogresstracker.ProgressTracker;
import com.gimprogresstracker.model.Location;
import com.gimprogresstracker.model.PlayerProgress;
import com.gimprogresstracker.model.StepEntry;
import com.gimprogresstracker.util.PluginPaths;
import com.gimprogresstracker.util.ProgressStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class TeammatesPanel extends PluginPanel
{
	private static final Color CARD_BG = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color WARN_COLOR = new Color(220, 160, 40);
	private static final Color DONE_COLOR = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color MAP_BTN_FG = new Color(100, 170, 240);
	private static final Color LIVE_COLOR = new Color(80, 210, 100);

	private final ProgressTracker tracker;
	private final ProgressStore progressStore;
	private final Supplier<String> sharedFolderPath;
	private final Consumer<Location> onMapClicked;
	private final BooleanSupplier isShareLocationEnabled;
	private final Consumer<Boolean> setShareLocation;

	private final JPanel content;
	private JCheckBox locationShareToggle;

	public TeammatesPanel(ProgressTracker tracker, ProgressStore progressStore,
		Supplier<String> sharedFolderPath, Consumer<Location> onMapClicked,
		BooleanSupplier isShareLocationEnabled, Consumer<Boolean> setShareLocation)
	{
		super(false);
		this.tracker = tracker;
		this.progressStore = progressStore;
		this.sharedFolderPath = sharedFolderPath;
		this.onMapClicked = onMapClicked;
		this.isShareLocationEnabled = isShareLocationEnabled;
		this.setShareLocation = setShareLocation;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = buildHeader();
		add(header, BorderLayout.NORTH);

		content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane scroll = new JScrollPane(content);
		scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		add(scroll, BorderLayout.CENTER);

		rebuild();
	}

	public void rebuild()
	{
		SwingUtilities.invokeLater(() ->
		{
			content.removeAll();
			content.add(Box.createVerticalStrut(6));

			String folderStr = sharedFolderPath.get();
			if (folderStr == null || folderStr.trim().isEmpty())
			{
				content.add(infoLabel("No shared folder configured.", MUTED));
				content.add(Box.createVerticalStrut(4));
				content.add(infoLabel("Set the Shared folder path in plugin settings", MUTED));
				content.add(Box.createVerticalStrut(4));
				content.add(infoLabel("to see your teammates' progress here.", MUTED));
				content.revalidate();
				content.repaint();
				return;
			}

			Path dir = Paths.get(folderStr.trim());
			if (!Files.isDirectory(dir))
			{
				content.add(infoLabel("Shared folder not found:", WARN_COLOR));
				content.add(Box.createVerticalStrut(2));
				content.add(infoLabel(dir.toString(), MUTED));
				content.revalidate();
				content.repaint();
				return;
			}

			List<Path> files = findProgressFiles(dir);
			if (files.isEmpty())
			{
				content.add(infoLabel("No teammate progress files found.", MUTED));
				content.add(Box.createVerticalStrut(4));
				content.add(infoLabel("Files should be named <name>_progress.json", MUTED));
				content.revalidate();
				content.repaint();
				return;
			}

			String localSanitized = PluginPaths.sanitizeForFilename(tracker.getPlayerName());
			String localFile = localSanitized + "_progress.json";

			for (Path file : files)
			{
				if (file.getFileName().toString().equalsIgnoreCase(localFile))
				{
					continue;
				}
				JPanel card = buildTeammateCard(file);
				if (card != null)
				{
					content.add(card);
					content.add(Box.createVerticalStrut(6));
				}
			}

			if (content.getComponentCount() <= 1)
			{
				content.add(infoLabel("No other teammates found in shared folder.", MUTED));
			}

			content.revalidate();
			content.repaint();
		});
	}

	private List<Path> findProgressFiles(Path dir)
	{
		List<Path> out = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*_progress.json"))
		{
			for (Path p : stream)
			{
				if (Files.isRegularFile(p))
				{
					out.add(p);
				}
			}
		}
		catch (IOException e)
		{
			// treated as empty; directory existence already checked above
		}
		return out;
	}

	private JPanel buildTeammateCard(Path file)
	{
		PlayerProgress progress;
		try
		{
			progress = progressStore.readPlayerProgress(file);
		}
		catch (IOException e)
		{
			return errorCard(file.getFileName().toString(), e.getMessage());
		}

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(CARD_BG);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, ColorScheme.BRAND_ORANGE),
			BorderFactory.createEmptyBorder(8, 10, 8, 10)
		));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		// Player name
		String displayName = progress.getPlayerName() != null && !progress.getPlayerName().isEmpty()
			? progress.getPlayerName()
			: file.getFileName().toString().replace("_progress.json", "");

		JLabel nameLabel = new JLabel(displayName);
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(nameLabel);

		card.add(mutedLabel(formatLastUpdated(progress.getLastUpdated())));
		card.add(Box.createVerticalStrut(6));

		String localGuide = tracker.getGuide() != null ? tracker.getGuide().getGuideName() : null;
		String theirGuide = progress.getGuideName();
		boolean guideMismatch = localGuide != null && theirGuide != null && !localGuide.equals(theirGuide);

		int done = progress.getCompletedStepIds().size() + progress.getSkippedStepIds().size();
		int todo = progress.getTodoStepIds().size();

		if (guideMismatch)
		{
			if (theirGuide != null && !theirGuide.isEmpty())
			{
				card.add(mutedLabel("Guide: " + theirGuide));
				card.add(Box.createVerticalStrut(3));
			}
			// Raw counts — no % since we don't have their guide's total step count
			if (done > 0 || todo > 0)
			{
				JLabel statsLabel = new JLabel(done + " steps done" + (todo > 0 ? "  •  " + todo + " todo" : ""));
				statsLabel.setFont(FontManager.getRunescapeSmallFont());
				statsLabel.setForeground(DONE_COLOR);
				statsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
				card.add(statsLabel);
			}
			card.add(Box.createVerticalStrut(5));
			// Use the snapshot written into the export file at their end
			addCurrentStepSection(card,
				progress.getCurrentStepBreadcrumb(),
				progress.getCurrentStepTldr(),
				progress.getCurrentStepDescription(),
				progress.getCurrentStepLocation());
		}
		else
		{
			int total = tracker.getTotalCount();
			if (total > 0)
			{
				String pct = String.format("%d%%", Math.round(done * 100.0 / total));
				JLabel statsLabel = new JLabel(pct + " done  •  " + done + "/" + total + " steps"
					+ (todo > 0 ? "  •  " + todo + " todo" : ""));
				statsLabel.setFont(FontManager.getRunescapeSmallFont());
				statsLabel.setForeground(DONE_COLOR);
				statsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
				card.add(statsLabel);
			}
			else if (theirGuide != null)
			{
				card.add(mutedLabel("Guide: " + theirGuide));
			}

			card.add(Box.createVerticalStrut(5));

			Optional<StepEntry> currentOpt = tracker.getCurrentStepFor(progress);
			if (currentOpt.isPresent())
			{
				StepEntry entry = currentOpt.get();
				addCurrentStepSection(card,
					entry.getChapter().getName() + " › " + entry.getSection().getName(),
					entry.getStep().getTldr(),
					entry.getStep().getDescription(),
					entry.getStep().getLocation());
			}
			else if (total > 0 && done >= total)
			{
				JLabel doneLabel = new JLabel("Guide complete!");
				doneLabel.setFont(FontManager.getRunescapeBoldFont());
				doneLabel.setForeground(DONE_COLOR);
				doneLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
				card.add(doneLabel);
			}
		}

		Location liveLoc = progress.getLiveLocation();
		String liveUpdated = progress.getLiveLocationUpdated();
		if (liveLoc != null && isLiveRecent(liveUpdated))
		{
			card.add(Box.createVerticalStrut(4));
			addLiveLocationRow(card, liveLoc, liveUpdated);
		}

		return card;
	}

	private void addLiveLocationRow(JPanel card, Location loc, String updatedIso)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

		JLabel dot = new JLabel("● ");
		dot.setFont(FontManager.getRunescapeSmallFont());
		dot.setForeground(LIVE_COLOR);
		row.add(dot);

		JLabel liveLabel = new JLabel("Live · " + formatLiveAgo(updatedIso));
		liveLabel.setFont(FontManager.getRunescapeSmallFont());
		liveLabel.setForeground(LIVE_COLOR);
		row.add(liveLabel);

		row.add(Box.createHorizontalStrut(6));

		JButton mapBtn = new JButton("Map");
		mapBtn.setFont(FontManager.getRunescapeSmallFont());
		mapBtn.setForeground(MAP_BTN_FG);
		mapBtn.setBackground(CARD_BG);
		mapBtn.setBorder(BorderFactory.createLineBorder(MAP_BTN_FG, 1));
		mapBtn.setFocusPainted(false);
		mapBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		mapBtn.setToolTipText("Open world map at this teammate's current location");
		mapBtn.addActionListener(e -> onMapClicked.accept(loc));
		row.add(mapBtn);

		row.add(Box.createHorizontalGlue());
		card.add(row);
	}

	private static boolean isLiveRecent(String iso)
	{
		if (iso == null)
		{
			return false;
		}
		try
		{
			return Duration.between(Instant.parse(iso), Instant.now()).toMinutes() < 10;
		}
		catch (Exception e)
		{
			return false;
		}
	}

	private static String formatLiveAgo(String iso)
	{
		if (iso == null)
		{
			return "";
		}
		try
		{
			Duration ago = Duration.between(Instant.parse(iso), Instant.now());
			long secs = ago.getSeconds();
			if (secs < 60)
			{
				return secs + "s ago";
			}
			return ago.toMinutes() + "m ago";
		}
		catch (Exception e)
		{
			return "";
		}
	}

	private void addCurrentStepSection(JPanel card, String breadcrumb, String tldr, String desc, Location loc)
	{
		if (breadcrumb == null && tldr == null && desc == null)
		{
			return;
		}
		if (breadcrumb != null)
		{
			card.add(mutedLabel(breadcrumb));
		}
		if (tldr != null || loc != null)
		{
			String heading = (tldr != null && !tldr.isEmpty()) ? tldr : "Current step";

			JPanel stepRow = new JPanel();
			stepRow.setLayout(new BoxLayout(stepRow, BoxLayout.X_AXIS));
			stepRow.setOpaque(false);
			stepRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			stepRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

			JLabel stepLabel = new JLabel(heading);
			stepLabel.setFont(FontManager.getRunescapeBoldFont());
			stepLabel.setForeground(Color.WHITE);
			stepRow.add(stepLabel);

			if (loc != null)
			{
				stepRow.add(Box.createHorizontalStrut(6));
				JButton mapBtn = new JButton("Map");
				mapBtn.setFont(FontManager.getRunescapeSmallFont());
				mapBtn.setForeground(MAP_BTN_FG);
				mapBtn.setBackground(CARD_BG);
				mapBtn.setBorder(BorderFactory.createLineBorder(MAP_BTN_FG, 1));
				mapBtn.setFocusPainted(false);
				mapBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				mapBtn.setToolTipText("Mark this location on the world map");
				mapBtn.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseEntered(MouseEvent e)
					{
						mapBtn.setBackground(new Color(30, 60, 100));
					}

					@Override
					public void mouseExited(MouseEvent e)
					{
						mapBtn.setBackground(CARD_BG);
					}
				});
				mapBtn.addActionListener(e -> onMapClicked.accept(loc));
				stepRow.add(mapBtn);

				stepRow.add(Box.createHorizontalStrut(4));
				stepRow.add(mapInfoIcon());
			}

			stepRow.add(Box.createHorizontalGlue());
			card.add(stepRow);
		}
		if (desc != null && !desc.isEmpty())
		{
			String preview = firstWords(desc, 20);
			JLabel descLabel = new JLabel("<html><body style='width:175px'>" + preview + "</body></html>");
			descLabel.setFont(FontManager.getRunescapeSmallFont());
			descLabel.setForeground(MUTED);
			descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			card.add(Box.createVerticalStrut(2));
			card.add(descLabel);
		}
	}

	private static JLabel mapInfoIcon()
	{
		JLabel info = new JLabel("ⓘ");
		info.setFont(FontManager.getRunescapeSmallFont());
		info.setForeground(MUTED);
		info.setToolTipText("Open the world map first — clicking Map will focus it on the current step location.");
		info.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		return info;
	}

	private JPanel errorCard(String filename, String error)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(CARD_BG);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, WARN_COLOR),
			BorderFactory.createEmptyBorder(8, 10, 8, 10)
		));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		JLabel name = new JLabel(filename);
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(WARN_COLOR);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(name);

		JLabel errLabel = new JLabel("<html><body style='width:175px'>Could not read: " + error + "</body></html>");
		errLabel.setFont(FontManager.getRunescapeSmallFont());
		errLabel.setForeground(MUTED);
		errLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(errLabel);

		return card;
	}

	private JPanel buildHeader()
	{
		JPanel outer = new JPanel();
		outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
		outer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		outer.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR));

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		titleRow.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));

		JLabel title = new JLabel("Teammates");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		titleRow.add(title, BorderLayout.WEST);

		JButton refresh = new JButton("Refresh");
		refresh.setFont(FontManager.getRunescapeSmallFont());
		refresh.setBackground(ColorScheme.DARK_GRAY_COLOR);
		refresh.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		refresh.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		refresh.setFocusPainted(false);
		refresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		refresh.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				refresh.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				refresh.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		});
		refresh.addActionListener(e -> rebuild());
		titleRow.add(refresh, BorderLayout.EAST);
		outer.add(titleRow);

		JPanel toggleRow = new JPanel(new BorderLayout());
		toggleRow.setOpaque(false);
		toggleRow.setBorder(BorderFactory.createEmptyBorder(2, 10, 8, 10));

		locationShareToggle = new JCheckBox("Share my location");
		locationShareToggle.setFont(FontManager.getRunescapeSmallFont());
		locationShareToggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		locationShareToggle.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		locationShareToggle.setFocusPainted(false);
		boolean hasFolder = hasSharedFolder();
		locationShareToggle.setEnabled(hasFolder);
		locationShareToggle.setSelected(hasFolder && isShareLocationEnabled.getAsBoolean());
		locationShareToggle.setToolTipText(hasFolder
			? "Periodically write your location to the shared folder"
			: "Configure a shared folder first");
		locationShareToggle.addActionListener(e -> setShareLocation.accept(locationShareToggle.isSelected()));
		toggleRow.add(locationShareToggle, BorderLayout.WEST);
		outer.add(toggleRow);

		return outer;
	}

	public void refreshLocationToggle()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (locationShareToggle == null)
			{
				return;
			}
			boolean hasFolder = hasSharedFolder();
			locationShareToggle.setEnabled(hasFolder);
			locationShareToggle.setToolTipText(hasFolder
				? "Periodically write your location to the shared folder"
				: "Configure a shared folder first");
			boolean active = hasFolder && isShareLocationEnabled.getAsBoolean();
			if (locationShareToggle.isSelected() != active)
			{
				locationShareToggle.setSelected(active);
			}
		});
	}

	private boolean hasSharedFolder()
	{
		String folder = sharedFolderPath.get();
		return folder != null && !folder.trim().isEmpty();
	}

	private JLabel mutedLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(MUTED);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private JLabel infoLabel(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
		return label;
	}

	private static String formatLastUpdated(String iso)
	{
		if (iso == null || iso.isEmpty())
		{
			return "Last updated: unknown";
		}
		try
		{
			Instant then = Instant.parse(iso);
			Duration ago = Duration.between(then, Instant.now());
			long mins = ago.toMinutes();
			if (mins < 1)
			{
				return "Updated just now";
			}
			if (mins < 60)
			{
				return "Updated " + mins + " min ago";
			}
			long hours = ago.toHours();
			if (hours < 24)
			{
				return "Updated " + hours + "h ago";
			}
			return "Updated " + ago.toDays() + "d ago";
		}
		catch (Exception e)
		{
			return "Last updated: " + iso;
		}
	}

	private static String firstWords(String text, int max)
	{
		if (text == null)
		{
			return "";
		}
		String flat = text.replace('\n', ' ').trim();
		String[] words = flat.split("\\s+");
		if (words.length <= max)
		{
			return flat;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < max; i++)
		{
			if (i > 0)
			{
				sb.append(' ');
			}
			sb.append(words[i]);
		}
		sb.append("…");
		return sb.toString();
	}
}
