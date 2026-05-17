package com.gimprogresstracker.ui;

import com.gimprogresstracker.ProgressTracker;
import com.gimprogresstracker.model.Guide;
import com.gimprogresstracker.model.StepEntry;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
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
import javax.swing.filechooser.FileNameExtensionFilter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class GIMProgressTrackerPanel extends PluginPanel
{
	private final ProgressTracker tracker;
	private final Consumer<Path> onGuideFileChosen;
	private final Runnable onResetRequested;

	private final JPanel contentPanel = new JPanel();

	public GIMProgressTrackerPanel(ProgressTracker tracker, Consumer<Path> onGuideFileChosen, Runnable onResetRequested)
	{
		super(false);
		this.tracker = tracker;
		this.onGuideFileChosen = onGuideFileChosen;
		this.onResetRequested = onResetRequested;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

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

		JLabel title = new JLabel("GIM Progress Tracker");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		contentPanel.add(title);

		contentPanel.add(Box.createVerticalStrut(8));

		Guide guide = tracker.getGuide();
		if (guide == null)
		{
			contentPanel.add(noGuideHelp());
		}
		else
		{
			contentPanel.add(guideHeader(guide));
			contentPanel.add(Box.createVerticalStrut(8));
			contentPanel.add(progressSection());
			contentPanel.add(Box.createVerticalStrut(8));
			contentPanel.add(currentStepSection());
			contentPanel.add(Box.createVerticalStrut(8));
			contentPanel.add(upcomingSection());
		}

		contentPanel.add(Box.createVerticalStrut(10));
		contentPanel.add(buttonRow());

		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private JPanel noGuideHelp()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel l = new JLabel("<html><body style='width: 180px'>"
			+ "No guide loaded.<br><br>"
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

		JLabel name = new JLabel(guide.getGuideName());
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(ColorScheme.BRAND_ORANGE);
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
				meta.append(" • ");
			}
			meta.append("v").append(guide.getVersion());
		}
		if (meta.length() > 0)
		{
			JLabel m = new JLabel(meta.toString());
			m.setFont(FontManager.getRunescapeSmallFont());
			m.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			m.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.add(m);
		}
		return panel;
	}

	private JPanel progressSection()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		int total = tracker.getTotalCount();
		int done = tracker.getCompletedCount() + tracker.getSkippedCount();
		int pct = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);

		JLabel header = new JLabel(String.format("Progress: %d / %d  (%d%%)", done, total, pct));
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(Color.WHITE);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(header);

		JProgressBar bar = new JProgressBar(0, Math.max(total, 1));
		bar.setValue(done);
		bar.setStringPainted(false);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(bar);

		return panel;
	}

	private JPanel currentStepSection()
	{
		Optional<StepEntry> currentOpt = tracker.getCurrentStep();
		if (!currentOpt.isPresent())
		{
			JPanel done = new JPanel(new BorderLayout());
			done.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			done.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
				BorderFactory.createEmptyBorder(12, 12, 12, 12)));
			done.setAlignmentX(Component.LEFT_ALIGNMENT);

			JLabel l = new JLabel("Guide complete!");
			l.setFont(FontManager.getRunescapeBoldFont());
			l.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
			done.add(l, BorderLayout.CENTER);
			return done;
		}
		StepCardPanel card = new StepCardPanel(currentOpt.get(), tracker);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
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

		JLabel header = new JLabel("Upcoming");
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(header);
		panel.add(Box.createVerticalStrut(4));

		for (StepEntry e : upcoming)
		{
			panel.add(upcomingRow(e));
			panel.add(Box.createVerticalStrut(3));
		}
		return panel;
	}

	private JPanel upcomingRow(StepEntry entry)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		JLabel id = new JLabel("#" + entry.getStep().getId());
		id.setFont(FontManager.getRunescapeSmallFont());
		id.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(id, BorderLayout.WEST);

		String desc = entry.getStep().getDescription();
		if (desc.length() > 60)
		{
			desc = desc.substring(0, 57) + "...";
		}
		JLabel d = new JLabel(desc);
		d.setFont(FontManager.getRunescapeSmallFont());
		d.setForeground(Color.WHITE);
		row.add(d, BorderLayout.CENTER);

		return row;
	}

	private JPanel buttonRow()
	{
		JPanel row = new JPanel(new GridLayout(0, 1, 0, 4));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton importBtn = new JButton("Import guide…");
		importBtn.setFocusPainted(false);
		importBtn.addActionListener(e -> chooseGuideFile());
		row.add(importBtn);

		if (tracker.getGuide() != null)
		{
			JButton reset = new JButton("Reset progress");
			reset.setFocusPainted(false);
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
}
