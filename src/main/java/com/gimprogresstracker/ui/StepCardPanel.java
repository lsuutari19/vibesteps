package com.gimprogresstracker.ui;

import com.gimprogresstracker.ProgressTracker;
import com.gimprogresstracker.model.Location;
import com.gimprogresstracker.model.RequiredItem;
import com.gimprogresstracker.model.Step;
import com.gimprogresstracker.model.StepEntry;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * Card showing the currently-active step: description, required items, location, action buttons.
 */
class StepCardPanel extends JPanel
{
	StepCardPanel(StepEntry entry, ProgressTracker tracker)
	{
		Step step = entry.getStep();
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)));

		JLabel breadcrumb = new JLabel(entry.getChapter().getName() + " • " + entry.getSection().getName());
		breadcrumb.setFont(FontManager.getRunescapeSmallFont());
		breadcrumb.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		breadcrumb.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(breadcrumb);

		add(Box.createVerticalStrut(4));

		JLabel title = new JLabel("Step " + step.getId());
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(title);

		add(Box.createVerticalStrut(4));

		JLabel description = new JLabel(htmlWrap(step.getDescription()));
		description.setFont(FontManager.getRunescapeFont());
		description.setForeground(Color.WHITE);
		description.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(description);

		if (!step.getRequiredItems().isEmpty())
		{
			add(Box.createVerticalStrut(8));
			JLabel itemsHeader = new JLabel("Required items:");
			itemsHeader.setFont(FontManager.getRunescapeSmallFont());
			itemsHeader.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			itemsHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(itemsHeader);
			for (RequiredItem item : step.getRequiredItems())
			{
				JLabel line = new JLabel("• item " + item.getItemId() + " × " + item.getQuantity());
				line.setFont(FontManager.getRunescapeSmallFont());
				line.setForeground(Color.WHITE);
				line.setAlignmentX(Component.LEFT_ALIGNMENT);
				add(line);
			}
		}

		Location loc = step.getLocation();
		if (loc != null)
		{
			add(Box.createVerticalStrut(6));
			JLabel locLabel = new JLabel(String.format("Location: (%d, %d, %d)", loc.getX(), loc.getY(), loc.getPlane()));
			locLabel.setFont(FontManager.getRunescapeSmallFont());
			locLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			locLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(locLabel);
		}

		add(Box.createVerticalStrut(10));

		JPanel buttonRow = new JPanel(new GridLayout(1, 2, 4, 0));
		buttonRow.setOpaque(false);
		buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		JButton complete = new JButton("Mark complete");
		complete.setFocusPainted(false);
		complete.addActionListener(e -> tracker.markCompleted(step.getId()));
		buttonRow.add(complete);

		JButton skip = new JButton("Skip");
		skip.setFocusPainted(false);
		skip.addActionListener(e -> tracker.markSkipped(step.getId()));
		buttonRow.add(skip);

		add(buttonRow);

		String qh = step.getQuestHelperLink();
		if (qh != null && !qh.isEmpty())
		{
			add(Box.createVerticalStrut(6));
			JButton qhButton = new JButton("Open Quest Helper link");
			qhButton.setFocusPainted(false);
			qhButton.setAlignmentX(Component.LEFT_ALIGNMENT);
			qhButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
			qhButton.addActionListener(e -> LinkBrowser.browse(qh));
			add(qhButton);
		}
	}

	private static String htmlWrap(String text)
	{
		String escaped = text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");
		return "<html><body style='width: 180px'>" + escaped + "</body></html>";
	}
}
