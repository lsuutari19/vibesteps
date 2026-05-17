package com.gimprogresstracker.ui;

import com.gimprogresstracker.ProgressTracker;
import com.gimprogresstracker.model.Guide;
import com.gimprogresstracker.model.StepEntry;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.border.MatteBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import com.gimprogresstracker.util.QuestDetector;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class GIMProgressTrackerPanel extends PluginPanel
{
	private static final Color ACCENT = ColorScheme.BRAND_ORANGE;
	private static final Color BTN_BG = ColorScheme.DARK_GRAY_COLOR;
	private static final Color BTN_HOVER = ColorScheme.DARK_GRAY_HOVER_COLOR;

	private final ProgressTracker tracker;
	private final Consumer<Path> onGuideFileChosen;
	private final Runnable onResetRequested;
	private final Supplier<Boolean> questHelperInstalled;
	private final Consumer<String> openQuestGuide;

	private final JPanel contentPanel = new JPanel();

	public GIMProgressTrackerPanel(ProgressTracker tracker, Consumer<Path> onGuideFileChosen, Runnable onResetRequested,
		Supplier<Boolean> questHelperInstalled, Consumer<String> openQuestGuide)
	{
		super(false);
		this.tracker = tracker;
		this.onGuideFileChosen = onGuideFileChosen;
		this.onResetRequested = onResetRequested;
		this.questHelperInstalled = questHelperInstalled;
		this.openQuestGuide = openQuestGuide;

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

	private void rebuild()
	{
		contentPanel.removeAll();

		contentPanel.add(panelTitle());
		contentPanel.add(Box.createVerticalStrut(10));

		Guide guide = tracker.getGuide();
		if (guide == null)
		{
			contentPanel.add(noGuideHelp());
		}
		else
		{
			contentPanel.add(guideHeader(guide));
			contentPanel.add(Box.createVerticalStrut(10));
			contentPanel.add(progressSection());
			contentPanel.add(Box.createVerticalStrut(12));
			contentPanel.add(currentStepSection());
			contentPanel.add(Box.createVerticalStrut(12));
			contentPanel.add(upcomingSection());
		}

		contentPanel.add(Box.createVerticalStrut(14));
		contentPanel.add(buttonRow());

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

	private JPanel noGuideHelp()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(0, 3, 0, 0, ACCENT),
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 1, 1, ColorScheme.MEDIUM_GRAY_COLOR),
				BorderFactory.createEmptyBorder(10, 10, 10, 10))));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel header = new JLabel("No guide loaded");
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(ACCENT);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(header);

		panel.add(Box.createVerticalStrut(6));

		JLabel l = new JLabel("<html><body style='width: 120px'>"
			+ "Click <b>Import guide…</b> below and pick a guide <code>.json</code> file. "
			+ "Progress is saved per character."
			+ "</body></html>");
		l.setFont(FontManager.getRunescapeFont());
		l.setForeground(Color.WHITE);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(l);
		return panel;
	}

	private JPanel guideHeader(Guide guide)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel name = new JLabel("<html><body style='width: 150px'>" + escapeHtml(guide.getGuideName()) + "</body></html>");
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

		JProgressBar bar = new JProgressBar(0, Math.max(total, 1));
		bar.setValue(done);
		bar.setStringPainted(false);
		bar.setForeground(ACCENT);
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bar.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
		bar.setPreferredSize(new Dimension(0, 10));
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(bar);

		panel.add(Box.createVerticalStrut(6));

		int remaining = Math.max(total - done, 0);
		JLabel stats = new JLabel(String.format("%d done  •  %d skipped  •  %d left", completed, skipped, remaining));
		stats.setFont(FontManager.getRunescapeSmallFont());
		stats.setForeground(Color.WHITE);
		stats.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(stats);

		return panel;
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

		String questBtnLabel = null;
		Runnable questAction = null;
		if (questName != null)
		{
			questBtnLabel = questHelperInstalled.get() ? "Open in Quest Helper" : "View Quest Guide";
			final String finalQuestName = questName;
			questAction = () -> openQuestGuide.accept(finalQuestName);
		}

		StepCardPanel card = new StepCardPanel(current, tracker, questName, questBtnLabel, questAction);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		return card;
	}

	private JPanel upcomingSection()
	{
		List<StepEntry> upcoming = tracker.getUpcoming(5);
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

		for (StepEntry e : upcoming)
		{
			JPanel row = upcomingRow(e);
			panel.add(row);
			panel.add(Box.createVerticalStrut(4));
		}
		return panel;
	}

	private JPanel upcomingRow(StepEntry entry)
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

		String desc = entry.getStep().getDescription();
		JLabel d = new JLabel("<html><body style='width: 100px'>" + escapeHtml(desc) + "</body></html>");
		d.setFont(FontManager.getRunescapeSmallFont());
		d.setForeground(Color.WHITE);
		row.add(d, BorderLayout.CENTER);

		return row;
	}

	private JPanel buttonRow()
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton importBtn = styledButton("Import guide…", Color.WHITE);
		importBtn.addActionListener(e -> chooseGuideFile());
		row.add(importBtn);

		if (tracker.getGuide() != null)
		{
			row.add(Box.createVerticalStrut(4));
			JButton reset = styledButton("Reset progress", ColorScheme.LIGHT_GRAY_COLOR);
			reset.addActionListener(e -> {
				int choice = JOptionPane.showConfirmDialog(this,
					"Clear all completed and skipped steps for this guide?",
					"Reset progress", JOptionPane.OK_CANCEL_OPTION);
				if (choice == JOptionPane.OK_OPTION)
				{
					onResetRequested.run();
				}
			});
			row.add(reset);
		}

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
}
