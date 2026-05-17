package com.gimprogresstracker.ui;

import com.gimprogresstracker.ProgressTracker;
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
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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

	private final ProgressTracker tracker;
	private final ProgressStore progressStore;
	private final Supplier<String> sharedFolderPath;

	private final JPanel content;

	public TeammatesPanel(ProgressTracker tracker, ProgressStore progressStore,
		Supplier<String> sharedFolderPath)
	{
		super(false);
		this.tracker = tracker;
		this.progressStore = progressStore;
		this.sharedFolderPath = sharedFolderPath;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Fixed header
		JPanel header = buildHeader();
		add(header, BorderLayout.NORTH);

		// Scrollable body
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

			// If every file was the local player's own
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
			// Treat as empty list; error already surfaced by directory check above
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

		// Last updated
		String lastUpdated = formatLastUpdated(progress.getLastUpdated());
		card.add(mutedLabel(lastUpdated));
		card.add(Box.createVerticalStrut(6));

		// Guide check
		String localGuide = tracker.getGuide() != null ? tracker.getGuide().getGuideName() : null;
		String theirGuide = progress.getGuideName();
		boolean guideMismatch = localGuide != null && theirGuide != null && !localGuide.equals(theirGuide);

		if (guideMismatch)
		{
			JLabel warn = new JLabel("Guide: " + theirGuide);
			warn.setFont(FontManager.getRunescapeSmallFont());
			warn.setForeground(WARN_COLOR);
			warn.setAlignmentX(Component.LEFT_ALIGNMENT);
			card.add(warn);
			JLabel warnNote = new JLabel("(different from your loaded guide)");
			warnNote.setFont(FontManager.getRunescapeSmallFont());
			warnNote.setForeground(MUTED);
			warnNote.setAlignmentX(Component.LEFT_ALIGNMENT);
			card.add(warnNote);
		}
		else
		{
			// Progress stats
			int total = tracker.getTotalCount();
			int done = progress.getCompletedStepIds().size() + progress.getSkippedStepIds().size();
			int todo = progress.getTodoStepIds().size();

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

			// Current step
			Optional<StepEntry> currentOpt = tracker.getCurrentStepFor(progress);
			if (currentOpt.isPresent())
			{
				StepEntry entry = currentOpt.get();
				String breadcrumb = entry.getChapter().getName() + " › " + entry.getSection().getName();
				card.add(mutedLabel(breadcrumb));

				String tldr = entry.getStep().getTldr();
				String heading = (tldr != null && !tldr.isEmpty()) ? tldr : "Step " + entry.getStep().getId();
				JLabel stepLabel = new JLabel(heading);
				stepLabel.setFont(FontManager.getRunescapeBoldFont());
				stepLabel.setForeground(Color.WHITE);
				stepLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
				card.add(stepLabel);

				String desc = entry.getStep().getDescription();
				if (desc != null && !desc.isEmpty())
				{
					String preview = firstWords(desc, 20);
					JLabel descLabel = new JLabel("<html><body style='width:190px'>" + preview + "</body></html>");
					descLabel.setFont(FontManager.getRunescapeSmallFont());
					descLabel.setForeground(MUTED);
					descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
					card.add(Box.createVerticalStrut(2));
					card.add(descLabel);
				}
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

		return card;
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

		JLabel errLabel = new JLabel("<html><body style='width:190px'>Could not read: " + error + "</body></html>");
		errLabel.setFont(FontManager.getRunescapeSmallFont());
		errLabel.setForeground(MUTED);
		errLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(errLabel);

		return card;
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(8, 10, 8, 10)
		));

		JLabel title = new JLabel("Teammates");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		header.add(title, BorderLayout.WEST);

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
		header.add(refresh, BorderLayout.EAST);

		return header;
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
