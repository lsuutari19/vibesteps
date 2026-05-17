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
	private static final int ICON_SIZE = 32;

	private PanelIcons()
	{
	}

	public static BufferedImage navIcon(Class<?> anchor)
	{
		try
		{
			BufferedImage raw = ImageUtil.loadImageResource(anchor, "/com/gimprogresstracker/icon.png");
			if (raw != null)
			{
				// Scale to the standard nav-icon size regardless of source resolution.
				BufferedImage scaled = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = scaled.createGraphics();
				try
				{
					g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
						RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
					g.drawImage(raw, 0, 0, ICON_SIZE, ICON_SIZE, null);
				}
				finally
				{
					g.dispose();
				}
				return scaled;
			}
		}
		catch (Exception ignored)
		{
		}
		return generated();
	}

	private static BufferedImage generated()
	{
		BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			g.setColor(new Color(0, 122, 204));
			g.fillRoundRect(0, 0, ICON_SIZE - 1, ICON_SIZE - 1, 8, 8);

			g.setColor(Color.WHITE);
			Font font = new Font(Font.SANS_SERIF, Font.BOLD, 13);
			g.setFont(font);
			String text = "VS";
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
