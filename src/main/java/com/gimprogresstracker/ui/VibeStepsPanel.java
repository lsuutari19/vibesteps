package com.gimprogresstracker.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class VibeStepsPanel extends PluginPanel
{
	private static final String CARD_GUIDE = "guide";
	private static final String CARD_TEAMMATES = "teammates";
	private static final Color TAB_ACCENT = ColorScheme.BRAND_ORANGE;

	private final CardLayout cardLayout = new CardLayout();
	private final JPanel cards;
	private JPanel guideTab;
	private JPanel teammatesTab;
	private String activeCard = CARD_GUIDE;

	public VibeStepsPanel(GIMProgressTrackerPanel guidePanel, TeammatesPanel teammatesPanel)
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildTabBar(), BorderLayout.NORTH);

		cards = new JPanel(cardLayout);
		cards.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane guideScroll = new JScrollPane(guidePanel);
		guideScroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		guideScroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		guideScroll.setBorder(null);
		guideScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		cards.add(guideScroll, CARD_GUIDE);

		cards.add(teammatesPanel, CARD_TEAMMATES);

		add(cards, BorderLayout.CENTER);

		showCard(CARD_GUIDE);
	}

	private JPanel buildTabBar()
	{
		JPanel bar = new JPanel();
		bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
		bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR));

		guideTab = buildTab("Guide", CARD_GUIDE);
		teammatesTab = buildTab("Teammates", CARD_TEAMMATES);

		bar.add(guideTab);
		bar.add(teammatesTab);
		bar.add(Box.createHorizontalGlue());

		return bar;
	}

	private JPanel buildTab(String text, String card)
	{
		JPanel tab = new JPanel(new BorderLayout());
		tab.setOpaque(false);
		tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		tab.setBorder(BorderFactory.createEmptyBorder(8, 14, 0, 14));

		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		tab.add(label, BorderLayout.CENTER);

		JPanel accent = new JPanel();
		accent.setBackground(TAB_ACCENT);
		accent.setPreferredSize(new Dimension(0, 2));
		accent.setVisible(false);
		tab.add(accent, BorderLayout.SOUTH);

		tab.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				showCard(card);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (!activeCard.equals(card))
				{
					label.setForeground(Color.WHITE);
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (!activeCard.equals(card))
				{
					label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				}
			}
		});

		tab.putClientProperty("label", label);
		tab.putClientProperty("accent", accent);
		tab.putClientProperty("card", card);

		return tab;
	}

	public void showGuideTab()
	{
		showCard(CARD_GUIDE);
	}

	public void showTeammatesTab()
	{
		showCard(CARD_TEAMMATES);
	}

	private void showCard(String card)
	{
		activeCard = card;
		cardLayout.show(cards, card);
		updateTabStates();
	}

	private void updateTabStates()
	{
		for (JPanel tab : new JPanel[]{guideTab, teammatesTab})
		{
			String tabCard = (String) tab.getClientProperty("card");
			JLabel label = (JLabel) tab.getClientProperty("label");
			JPanel accent = (JPanel) tab.getClientProperty("accent");
			boolean active = activeCard.equals(tabCard);
			label.setForeground(active ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
			accent.setVisible(active);
		}
	}
}
