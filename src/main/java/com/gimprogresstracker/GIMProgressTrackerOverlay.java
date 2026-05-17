package com.gimprogresstracker;

import com.gimprogresstracker.model.Location;
import com.gimprogresstracker.model.StepEntry;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.util.Optional;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws a tile highlight on the destination of the current step when it is in the loaded scene.
 */
public class GIMProgressTrackerOverlay extends Overlay
{
	private static final Stroke OUTLINE_STROKE = new BasicStroke(2f);

	private final Client client;
	private final ProgressTracker tracker;
	private final GIMProgressTrackerConfig config;

	@Inject
	GIMProgressTrackerOverlay(Client client, ProgressTracker tracker, GIMProgressTrackerConfig config)
	{
		this.client = client;
		this.tracker = tracker;
		this.config = config;
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Optional<StepEntry> currentOpt = tracker.getCurrentStep();
		if (!currentOpt.isPresent())
		{
			return null;
		}
		Location loc = currentOpt.get().getStep().getLocation();
		if (loc == null)
		{
			return null;
		}

		WorldPoint target = new WorldPoint(loc.getX(), loc.getY(), loc.getPlane());
		if (target.getPlane() != client.getTopLevelWorldView().getPlane())
		{
			return null;
		}

		LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), target);
		if (lp == null)
		{
			return null;
		}

		Polygon poly = Perspective.getCanvasTilePoly(client, lp);
		if (poly == null)
		{
			return null;
		}

		Color highlight = config.highlightColor();
		Color fill = new Color(highlight.getRed(), highlight.getGreen(), highlight.getBlue(), 50);

		Stroke prev = graphics.getStroke();
		graphics.setStroke(OUTLINE_STROKE);
		graphics.setColor(highlight);
		graphics.draw(poly);
		graphics.setColor(fill);
		graphics.fill(poly);
		graphics.setStroke(prev);

		return null;
	}
}
