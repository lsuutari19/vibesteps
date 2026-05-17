package com.gimprogresstracker.ui;

import com.gimprogresstracker.ProgressTracker;
import com.gimprogresstracker.model.Location;
import com.gimprogresstracker.model.RequiredItem;
import com.gimprogresstracker.model.Step;
import com.gimprogresstracker.model.StepEntry;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * Card showing the currently-active step: description, required items, location, action buttons.
 */
class StepCardPanel extends JPanel
{
	private static final Color CARD_BG = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color ACCENT = ColorScheme.BRAND_ORANGE;
	private static final Color PRIMARY_BTN_BG = new Color(60, 42, 28);
	private static final Color PRIMARY_BTN_HOVER = new Color(86, 60, 38);
	private static final Color SECONDARY_BTN_BG = ColorScheme.DARK_GRAY_COLOR;
	private static final Color SECONDARY_BTN_HOVER = ColorScheme.DARK_GRAY_HOVER_COLOR;

	StepCardPanel(StepEntry entry, ProgressTracker tracker)
	{
		Step step = entry.getStep();
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(CARD_BG);
		setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(0, 3, 0, 0, ACCENT),
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 1, 1, ColorScheme.MEDIUM_GRAY_COLOR),
				BorderFactory.createEmptyBorder(10, 10, 10, 10))));

		JLabel breadcrumb = new JLabel(htmlWrap(entry.getChapter().getName() + "  •  " + entry.getSection().getName()));
		breadcrumb.setFont(FontManager.getRunescapeSmallFont());
		breadcrumb.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		breadcrumb.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(breadcrumb);

		add(Box.createVerticalStrut(6));

		JLabel title = new JLabel("Step " + step.getId());
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ACCENT);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(title);

		add(Box.createVerticalStrut(2));
		add(thinDivider());
		add(Box.createVerticalStrut(8));

		JLabel description = new JLabel(htmlWrap(step.getDescription()));
		description.setFont(FontManager.getRunescapeFont());
		description.setForeground(Color.WHITE);
		description.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(description);

		if (!step.getRequiredItems().isEmpty())
		{
			add(Box.createVerticalStrut(12));
			add(sectionHeader("REQUIRED ITEMS"));
			add(Box.createVerticalStrut(3));
			for (RequiredItem item : step.getRequiredItems())
			{
				JLabel line = new JLabel(htmlWrap("•  item " + item.getItemId() + "  ×  " + item.getQuantity()));
				line.setFont(FontManager.getRunescapeSmallFont());
				line.setForeground(Color.WHITE);
				line.setAlignmentX(Component.LEFT_ALIGNMENT);
				add(line);
			}
		}

		Location loc = step.getLocation();
		if (loc != null)
		{
			add(Box.createVerticalStrut(10));
			add(sectionHeader("LOCATION"));
			add(Box.createVerticalStrut(3));
			JLabel locLabel = new JLabel(String.format("(%d, %d, %d)", loc.getX(), loc.getY(), loc.getPlane()));
			locLabel.setFont(FontManager.getRunescapeSmallFont());
			locLabel.setForeground(Color.WHITE);
			locLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(locLabel);
		}

		add(Box.createVerticalStrut(12));

		JButton complete = styledButton("Mark as Completed", ACCENT, PRIMARY_BTN_BG, PRIMARY_BTN_HOVER, ACCENT.darker());
		complete.addActionListener(e -> tracker.markCompleted(step.getId()));
		add(complete);

		add(Box.createVerticalStrut(4));

		JButton skip = styledButton("Skip", ColorScheme.LIGHT_GRAY_COLOR, SECONDARY_BTN_BG, SECONDARY_BTN_HOVER, ColorScheme.MEDIUM_GRAY_COLOR);
		skip.addActionListener(e -> tracker.markSkipped(step.getId()));
		add(skip);

		String qh = step.getQuestHelperLink();
		if (qh != null && !qh.isEmpty())
		{
			add(Box.createVerticalStrut(4));
			JButton qhButton = styledButton("Open Quest Helper link", ColorScheme.LIGHT_GRAY_COLOR, SECONDARY_BTN_BG, SECONDARY_BTN_HOVER, ColorScheme.MEDIUM_GRAY_COLOR);
			qhButton.addActionListener(e -> LinkBrowser.browse(qh));
			add(qhButton);
		}
	}

	private static JLabel sectionHeader(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ACCENT);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JPanel thinDivider()
	{
		JPanel d = new JPanel();
		d.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		d.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		d.setPreferredSize(new Dimension(1, 1));
		d.setAlignmentX(Component.LEFT_ALIGNMENT);
		return d;
	}

	private static JButton styledButton(String text, Color textColor, Color base, Color hover, Color borderColor)
	{
		JButton b = new JButton(text);
		b.setFocusPainted(false);
		b.setRolloverEnabled(false);
		b.setForeground(textColor);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setBackground(base);
		b.setOpaque(true);
		b.setContentAreaFilled(true);
		b.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(borderColor),
			BorderFactory.createEmptyBorder(6, 10, 6, 10)));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		b.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				b.setBackground(hover);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				b.setBackground(base);
			}
		});
		return b;
	}

	private static String htmlWrap(String text)
	{
		String escaped = text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");
		return "<html><body style='width: 195px'>" + escaped + "</body></html>";
	}
}
