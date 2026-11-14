package dev.keith.buffrings;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class BuffRingsOverlay extends Overlay
{
	private static final int GAP = 6;

	private final BuffRingsPlugin plugin;
	private final BuffRingsConfig config;

	@Inject
	private BuffRingsOverlay(BuffRingsPlugin plugin, BuffRingsConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<ActiveBuffDisplay> displays = plugin.getActiveDisplays();
		if (displays.isEmpty())
		{
			return null;
		}

		int diameter = config.ringDiameter();
		boolean showText = config.showText();

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int y = 0;
		for (ActiveBuffDisplay display : displays)
		{
			drawRing(graphics, 0, y, diameter, display, showText);
			y += diameter + GAP;
		}

		return new Dimension(diameter, y - GAP);
	}

	private void drawRing(Graphics2D graphics, int x, int y, int diameter, ActiveBuffDisplay display, boolean showText)
	{
		Color buffColor = display.getBuff().getColor();

		graphics.setColor(new Color(0, 0, 0, 160));
		graphics.fill(new Ellipse2D.Double(x, y, diameter, diameter));

		double sweep = 360.0 * display.getFraction();
		Arc2D arc = new Arc2D.Double(x, y, diameter, diameter, 90, -sweep, Arc2D.PIE);
		graphics.setColor(buffColor);
		graphics.fill(arc);

		graphics.setColor(Color.WHITE);
		graphics.setStroke(new BasicStroke(1.5f));
		graphics.draw(new Ellipse2D.Double(x, y, diameter, diameter));

		if (showText)
		{
			String text = formatSeconds(display.getSecondsRemaining());
			graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, diameter / 3)));
			FontMetrics fm = graphics.getFontMetrics();
			int textWidth = fm.stringWidth(text);
			int textX = x + (diameter - textWidth) / 2;
			int textY = y + (diameter + fm.getAscent()) / 2 - 2;

			graphics.setColor(Color.BLACK);
			graphics.drawString(text, textX + 1, textY + 1);
			graphics.setColor(Color.WHITE);
			graphics.drawString(text, textX, textY);
		}
	}

	private static String formatSeconds(int totalSeconds)
	{
		int minutes = totalSeconds / 60;
		int seconds = totalSeconds % 60;
		if (minutes > 0)
		{
			return minutes + ":" + String.format("%02d", seconds);
		}
		return Integer.toString(seconds);
	}
}
