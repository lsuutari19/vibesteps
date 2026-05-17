package com.gimprogresstracker.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Generates the navigation icon programmatically so the plugin needs no PNG resources.
 */
public final class PanelIcons
{
	private PanelIcons()
	{
	}

	public static BufferedImage navIcon()
	{
		int size = 32;
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			g.setColor(new Color(0, 122, 204));
			g.fillRoundRect(0, 0, size - 1, size - 1, 8, 8);

			g.setColor(Color.WHITE);
			Font font = new Font(Font.SANS_SERIF, Font.BOLD, 13);
			g.setFont(font);
			String text = "VS";
			FontMetrics fm = g.getFontMetrics();
			int tx = (size - fm.stringWidth(text)) / 2;
			int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
			g.drawString(text, tx, ty);
		}
		finally
		{
			g.dispose();
		}
		return img;
	}
}
