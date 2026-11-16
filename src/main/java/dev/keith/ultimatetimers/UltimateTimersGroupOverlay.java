package dev.keith.ultimatetimers;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * One of these per ring "group". Fully custom-drawn (not a real InfoBox), but mirrors how
 * infoboxes can be arranged: drag to reposition (free, from the base Overlay class), drag one
 * group onto another to merge them (onDrag below), and right-click a specific ring within a
 * group to Detach it into its own new group - all without the size/background/tick-rate
 * limitations that come from actually registering as net.runelite.client.ui.overlay.infobox.InfoBox.
 */
public class UltimateTimersGroupOverlay extends Overlay
{
	private static final int ELEMENT_GAP = 2;
	private static final int TEXT_STYLE_OFFSET = 1;

	/** Fixed stand-in used only to reserve stable outside-left/outside-right margins - the
	 * actual displayed text (which varies digit-to-digit) is still drawn at its real width, so
	 * only the reserved layout space, not the text itself, ignores minute counts above 9. */
	private static final String TEXT_WIDTH_RESERVATION = "00:00";

	private static final class FlashSettings
	{
		final boolean enabled;
		final int thresholdSeconds;
		final Color color;
		final boolean foreground;
		final boolean background;
		final boolean border;
		final boolean text;

		FlashSettings(boolean enabled, int thresholdSeconds, Color color, boolean foreground, boolean background,
			boolean border, boolean text)
		{
			this.enabled = enabled;
			this.thresholdSeconds = thresholdSeconds;
			this.color = color;
			this.foreground = foreground;
			this.background = background;
			this.border = border;
			this.text = text;
		}
	}

	private static final class TextStyle
	{
		final Color color;
		final boolean borderEnabled;
		final Color borderColor;
		final boolean shadowEnabled;

		TextStyle(Color color, boolean borderEnabled, Color borderColor, boolean shadowEnabled)
		{
			this.color = color;
			this.borderEnabled = borderEnabled;
			this.borderColor = borderColor;
			this.shadowEnabled = shadowEnabled;
		}
	}

	private final UltimateTimersPlugin plugin;
	private final UltimateTimersConfig config;
	private final Client client;
	private final TooltipManager tooltipManager;
	private final String groupName;

	private TrackedBuff hoveredBuff;

	UltimateTimersGroupOverlay(UltimateTimersPlugin plugin, UltimateTimersConfig config, Client client,
		TooltipManager tooltipManager, String groupName)
	{
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		this.tooltipManager = tooltipManager;
		this.groupName = groupName;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setDragTargetable(true);
		// Resizing this (drag an edge, same as RuneLite's own infobox panel) sets a per-group
		// preferredSize that render() reads below to decide how many timers fit in a row/column
		// before wrapping - a per-group wrap limit instead of one global setting.
		setResizable(true);
		setMinimumSize(20);
	}

	String getGroupName()
	{
		return groupName;
	}

	@Override
	public String getName()
	{
		return "UltimateTimersGroup_" + groupName;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<ActiveBuffDisplay> displays = plugin.getActiveDisplaysForGroup(groupName);
		hoveredBuff = null;
		if (displays.isEmpty())
		{
			return null;
		}

		int width = config.ringWidth();
		int height = config.ringHeight();
		boolean showTimeText = config.showTimeText();
		ElementPosition textPosition = config.textPosition();
		boolean showDescription = config.showDescription();
		ElementPosition descriptionPosition = config.descriptionPosition();
		RingLayout layout = plugin.getGroupLayout(groupName);
		int cornerRadius = config.cornerRadius();
		FillDirection fillDirection = config.fillDirection();
		boolean showIcon = config.showIcon();
		ElementPosition iconPosition = config.iconPosition();
		int iconSize = config.iconSize();
		int ringBorderThickness = config.ringBorderThickness();
		int gap = config.groupPadding();
		boolean tooltipsEnabled = config.enableTooltips();

		boolean flashPhaseOn = isFlashPhaseOn();
		boolean forceFlashing = config.debugForceFlashing();
		Map<TrackedBuff.Kind, FlashSettings> flashByKind = new EnumMap<>(TrackedBuff.Kind.class);
		flashByKind.put(TrackedBuff.Kind.BUFF, new FlashSettings(config.enableFlash(), config.flashThresholdSeconds(),
			config.flashColor(), config.flashForeground(), config.flashBackground(), config.flashBorder(),
			config.flashText()));
		flashByKind.put(TrackedBuff.Kind.DEBUFF, new FlashSettings(config.enableDebuffFlash(),
			config.debuffFlashThresholdSeconds(), config.debuffFlashColor(), config.debuffFlashForeground(),
			config.debuffFlashBackground(), config.debuffFlashBorder(), config.debuffFlashText()));
		flashByKind.put(TrackedBuff.Kind.COOLDOWN, new FlashSettings(config.enableCooldownFlash(),
			config.cooldownFlashThresholdSeconds(), config.cooldownFlashColor(), config.cooldownFlashForeground(),
			config.cooldownFlashBackground(), config.cooldownFlashBorder(), config.cooldownFlashText()));
		flashByKind.put(TrackedBuff.Kind.OTHER_TIMER, new FlashSettings(config.enableOtherTimerFlash(),
			config.otherTimerFlashThresholdSeconds(), config.otherTimerFlashColor(),
			config.otherTimerFlashForeground(), config.otherTimerFlashBackground(), config.otherTimerFlashBorder(),
			config.otherTimerFlashText()));
		// Counters (e.g. the slayer task kill count) have no flash settings of their own at all -
		// permanently inert, and excluded from "Force flashing" below too.
		flashByKind.put(TrackedBuff.Kind.COUNTER,
			new FlashSettings(false, 0, Color.BLACK, false, false, false, false));

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// Item icons are native 36x32 sprites; without this hint, scaling them to the configured
		// icon size defaults to nearest-neighbor and looks noticeably blockier than the same
		// icon drawn 1:1 in the inventory or scaled by RuneLite's own infobox rendering.
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		Font font = config.textFont().getFont();
		graphics.setFont(font);
		FontMetrics fm = graphics.getFontMetrics(font);

		// How much extra space to reserve around the ring itself for elements positioned outside
		// it. Text's outside-left/outside-right reservation uses a fixed stand-in string so the
		// cell's overall size (and therefore the whole group's layout) doesn't jitter as the
		// digit count of the live countdown changes tick to tick.
		int textReserveWidth = fm.stringWidth(TEXT_WIDTH_RESERVATION);
		int textReserveHeight = fm.getHeight();

		// Unlike the countdown text, the description doesn't change tick to tick, but it does vary
		// buff to buff - reserve enough width for the longest label currently in this group so the
		// cell size (and the whole group's layout) stays uniform across its members.
		int descriptionReserveHeight = fm.getHeight();
		int descriptionReserveWidth = 0;
		if (showDescription)
		{
			for (ActiveBuffDisplay display : displays)
			{
				descriptionReserveWidth = Math.max(descriptionReserveWidth, fm.stringWidth(display.getBuff().getLabel()));
			}
		}

		int marginTop = Math.max(Math.max(
			showTimeText && textPosition == ElementPosition.ABOVE ? textReserveHeight + ELEMENT_GAP : 0,
			showIcon && iconPosition == ElementPosition.ABOVE ? iconSize + ELEMENT_GAP : 0),
			showDescription && descriptionPosition == ElementPosition.ABOVE ? descriptionReserveHeight + ELEMENT_GAP : 0);
		int marginBottom = Math.max(Math.max(
			showTimeText && textPosition == ElementPosition.BELOW ? textReserveHeight + ELEMENT_GAP : 0,
			showIcon && iconPosition == ElementPosition.BELOW ? iconSize + ELEMENT_GAP : 0),
			showDescription && descriptionPosition == ElementPosition.BELOW ? descriptionReserveHeight + ELEMENT_GAP : 0);
		int marginLeft = Math.max(Math.max(
			showTimeText && textPosition == ElementPosition.OUTSIDE_LEFT ? textReserveWidth + ELEMENT_GAP : 0,
			showIcon && iconPosition == ElementPosition.OUTSIDE_LEFT ? iconSize + ELEMENT_GAP : 0),
			showDescription && descriptionPosition == ElementPosition.OUTSIDE_LEFT ? descriptionReserveWidth + ELEMENT_GAP : 0);
		int marginRight = Math.max(Math.max(
			showTimeText && textPosition == ElementPosition.OUTSIDE_RIGHT ? textReserveWidth + ELEMENT_GAP : 0,
			showIcon && iconPosition == ElementPosition.OUTSIDE_RIGHT ? iconSize + ELEMENT_GAP : 0),
			showDescription && descriptionPosition == ElementPosition.OUTSIDE_RIGHT ? descriptionReserveWidth + ELEMENT_GAP : 0);

		int cellWidth = marginLeft + width + marginRight;
		int cellHeight = marginTop + height + marginBottom;

		Instant now = Instant.now();
		Rectangle overlayBounds = getBounds();
		Point mouse = new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY());

		// Items fill one row/column up to wrapAfter, then wrap into the next row/column - same
		// idea as how RuneLite's own infobox panel wraps instead of becoming one long stack.
		// wrapAfter comes from this group's own resized preferredSize (drag an edge to resize,
		// same interaction as the infobox panel) rather than a single global setting, so each
		// group remembers its own wrap width/height. Unresized groups (preferredSize still null)
		// behave exactly as before - one unbroken row/column.
		Dimension preferredSize = getPreferredSize();
		int wrapAfter;
		if (preferredSize == null)
		{
			wrapAfter = displays.size();
		}
		else if (layout == RingLayout.VERTICAL)
		{
			wrapAfter = Math.max(1, (preferredSize.height + gap) / (cellHeight + gap));
		}
		else
		{
			wrapAfter = Math.max(1, (preferredSize.width + gap) / (cellWidth + gap));
		}

		int itemsPerLine = Math.min(wrapAfter, displays.size());
		int numLines = (int) Math.ceil(displays.size() / (double) wrapAfter);

		for (int i = 0; i < displays.size(); i++)
		{
			ActiveBuffDisplay display = displays.get(i);
			int indexInLine = i % wrapAfter;
			int lineIndex = i / wrapAfter;
			int x = layout == RingLayout.VERTICAL ? lineIndex * (cellWidth + gap) : indexInLine * (cellWidth + gap);
			int y = layout == RingLayout.VERTICAL ? indexInLine * (cellHeight + gap) : lineIndex * (cellHeight + gap);

			long remainingMillis = display.getRemainingMillis(now);
			long peak = display.getPeakMillis();
			double fraction = peak <= 0 ? 0 : Math.min(1.0, (double) remainingMillis / peak);
			int seconds = (int) Math.round(remainingMillis / 1000.0);

			TrackedBuff.Kind kind = display.getBuff().getKind();
			FlashSettings flash = flashByKind.get(kind);
			boolean useFlash = kind != TrackedBuff.Kind.COUNTER && flashPhaseOn && flash.enabled
				&& (forceFlashing || seconds <= flash.thresholdSeconds);
			Color foreground = (useFlash && flash.foreground) ? flash.color : display.getColor();
			Color background = (useFlash && flash.background) ? flash.color : config.backgroundColor();
			Color ringBorder = (useFlash && flash.border) ? flash.color : config.borderColor();
			Color textColor = (useFlash && flash.text) ? flash.color : config.textColor();

			TextStyle textStyle = new TextStyle(textColor, config.textBorderEnabled(), config.textBorderColor(),
				config.textShadowEnabled());

			int ringX = x + marginLeft;
			int ringY = y + marginTop;

			drawRingShape(graphics, ringX, ringY, width, height, fraction, foreground, ringBorder, background,
				ringBorderThickness, cornerRadius, fillDirection);

			if (showIcon && display.getIcon() != null)
			{
				Point p = resolveElementPosition(ringX, ringY, width, height, iconSize, iconSize, iconPosition,
					ELEMENT_GAP);
				graphics.drawImage(display.getIcon(), p.x, p.y, iconSize, iconSize, null);
			}

			if (showTimeText)
			{
				String text = display.getBuff().isKillCounter()
					? String.valueOf(seconds)
					: formatSeconds(seconds);
				int textWidth = fm.stringWidth(text);
				int textHeight = fm.getHeight();
				Point p = resolveElementPosition(ringX, ringY, width, height, textWidth, textHeight, textPosition,
					ELEMENT_GAP);
				drawStyledText(graphics, text, p.x, p.y + fm.getAscent(), textStyle);
			}

			if (showDescription)
			{
				String description = display.getBuff().getLabel();
				int descriptionWidth = fm.stringWidth(description);
				int descriptionHeight = fm.getHeight();
				Point p = resolveElementPosition(ringX, ringY, width, height, descriptionWidth, descriptionHeight,
					descriptionPosition, ELEMENT_GAP);
				drawStyledText(graphics, description, p.x, p.y + fm.getAscent(), textStyle);
			}

			Rectangle ringRect = new Rectangle(overlayBounds.x + ringX, overlayBounds.y + ringY, width, height);
			if (ringRect.contains(mouse))
			{
				hoveredBuff = display.getBuff();
				if (tooltipsEnabled)
				{
					tooltipManager.add(new Tooltip(display.getBuff().isKillCounter()
						? slayerTaskTooltip(display.getBuff())
						: display.getBuff().getLabel()));
				}
			}
		}

		int totalWidth = layout == RingLayout.VERTICAL
			? numLines * cellWidth + (numLines - 1) * gap
			: itemsPerLine * cellWidth + (itemsPerLine - 1) * gap;
		int totalHeight = layout == RingLayout.VERTICAL
			? itemsPerLine * cellHeight + (itemsPerLine - 1) * gap
			: numLines * cellHeight + (numLines - 1) * gap;

		return new Dimension(totalWidth, totalHeight);
	}

	/**
	 * Mirrors the tooltip the core Slayer plugin's own infobox shows (name, points, streak,
	 * starting count), read directly from varps/varbits so it works whether or not that infobox
	 * is visible. Streak and starting-count logic (including the wilderness/Mortimer master
	 * special cases and the extension-task modifier) matches the core Slayer plugin exactly.
	 */
	private String slayerTaskTooltip(TrackedBuff buff)
	{
		String name = slayerTaskName(client.getVarpValue(VarPlayerID.SLAYER_TARGET));

		int points = client.getVarbitValue(VarbitID.SLAYER_POINTS);

		int master = client.getVarbitValue(VarbitID.SLAYER_MASTER);
		String masterName = slayerMasterName(master);

		int streak;
		if (master == 7)
		{
			streak = client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED);
		}
		else if (master == 10)
		{
			streak = client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED);
		}
		else
		{
			streak = client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
		}

		int start = client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
		if (client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID) == 2)
		{
			int modifierValue = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE);
			boolean negative = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_NEGATIVE) == 1;
			start += negative ? -modifierValue : modifierValue;
		}

		return String.format("%s</br>Pts: %d</br>Master: %s</br>Streak: %d</br>Start: %d",
			name != null ? name : buff.getLabel(), points, masterName, streak, start);
	}

	/**
	 * Reads the current task's real display name straight out of the game's own Slayer task
	 * table (DBTableID.SlayerTask), keyed by the row id VarPlayerID.SLAYER_TARGET holds - so the
	 * name is always correct and complete, with no hand-maintained task list to fall out of date.
	 * The table only stores case-folded search strings (COL_NAME_LOWERCASE), so the words are
	 * re-capitalised for display.
	 */
	private String slayerTaskName(int targetRow)
	{
		if (targetRow == 0)
		{
			return null;
		}
		Object[] nameField = client.getDBTableField(targetRow, DBTableID.SlayerTask.COL_NAME_LOWERCASE, 0);
		if (nameField == null || nameField.length == 0 || !(nameField[0] instanceof String))
		{
			return null;
		}
		return capitalizeWords((String) nameField[0]);
	}

	private static String capitalizeWords(String text)
	{
		StringBuilder result = new StringBuilder(text.length());
		boolean capitalizeNext = true;
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if (capitalizeNext && Character.isLetter(c))
			{
				result.append(Character.toUpperCase(c));
				capitalizeNext = false;
			}
			else
			{
				result.append(c);
			}
			if (c == ' ')
			{
				capitalizeNext = true;
			}
		}
		return result.toString();
	}

	/**
	 * VarbitID.SLAYER_MASTER values 1-10 identify which slayer master assigned the current task -
	 * values 7 (Krystilia) and 10 (Mortimer) are confirmed directly from the core Slayer plugin's
	 * own bytecode (it branches on them for the Wilderness/Mortimer streak varp special cases);
	 * the rest follow the same ordering corroborated by community-documented client data.
	 */
	private static String slayerMasterName(int master)
	{
		switch (master)
		{
			case 1: return "Turael";
			case 2: return "Mazchna";
			case 3: return "Vannaka";
			case 4: return "Chaeldar";
			case 5: return "Duradel";
			case 6: return "Nieve";
			case 7: return "Krystilia";
			case 8: return "Konar quo Maten";
			case 9: return "Spria";
			case 10: return "Mortimer";
			default: return "Unknown";
		}
	}

	@Override
	public List<OverlayMenuEntry> getMenuEntries()
	{
		List<OverlayMenuEntry> entries = super.getMenuEntries();
		entries.clear();
		entries.add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, "Flip", "Timer Group"));
		if (hoveredBuff != null)
		{
			List<TrackedBuff> members = plugin.getGroupMembersInOrder(groupName);
			int index = members.indexOf(hoveredBuff);
			if (index > 0)
			{
				entries.add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, "Move Up", hoveredBuff.getLabel()));
			}
			if (index >= 0 && index < members.size() - 1)
			{
				entries.add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, "Move Down", hoveredBuff.getLabel()));
			}
			entries.add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, "Detach", hoveredBuff.getLabel()));
		}
		return entries;
	}

	@Override
	public boolean onDrag(Overlay source)
	{
		if (!(source instanceof UltimateTimersGroupOverlay) || source == this)
		{
			return false;
		}

		plugin.mergeGroups(((UltimateTimersGroupOverlay) source).getGroupName(), groupName);
		return true;
	}

	private static boolean isFlashPhaseOn()
	{
		return (System.currentTimeMillis() / 500) % 2 == 0;
	}

	/**
	 * cornerRadiusPercent 0 gives a plain square; 100 gives arcs as big as the whole width/height,
	 * which degenerates into an ellipse (Java2D's standard trick for drawing a circle/pill via
	 * RoundRectangle2D); anything in between is a rounded square. 0 is special-cased to a true
	 * Rectangle2D rather than a zero-arc RoundRectangle2D - the latter's corners are still built
	 * from (degenerate) Bezier arcs, which anti-aliasing softens into a visibly missing corner
	 * pixel compared to a real square corner.
	 */
	private void drawRingShape(Graphics2D graphics, int x, int y, int width, int height, double fraction,
		Color foreground, Color border, Color background, int borderThickness, int cornerRadiusPercent,
		FillDirection fillDirection)
	{
		RectangularShape ring;
		if (cornerRadiusPercent <= 0)
		{
			ring = new Rectangle2D.Double(x, y, width, height);
		}
		else
		{
			double arcWidth = width * (cornerRadiusPercent / 100.0);
			double arcHeight = height * (cornerRadiusPercent / 100.0);
			ring = new RoundRectangle2D.Double(x, y, width, height, arcWidth, arcHeight);
		}

		graphics.setColor(background);
		graphics.fill(ring);

		Shape wedge = foregroundShape(x, y, width, height, fraction, fillDirection);
		Shape oldClip = graphics.getClip();
		graphics.clip(ring);
		graphics.setColor(foreground);
		graphics.fill(wedge);
		graphics.setClip(oldClip);

		if (borderThickness > 0)
		{
			graphics.setColor(border);
			graphics.setStroke(new BasicStroke(borderThickness));
			graphics.draw(ring);
		}
	}

	/**
	 * The shape of the remaining (filled) portion, clipped to the ring's circle/square by the
	 * caller. For the two rotational directions this is a pie wedge oversized so its straight
	 * edges always reach past the ring's bounds regardless of shape - its framing rectangle keeps
	 * the same width:height aspect ratio as the real ring so the wedge angles line up correctly
	 * after clipping, even when width and height differ. For the four linear directions it's a
	 * rectangle sized by fraction and anchored on the side opposite the direction it depletes
	 * toward (so the receding edge travels in the named direction).
	 */
	private Shape foregroundShape(int x, int y, int width, int height, double fraction, FillDirection direction)
	{
		switch (direction)
		{
			case CLOCKWISE:
			{
				double sweep = 360.0 * fraction;
				double cx = x + width / 2.0;
				double cy = y + height / 2.0;
				return new Arc2D.Double(cx - width, cy - height, 2 * width, 2 * height, 90, sweep, Arc2D.PIE);
			}
			case TOP_TO_BOTTOM:
			{
				double filledHeight = height * fraction;
				return new Rectangle2D.Double(x, y + height - filledHeight, width, filledHeight);
			}
			case BOTTOM_TO_TOP:
			{
				double filledHeight = height * fraction;
				return new Rectangle2D.Double(x, y, width, filledHeight);
			}
			case LEFT_TO_RIGHT:
			{
				double filledWidth = width * fraction;
				return new Rectangle2D.Double(x + width - filledWidth, y, filledWidth, height);
			}
			case RIGHT_TO_LEFT:
			{
				double filledWidth = width * fraction;
				return new Rectangle2D.Double(x, y, filledWidth, height);
			}
			case COUNTERCLOCKWISE:
			default:
			{
				double sweep = 360.0 * fraction;
				double cx = x + width / 2.0;
				double cy = y + height / 2.0;
				return new Arc2D.Double(cx - width, cy - height, 2 * width, 2 * height, 90, -sweep, Arc2D.PIE);
			}
		}
	}

	/**
	 * Top-left coordinate for an element (icon or text) of size elemW x elemH, placed relative
	 * to a ring at ringX,ringY sized ringW x ringH. The nine "inside" positions sit flush against
	 * an edge/corner of the ring (or centered within it); the four "outside" positions sit beyond
	 * the ring's edge by gap pixels, centered along that edge.
	 */
	private Point resolveElementPosition(int ringX, int ringY, int ringW, int ringH, int elemW, int elemH,
		ElementPosition position, int gap)
	{
		switch (position)
		{
			case TOP:
				return new Point(ringX + (ringW - elemW) / 2, ringY);
			case BOTTOM:
				return new Point(ringX + (ringW - elemW) / 2, ringY + ringH - elemH);
			case LEFT:
				return new Point(ringX, ringY + (ringH - elemH) / 2);
			case RIGHT:
				return new Point(ringX + ringW - elemW, ringY + (ringH - elemH) / 2);
			case TOP_LEFT:
				return new Point(ringX, ringY);
			case TOP_RIGHT:
				return new Point(ringX + ringW - elemW, ringY);
			case BOTTOM_LEFT:
				return new Point(ringX, ringY + ringH - elemH);
			case BOTTOM_RIGHT:
				return new Point(ringX + ringW - elemW, ringY + ringH - elemH);
			case ABOVE:
				return new Point(ringX + (ringW - elemW) / 2, ringY - gap - elemH);
			case BELOW:
				return new Point(ringX + (ringW - elemW) / 2, ringY + ringH + gap);
			case OUTSIDE_LEFT:
				return new Point(ringX - gap - elemW, ringY + (ringH - elemH) / 2);
			case OUTSIDE_RIGHT:
				return new Point(ringX + ringW + gap, ringY + (ringH - elemH) / 2);
			case CENTER:
			default:
				return new Point(ringX + (ringW - elemW) / 2, ringY + (ringH - elemH) / 2);
		}
	}

	private void drawStyledText(Graphics2D graphics, String text, int x, int y, TextStyle style)
	{
		int t = TEXT_STYLE_OFFSET;

		if (style.shadowEnabled)
		{
			graphics.setColor(style.borderColor);
			graphics.drawString(text, x + t, y + t);
		}

		if (style.borderEnabled)
		{
			graphics.setColor(style.borderColor);
			graphics.drawString(text, x - t, y - t);
			graphics.drawString(text, x, y - t);
			graphics.drawString(text, x + t, y - t);
			graphics.drawString(text, x - t, y);
			graphics.drawString(text, x + t, y);
			graphics.drawString(text, x - t, y + t);
			graphics.drawString(text, x, y + t);
			graphics.drawString(text, x + t, y + t);
		}

		graphics.setColor(style.color);
		graphics.drawString(text, x, y);
	}

	private static String formatSeconds(int totalSeconds)
	{
		int minutes = totalSeconds / 60;
		int seconds = totalSeconds % 60;
		return minutes + ":" + String.format("%02d", seconds);
	}
}
