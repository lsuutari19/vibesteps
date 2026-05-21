package com.gimprogresstracker.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import net.runelite.client.util.ImageUtil;

public final class PanelIcons
{
	private static final int ICON_SIZE = 64;

	private PanelIcons()
	{
	}

	public static BufferedImage navIcon(Class<?> anchor)
	{
		return loadOrGenerate(anchor, "/icon.png", "VS", new Color(0, 122, 204));
	}

	public static BufferedImage teammatesIcon()
	{
		return loadOrGenerate(PanelIcons.class, "/group-member.png",
			"GRP", new Color(30, 80, 160));
	}

	private static BufferedImage loadOrGenerate(Class<?> anchor, String resourcePath, String fallbackText, Color fallbackColor)
	{
		try
		{
			BufferedImage raw = ImageUtil.loadImageResource(anchor, resourcePath);
			if (raw != null)
			{
				return scale(raw, ICON_SIZE);
			}
		}
		catch (Exception ignored)
		{
		}
		return generated(fallbackText, fallbackColor);
	}

	private static BufferedImage scale(BufferedImage src, int size)
	{
		BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scaled.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			g.drawImage(src, 0, 0, size, size, null);
		}
		finally
		{
			g.dispose();
		}
		return scaled;
	}

	private static BufferedImage generated(String text, Color background)
	{
		BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			g.setColor(background);
			g.fillRoundRect(0, 0, ICON_SIZE - 1, ICON_SIZE - 1, 12, 12);

			g.setColor(Color.WHITE);
			Font font = new Font(Font.SANS_SERIF, Font.BOLD, 24);
			g.setFont(font);
			FontMetrics fm = g.getFontMetrics();
			int tx = (ICON_SIZE - fm.stringWidth(text)) / 2;
			int ty = (ICON_SIZE - fm.getHeight()) / 2 + fm.getAscent();
			g.drawString(text, tx, ty);
		}
		finally
		{
			g.dispose();
		}
		return img;
	}
}
