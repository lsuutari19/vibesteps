package com.gimprogresstracker.ui;

import com.gimprogresstracker.ProgressTracker;
import com.gimprogresstracker.model.Guide;
import com.gimprogresstracker.model.StepEntry;
import com.gimprogresstracker.util.GuideImporter;
import java.util.List;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.runelite.client.util.LinkBrowser;
import com.gimprogresstracker.model.ItemStatus;
import com.gimprogresstracker.model.Location;
import com.gimprogresstracker.model.RequiredItem;
import com.gimprogresstracker.util.QuestDetector;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

public class GIMProgressTrackerPanel extends JPanel
{
	private static final Color ACCENT = ColorScheme.BRAND_ORANGE;
	private static final Color TODO_COLOR = new Color(70, 120, 200);
	private static final Color BTN_BG = ColorScheme.DARK_GRAY_COLOR;
	private static final Color BTN_HOVER = ColorScheme.DARK_GRAY_HOVER_COLOR;

	private final ProgressTracker tracker;
	private final Consumer<Path> onGuideFileChosen;
	private final Consumer<GuideImporter.BundledGuide> onBundledGuideSelected;
	private final Runnable onResetRequested;
	private final Supplier<Boolean> questHelperInstalled;
	private final Consumer<String> openQuestHelper;
	private final Consumer<String> openWikiGuide;
	private final ItemManager itemManager;
	private final Function<RequiredItem, ItemStatus> itemStatus;
	private final Function<Integer, String> itemName;
	private final Supplier<Boolean> bankReady;
	private final Supplier<Boolean> gimBankReady;
	private final Supplier<Boolean> isGroupIronman;
	private final Function<Skill, Integer> skillLevel;
	private final Consumer<Location> onMapClicked;
	private final Supplier<Boolean> pathTrackingEnabled;
	private final Runnable togglePathTracking;
	private final Supplier<String> loadNotes;
	private final Consumer<String> saveNotes;

	private final JPanel contentPanel = new JPanel();

	// Persist description expand state across refreshes so inventory/bank events
	// don't auto-collapse text the user has explicitly opened.
	private boolean descriptionExpanded = false;
	private int expandedDescriptionStepId = -1;

	// AFK suggestion state persists across refreshes triggered by inventory events.
	private AfkTaskSuggester.AfkTask currentAfkTask = null;

	// Notes content cached to survive rebuilds; null = not yet loaded from disk.
	private String notesContent = null;

	public GIMProgressTrackerPanel(ProgressTracker tracker, Consumer<Path> onGuideFileChosen,
		Consumer<GuideImporter.BundledGuide> onBundledGuideSelected, Runnable onResetRequested,
		Supplier<Boolean> questHelperInstalled, Consumer<String> openQuestHelper, Consumer<String> openWikiGuide,
		ItemManager itemManager,
		Function<RequiredItem, ItemStatus> itemStatus, Function<Integer, String> itemName,
		Supplier<Boolean> bankReady, Supplier<Boolean> gimBankReady, Supplier<Boolean> isGroupIronman,
		Function<Skill, Integer> skillLevel, Consumer<Location> onMapClicked,
		Supplier<Boolean> pathTrackingEnabled, Runnable togglePathTracking,
		Supplier<String> loadNotes, Consumer<String> saveNotes)
	{
		this.tracker = tracker;
		this.onGuideFileChosen = onGuideFileChosen;
		this.onBundledGuideSelected = onBundledGuideSelected;
		this.onResetRequested = onResetRequested;
		this.questHelperInstalled = questHelperInstalled;
		this.openQuestHelper = openQuestHelper;
		this.openWikiGuide = openWikiGuide;
		this.itemManager = itemManager;
		this.itemStatus = itemStatus;
		this.itemName = itemName;
		this.bankReady = bankReady;
		this.gimBankReady = gimBankReady;
		this.isGroupIronman = isGroupIronman;
		this.skillLevel = skillLevel;
		this.onMapClicked = onMapClicked;
		this.pathTrackingEnabled = pathTrackingEnabled;
		this.togglePathTracking = togglePathTracking;
		this.loadNotes = loadNotes;
		this.saveNotes = saveNotes;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));

		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(contentPanel, BorderLayout.NORTH);

		rebuild();
	}

	public void refresh()
	{
		SwingUtilities.invokeLater(this::rebuild);
	}

	public void refreshNotes()
	{
		notesContent = null; // force reload from disk on next rebuild
		SwingUtilities.invokeLater(this::rebuild);
	}

	private void rebuild()
	{
		contentPanel.removeAll();

		Guide guide = tracker.getGuide();
		if (guide == null)
		{
			contentPanel.add(noGuideSelectionPanel());
		}
		else
		{
			contentPanel.add(guideHeader(guide));
			contentPanel.add(Box.createVerticalStrut(10));
			contentPanel.add(progressSection());
			JPanel tips = setupTips();
			if (tips != null)
			{
				contentPanel.add(Box.createVerticalStrut(8));
				contentPanel.add(tips);
			}
			contentPanel.add(Box.createVerticalStrut(12));
			contentPanel.add(currentStepSection());
			contentPanel.add(Box.createVerticalStrut(12));
			contentPanel.add(upcomingSection());

			List<StepEntry> todos = tracker.getTodoSteps();
			if (!todos.isEmpty())
			{
				contentPanel.add(Box.createVerticalStrut(12));
				contentPanel.add(todoSection(todos));
			}

			contentPanel.add(Box.createVerticalStrut(12));
			contentPanel.add(afkSection());

			contentPanel.add(Box.createVerticalStrut(12));
			contentPanel.add(notesSection());

			contentPanel.add(Box.createVerticalStrut(14));
			contentPanel.add(buttonRow());
		}

		contentPanel.add(Box.createVerticalStrut(8));
		contentPanel.add(githubLink());

		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private JPanel panelTitle()
	{
		JPanel wrap = new JPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel title = new JLabel("VIBE STEPS PROGRESS TRACKER");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.add(title);

		wrap.add(Box.createVerticalStrut(4));

		JPanel accentBar = new JPanel();
		accentBar.setBackground(ACCENT);
		accentBar.setMaximumSize(new Dimension(46, 2));
		accentBar.setPreferredSize(new Dimension(46, 2));
		accentBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.add(accentBar);

		return wrap;
	}

	private JPanel noGuideSelectionPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Import from file
		JButton importBtn = styledButton("Import guide…", Color.WHITE);
		importBtn.addActionListener(e -> chooseGuideFile());
		panel.add(importBtn);

		List<GuideImporter.BundledGuide> bundled = GuideImporter.BUNDLED_GUIDES;
		if (!bundled.isEmpty())
		{
			panel.add(Box.createVerticalStrut(12));

			JLabel orLabel = new JLabel("— or start with a bundled guide —");
			orLabel.setFont(FontManager.getRunescapeSmallFont());
			orLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			orLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.add(orLabel);

			panel.add(Box.createVerticalStrut(6));

			for (GuideImporter.BundledGuide guide : bundled)
			{
				String label = guide.getDisplayName()
					+ (guide.getAuthor() != null && !guide.getAuthor().isEmpty()
						? "  ·  " + guide.getAuthor()
						: "");
				JButton btn = styledButton(label, ColorScheme.LIGHT_GRAY_COLOR);
				btn.addActionListener(e -> onBundledGuideSelected.accept(guide));
				panel.add(btn);
				panel.add(Box.createVerticalStrut(4));
			}
		}

		return panel;
	}

	private JPanel guideHeader(Guide guide)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel name = new JLabel("<html><body style='width: 180px'>" + escapeHtml(guide.getGuideName()) + "</body></html>");
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(ACCENT);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(name);

		StringBuilder meta = new StringBuilder();
		if (guide.getAuthor() != null && !guide.getAuthor().isEmpty())
		{
			meta.append("by ").append(guide.getAuthor());
		}
		if (guide.getVersion() != null && !guide.getVersion().isEmpty())
		{
			if (meta.length() > 0)
			{
				meta.append("  •  ");
			}
			meta.append("v").append(guide.getVersion());
		}
		if (meta.length() > 0)
		{
			JLabel m = new JLabel(meta.toString());
			m.setFont(FontManager.getRunescapeSmallFont());
			m.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			m.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.add(Box.createVerticalStrut(2));
			panel.add(m);
		}
		return panel;
	}

	private JPanel progressSection()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(8, 10, 8, 10)));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		int total = tracker.getTotalCount();
		int completed = tracker.getCompletedCount();
		int skipped = tracker.getSkippedCount();
		int todo = tracker.getTodoCount();
		int done = completed + skipped;
		int pct = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);

		JPanel topRow = new JPanel(new BorderLayout());
		topRow.setOpaque(false);
		topRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		topRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

		JLabel header = new JLabel("Progress");
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		topRow.add(header, BorderLayout.WEST);

		JLabel pctLabel = new JLabel(pct + "%");
		pctLabel.setFont(FontManager.getRunescapeBoldFont());
		pctLabel.setForeground(ACCENT);
		topRow.add(pctLabel, BorderLayout.EAST);

		panel.add(topRow);
		panel.add(Box.createVerticalStrut(6));
		panel.add(segmentedProgressBar(total, done, todo));
		panel.add(Box.createVerticalStrut(6));

		int remaining = Math.max(total - done - todo, 0);
		String statsText = String.format("%d done  •  %d todo  •  %d left", done, todo, remaining);
		JLabel stats = new JLabel(statsText);
		stats.setFont(FontManager.getRunescapeSmallFont());
		stats.setForeground(Color.WHITE);
		stats.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(stats);

		return panel;
	}

	private JPanel segmentedProgressBar(int total, int done, int todo)
	{
		return new JPanel()
		{
			{
				setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
				setPreferredSize(new Dimension(0, 10));
				setAlignmentX(Component.LEFT_ALIGNMENT);
				setOpaque(false);
			}

			@Override
			protected void paintComponent(Graphics g)
			{
				int w = getWidth();
				int h = getHeight();
				g.setColor(ColorScheme.DARK_GRAY_COLOR);
				g.fillRect(0, 0, w, h);

				int safeTotal = Math.max(total, 1);
				int doneW = (int) Math.round(done * (double) w / safeTotal);
				int todoW = (int) Math.round(todo * (double) w / safeTotal);

				if (doneW > 0)
				{
					g.setColor(ACCENT);
					g.fillRect(0, 0, doneW, h);
				}
				if (todoW > 0)
				{
					g.setColor(TODO_COLOR);
					g.fillRect(doneW, 0, todoW, h);
				}

				g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
				g.drawRect(0, 0, w - 1, h - 1);
			}
		};
	}

	private JPanel setupTips()
	{
		boolean needBank = !bankReady.get();
		boolean needGim = isGroupIronman.get() && !gimBankReady.get();
		boolean needQH = !questHelperInstalled.get();

		if (!needBank && !needGim && !needQH)
		{
			return null;
		}

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(new Color(28, 32, 36));
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(60, 70, 80)),
			BorderFactory.createEmptyBorder(7, 9, 7, 9)));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel header = new JLabel("SETUP TIPS");
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(new Color(140, 170, 200));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(header);
		panel.add(Box.createVerticalStrut(4));

		if (needBank)
		{
			panel.add(tipRow("Open your bank once to enable item tracking"));
		}
		if (needGim)
		{
			panel.add(tipRow("Open GIM shared storage to track group items"));
		}
		if (needQH)
		{
			panel.add(tipRow("Install Quest Helper for enhanced quest buttons"));
		}

		return panel;
	}

	private static JLabel tipRow(String text)
	{
		JLabel l = new JLabel("<html><body style='width: 150px'>• " + text + "</body></html>");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(new Color(170, 190, 210));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private JPanel currentStepSection()
	{
		Optional<StepEntry> currentOpt = tracker.getCurrentStep();
		if (!currentOpt.isPresent())
		{
			JPanel done = new JPanel();
			done.setLayout(new BoxLayout(done, BoxLayout.Y_AXIS));
			done.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			done.setBorder(BorderFactory.createCompoundBorder(
				new MatteBorder(0, 3, 0, 0, ColorScheme.PROGRESS_COMPLETE_COLOR),
				BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(1, 0, 1, 1, ColorScheme.MEDIUM_GRAY_COLOR),
					BorderFactory.createEmptyBorder(14, 12, 14, 12))));
			done.setAlignmentX(Component.LEFT_ALIGNMENT);

			JLabel l = new JLabel("Guide complete!");
			l.setFont(FontManager.getRunescapeBoldFont());
			l.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
			l.setAlignmentX(Component.LEFT_ALIGNMENT);
			done.add(l);

			JLabel sub = new JLabel("Every step has been completed or skipped.");
			sub.setFont(FontManager.getRunescapeSmallFont());
			sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			sub.setAlignmentX(Component.LEFT_ALIGNMENT);
			done.add(Box.createVerticalStrut(4));
			done.add(sub);
			return done;
		}
		StepEntry current = currentOpt.get();
		String questName = current.getStep().getQuestName();
		if (questName == null || questName.isEmpty())
		{
			questName = QuestDetector.detectQuestName(current.getStep().getDescription()).orElse(null);
		}

		Runnable wikiAction = null;
		Runnable questHelperAction = null;
		if (questName != null)
		{
			final String finalQuestName = questName;
			wikiAction = () -> openWikiGuide.accept(finalQuestName);
			if (questHelperInstalled.get())
			{
				questHelperAction = () -> openQuestHelper.accept(finalQuestName);
			}
		}

		int stepId = current.getStep().getId();
		boolean startExpanded = descriptionExpanded && expandedDescriptionStepId == stepId;
		Runnable onDescriptionToggled = () -> {
			descriptionExpanded = !descriptionExpanded;
			expandedDescriptionStepId = stepId;
		};
		StepCardPanel card = new StepCardPanel(current, tracker, questName, wikiAction, questHelperAction,
			itemManager, itemStatus, itemName, startExpanded, onDescriptionToggled, onMapClicked,
			pathTrackingEnabled, togglePathTracking);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		return card;
	}

	private JPanel upcomingSection()
	{
		List<StepEntry> upcoming = tracker.getUpcoming(3);
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		if (upcoming.isEmpty())
		{
			return panel;
		}

		JLabel header = new JLabel("UPCOMING");
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(ACCENT);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(header);
		panel.add(Box.createVerticalStrut(6));

		for (int i = 0; i < upcoming.size(); i++)
		{
			panel.add(upcomingRow(upcoming.get(i), i == 0));
			panel.add(Box.createVerticalStrut(4));
		}
		return panel;
	}

	private JPanel upcomingRow(StepEntry entry, boolean showDescription)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(0, 2, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel id = new JLabel("#" + entry.getStep().getId());
		id.setFont(FontManager.getRunescapeSmallFont());
		id.setForeground(ACCENT);
		row.add(id, BorderLayout.WEST);

		String inner;
		if (showDescription)
		{
			inner = escapeHtml(firstWords(entry.getStep().getDescription(), 50));
		}
		else
		{
			String tldr = entry.getStep().getTldr();
			inner = (tldr != null && !tldr.isEmpty())
				? escapeHtml(tldr)
				: escapeHtml(shorten(entry.getStep().getDescription(), 40));
		}
		String labelText = "<html><body style='width: 140px'>" + inner + "</body></html>";
		JLabel d = new JLabel(labelText);
		d.setFont(FontManager.getRunescapeSmallFont());
		d.setForeground(Color.WHITE);
		row.add(d, BorderLayout.CENTER);

		return row;
	}

	private JPanel todoSection(List<StepEntry> todos)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel header = new JLabel("TO-DO LATER");
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(TODO_COLOR);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(header);
		panel.add(Box.createVerticalStrut(6));

		for (StepEntry e : todos)
		{
			panel.add(todoRow(e));
			panel.add(Box.createVerticalStrut(4));
		}
		return panel;
	}

	private JPanel todoRow(StepEntry entry)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(0, 2, 0, 0, TODO_COLOR),
			BorderFactory.createEmptyBorder(4, 8, 4, 6)));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.setOpaque(false);

		JLabel id = new JLabel("#" + entry.getStep().getId());
		id.setFont(FontManager.getRunescapeSmallFont());
		id.setForeground(TODO_COLOR);
		left.add(id);

		String tldr = entry.getStep().getTldr();
		String labelText = (tldr != null && !tldr.isEmpty()) ? escapeHtml(tldr)
			: escapeHtml(shorten(entry.getStep().getDescription(), 40));
		JLabel d = new JLabel("<html><body style='width: 115px'>" + labelText + "</body></html>");
		d.setFont(FontManager.getRunescapeSmallFont());
		d.setForeground(Color.WHITE);
		left.add(d);

		row.add(left, BorderLayout.CENTER);

		JButton activate = new JButton("Activate");
		activate.setFont(FontManager.getRunescapeSmallFont());
		activate.setForeground(Color.WHITE);
		activate.setBackground(new Color(40, 60, 100));
		activate.setOpaque(true);
		activate.setContentAreaFilled(true);
		activate.setFocusPainted(false);
		activate.setRolloverEnabled(false);
		activate.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(TODO_COLOR),
			BorderFactory.createEmptyBorder(3, 6, 3, 6)));
		activate.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		activate.addActionListener(e -> tracker.unmark(entry.getStep().getId()));
		activate.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				activate.setBackground(new Color(60, 90, 150));
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				activate.setBackground(new Color(40, 60, 100));
			}
		});
		row.add(activate, BorderLayout.EAST);

		return row;
	}

	private JPanel afkSection()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(8, 10, 8, 10)));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel header = new JLabel("AFK SUGGESTION");
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(new Color(140, 190, 140));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(header);

		if (currentAfkTask == null)
		{
			panel.add(Box.createVerticalStrut(6));
			JButton suggest = styledButton("Suggest AFK task", ColorScheme.LIGHT_GRAY_COLOR);
			suggest.addActionListener(e ->
			{
				currentAfkTask = AfkTaskSuggester.suggest(skillLevel, null);
				refresh();
			});
			panel.add(suggest);
		}
		else
		{
			panel.add(Box.createVerticalStrut(6));

			JLabel taskTitle = new JLabel(currentAfkTask.title);
			taskTitle.setFont(FontManager.getRunescapeBoldFont());
			taskTitle.setForeground(new Color(140, 210, 140));
			taskTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.add(taskTitle);

			panel.add(Box.createVerticalStrut(3));

			JLabel detail = new JLabel("<html><body style='width: 155px'>" + escapeHtml(currentAfkTask.detail) + "</body></html>");
			detail.setFont(FontManager.getRunescapeSmallFont());
			detail.setForeground(Color.WHITE);
			detail.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.add(detail);

			panel.add(Box.createVerticalStrut(8));

			JPanel btnRow = new JPanel();
			btnRow.setLayout(new BoxLayout(btnRow, BoxLayout.X_AXIS));
			btnRow.setOpaque(false);
			btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

			JButton regen = styledButton("Regenerate", ColorScheme.LIGHT_GRAY_COLOR);
			regen.setMaximumSize(regen.getPreferredSize());
			regen.addActionListener(e ->
			{
				currentAfkTask = AfkTaskSuggester.suggest(skillLevel, currentAfkTask);
				refresh();
			});
			btnRow.add(regen);

			btnRow.add(Box.createHorizontalStrut(4));

			JButton dismiss = styledButton("Dismiss", ColorScheme.LIGHT_GRAY_COLOR);
			dismiss.setMaximumSize(dismiss.getPreferredSize());
			dismiss.addActionListener(e ->
			{
				currentAfkTask = null;
				refresh();
			});
			btnRow.add(dismiss);

			panel.add(btnRow);
		}

		return panel;
	}

	private JPanel notesSection()
	{
		if (notesContent == null)
		{
			notesContent = loadNotes.get();
		}

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(8, 10, 8, 10)));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel header = new JLabel("MY NOTES");
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(new Color(160, 190, 220));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(header);
		panel.add(Box.createVerticalStrut(6));

		JTextArea area = new JTextArea(notesContent, 5, 20);
		area.setWrapStyleWord(true);
		area.setLineWrap(true);
		area.setFont(FontManager.getRunescapeSmallFont());
		area.setForeground(Color.WHITE);
		area.setBackground(ColorScheme.DARK_GRAY_COLOR);
		area.setCaretColor(Color.WHITE);
		area.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		area.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent e) { save(); }
			@Override public void removeUpdate(DocumentEvent e) { save(); }
			@Override public void changedUpdate(DocumentEvent e) { save(); }

			private void save()
			{
				notesContent = area.getText();
				saveNotes.accept(notesContent);
			}
		});

		JScrollPane scroll = new JScrollPane(area);
		scroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
		panel.add(scroll);

		return panel;
	}

	private JPanel githubLink()
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		javax.swing.ImageIcon icon = PanelIcons.githubIcon(14);
		JLabel iconLabel = new JLabel(icon);
		iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
		row.add(iconLabel);

		JLabel link = new JLabel("Support / GitHub");
		link.setFont(FontManager.getRunescapeSmallFont());
		link.setForeground(new Color(100, 140, 200));
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.setToolTipText("Open the plugin's GitHub page for support and issue reports");
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse("https://github.com/lsuutari19/vibesteps/issues");
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				link.setForeground(new Color(140, 180, 255));
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				link.setForeground(new Color(100, 140, 200));
			}
		});
		row.add(link);
		row.add(Box.createHorizontalGlue());

		return row;
	}

	private JPanel buttonRow()
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton importBtn = styledButton("Import guide…", Color.WHITE);
		importBtn.setToolTipText("Load a different guide from a .json file");
		importBtn.addActionListener(e -> chooseGuideFile());
		row.add(importBtn);

		row.add(Box.createVerticalStrut(4));
		JButton reset = styledButton("Reset progress", ColorScheme.LIGHT_GRAY_COLOR);
		reset.addActionListener(e -> {
			int choice = JOptionPane.showConfirmDialog(this,
				"Clear all completed, skipped, and to-do steps for this guide?",
				"Reset progress", JOptionPane.OK_CANCEL_OPTION);
			if (choice == JOptionPane.OK_OPTION)
			{
				onResetRequested.run();
			}
		});
		row.add(reset);

		return row;
	}

	private static JButton styledButton(String text, Color textColor)
	{
		JButton b = new JButton(text);
		b.setFocusPainted(false);
		b.setRolloverEnabled(false);
		b.setForeground(textColor);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setBackground(BTN_BG);
		b.setOpaque(true);
		b.setContentAreaFilled(true);
		b.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 10, 6, 10)));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		b.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				b.setBackground(BTN_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				b.setBackground(BTN_BG);
			}
		});
		return b;
	}

	private void chooseGuideFile()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Choose guide JSON");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setFileFilter(new FileNameExtensionFilter("Guide JSON", "json"));
		int result = chooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION)
		{
			File file = chooser.getSelectedFile();
			if (file != null)
			{
				onGuideFileChosen.accept(file.toPath());
			}
		}
	}

	private static String escapeHtml(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String shorten(String s, int max)
	{
		if (s == null)
		{
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max) + "…";
	}

	private static String firstWords(String s, int wordCount)
	{
		if (s == null)
		{
			return "";
		}
		String[] words = s.split("\\s+");
		if (words.length <= wordCount)
		{
			return s;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < wordCount; i++)
		{
			if (i > 0)
			{
				sb.append(' ');
			}
			sb.append(words[i]);
		}
		return sb.append("…").toString();
	}
}
