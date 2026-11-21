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
import java.util.HashMap;
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
	private final Client client;
	private final TooltipManager tooltipManager;
	private final String groupName;

	private TrackedBuff hoveredBuff;

	UltimateTimersGroupOverlay(UltimateTimersPlugin plugin, Client client, TooltipManager tooltipManager,
		String groupName)
	{
		this.plugin = plugin;
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

		RingLayout layout = plugin.getGroupLayout(groupName);
		boolean flashPhaseOn = isFlashPhaseOn();
		// Each category's flash settings now live only in the side panel's settings dialog
		// (UltimateTimersPlugin's generic per-category accessors), not individual config methods.
		Map<TrackedBuff.TimerCategory, FlashSettings> flashByCategory =
			new HashMap<>();
		for (TrackedBuff.TimerCategory timerCategory : plugin.getCategories())
		{
			flashByCategory.put(timerCategory, new FlashSettings(plugin.isFlashEnabled(timerCategory),
				plugin.getFlashThresholdSeconds(timerCategory), plugin.getFlashColor(timerCategory),
				plugin.isFlashForeground(timerCategory), plugin.isFlashBackground(timerCategory),
				plugin.isFlashBorder(timerCategory), plugin.isFlashText(timerCategory)));
		}
		// Percentage-Based Color categories only: their full/half/empty colors, read once per frame.
		Map<TrackedBuff.TimerCategory, Color[]> percentColorsByCategory =
			new HashMap<>();
		for (TrackedBuff.TimerCategory timerCategory : plugin.getCategories())
		{
			if (plugin.getColorStyle(timerCategory) == ColorStyle.PERCENTAGE)
			{
				percentColorsByCategory.put(timerCategory, new Color[]{plugin.getPercentColorFull(timerCategory),
					plugin.getPercentColorHalf(timerCategory), plugin.getPercentColorEmpty(timerCategory)});
			}
		}

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// Item icons are native 36x32 sprites; without this hint, scaling them to the configured
		// icon size defaults to nearest-neighbor and looks noticeably blockier than the same
		// icon drawn 1:1 in the inventory or scaled by RuneLite's own infobox rendering.
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		Instant now = Instant.now();
		Rectangle overlayBounds = getBounds();
		Point mouse = new Point(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY());

		// Size/shape/text/icon settings are per-category now, so timers in the same group can be
		// different sizes/shapes - a true flow layout (advance a cursor by each item's own cell
		// size, wrap when it would overflow the group's resized bounds) rather than a uniform
		// grid. Unresized groups (preferredSize still null) behave as before - one unbroken
		// row/column, since there's no bound to wrap against.
		Dimension preferredSize = getPreferredSize();
		int availableWidth = preferredSize != null ? preferredSize.width : Integer.MAX_VALUE;
		int availableHeight = preferredSize != null ? preferredSize.height : Integer.MAX_VALUE;

		int cursorX = 0;
		int cursorY = 0;
		int lineExtent = 0;
		int totalWidth = 0;
		int totalHeight = 0;

		for (ActiveBuffDisplay display : displays)
		{
			TrackedBuff buff = display.getBuff();
			TrackedBuff.TimerCategory category = plugin.getTimerCategory(buff);
			if (!flashByCategory.containsKey(category))
			{
				// Moved to Unmapped (or its category deleted) since this frame's displays were built.
				continue;
			}

			int width = plugin.getRingWidth(category);
			int height = plugin.getRingHeight(category);
			int cornerRadius = plugin.getCornerRadius(category);
			int ringBorderThickness = plugin.getRingBorderThickness(category);
			FillDirection fillDirection = plugin.getFillDirection(category);
			int gap = plugin.getGroupPadding(category);
			boolean showTimeText = plugin.isShowTimeText(category);
			boolean showDescription = plugin.isShowDescription(category);
			boolean showIcon = plugin.isShowIcon(category);
			boolean tooltipsEnabled = plugin.isTooltipsEnabled(category);
			ElementPosition textPosition = plugin.getTextPosition(category);
			ElementPosition descriptionPosition = plugin.getDescriptionPosition(category);
			ElementPosition iconPosition = plugin.getIconPosition(category);
			int iconSize = plugin.getIconSize(category);
			int textOffset = plugin.getTextOffset(category);
			int descriptionOffset = plugin.getDescriptionOffset(category);
			int iconOffset = plugin.getIconOffset(category);

			Font font = plugin.getTextFont(category).getFont();
			graphics.setFont(font);
			FontMetrics fm = graphics.getFontMetrics(font);

			// Text's outside-left/outside-right reservation uses a fixed stand-in string so this
			// item's cell size doesn't jitter as the digit count of its live countdown changes
			// tick to tick.
			int textReserveWidth = fm.stringWidth(TEXT_WIDTH_RESERVATION);
			int textReserveHeight = fm.getHeight();
			String description = buff.getLabel();
			int descriptionWidth = fm.stringWidth(description);
			int descriptionHeight = fm.getHeight();

			int marginTop = Math.max(Math.max(
				showTimeText && textPosition == ElementPosition.ABOVE ? textReserveHeight + ELEMENT_GAP : 0,
				showIcon && iconPosition == ElementPosition.ABOVE ? iconSize + ELEMENT_GAP : 0),
				showDescription && descriptionPosition == ElementPosition.ABOVE ? descriptionHeight + ELEMENT_GAP : 0);
			int marginBottom = Math.max(Math.max(
				showTimeText && textPosition == ElementPosition.BELOW ? textReserveHeight + ELEMENT_GAP : 0,
				showIcon && iconPosition == ElementPosition.BELOW ? iconSize + ELEMENT_GAP : 0),
				showDescription && descriptionPosition == ElementPosition.BELOW ? descriptionHeight + ELEMENT_GAP : 0);
			int marginLeft = Math.max(Math.max(
				showTimeText && textPosition == ElementPosition.OUTSIDE_LEFT ? textReserveWidth + ELEMENT_GAP : 0,
				showIcon && iconPosition == ElementPosition.OUTSIDE_LEFT ? iconSize + ELEMENT_GAP : 0),
				showDescription && descriptionPosition == ElementPosition.OUTSIDE_LEFT ? descriptionWidth + ELEMENT_GAP : 0);
			int marginRight = Math.max(Math.max(
				showTimeText && textPosition == ElementPosition.OUTSIDE_RIGHT ? textReserveWidth + ELEMENT_GAP : 0,
				showIcon && iconPosition == ElementPosition.OUTSIDE_RIGHT ? iconSize + ELEMENT_GAP : 0),
				showDescription && descriptionPosition == ElementPosition.OUTSIDE_RIGHT ? descriptionWidth + ELEMENT_GAP : 0);

			// An offset text or icon can stick out past the ring (or further out than an outside position's
			// usual gap), so reserve room for it rather than letting it overlap the next timer.
			// Texts without an offset keep the original rules above, so existing layouts don't move.
			int[] margins = {marginTop, marginBottom, marginLeft, marginRight};
			if (showTimeText && textOffset != 0)
			{
				reserveForOffsetElement(margins, width, height, textReserveWidth, textReserveHeight, textPosition,
					textOffset);
			}
			if (showIcon && iconOffset != 0)
			{
				reserveForOffsetElement(margins, width, height, iconSize, iconSize, iconPosition, iconOffset);
			}
			if (showDescription && descriptionOffset != 0)
			{
				reserveForOffsetElement(margins, width, height, descriptionWidth, descriptionHeight,
					descriptionPosition, descriptionOffset);
			}
			marginTop = margins[0];
			marginBottom = margins[1];
			marginLeft = margins[2];
			marginRight = margins[3];

			int itemCellWidth = marginLeft + width + marginRight;
			int itemCellHeight = marginTop + height + marginBottom;

			int placeX;
			int placeY;
			if (layout == RingLayout.VERTICAL)
			{
				if (cursorY > 0 && cursorY + itemCellHeight > availableHeight)
				{
					cursorY = 0;
					cursorX += lineExtent + gap;
					lineExtent = 0;
				}
				placeX = cursorX;
				placeY = cursorY;
				cursorY += itemCellHeight + gap;
				lineExtent = Math.max(lineExtent, itemCellWidth);
			}
			else
			{
				if (cursorX > 0 && cursorX + itemCellWidth > availableWidth)
				{
					cursorX = 0;
					cursorY += lineExtent + gap;
					lineExtent = 0;
				}
				placeX = cursorX;
				placeY = cursorY;
				cursorX += itemCellWidth + gap;
				lineExtent = Math.max(lineExtent, itemCellHeight);
			}
			totalWidth = Math.max(totalWidth, placeX + itemCellWidth);
			totalHeight = Math.max(totalHeight, placeY + itemCellHeight);

			long remainingMillis = display.getRemainingMillis(now);
			long peak = display.getPeakMillis();
			// With a fixed maximum, the fill (and percentage color) is measured against it instead of
			// the timer's own starting length, so e.g. with 2:00 everything over 2:00 shows full.
			long fullMillis = peak;
			if (!buff.isKillCounter() && plugin.isFixedMaximumEnabled(category))
			{
				fullMillis = plugin.getFixedMaximumSeconds(category) * 1000L;
			}
			double fraction = fullMillis <= 0 ? 0 : Math.min(1.0, (double) remainingMillis / fullMillis);
			int seconds = (int) Math.round(remainingMillis / 1000.0);

			FlashSettings flash = flashByCategory.get(category);
			boolean useFlash = flashPhaseOn && flash.enabled
				&& (plugin.isForceFlashEnabled(category) || seconds <= flash.thresholdSeconds);
			Color[] percentColors = percentColorsByCategory.get(category);
			Color baseColor = percentColors == null ? display.getColor()
				: UltimateTimersPlugin.percentageColor(percentColors[0], percentColors[1], percentColors[2], fraction);
			Color foreground = (useFlash && flash.foreground) ? flash.color : baseColor;
			Color background = (useFlash && flash.background) ? flash.color : plugin.getBackgroundColor(category);
			Color ringBorder = (useFlash && flash.border) ? flash.color : plugin.getBorderColor(category);
			Color textColor = (useFlash && flash.text) ? flash.color : plugin.getTextColor(category);

			TextStyle textStyle = new TextStyle(textColor, plugin.isTextBorderEnabled(category),
				plugin.getTextBorderColor(category), plugin.isTextShadowEnabled(category));

			int ringX = placeX + marginLeft;
			int ringY = placeY + marginTop;

			drawRingShape(graphics, ringX, ringY, width, height, fraction, foreground, ringBorder, background,
				ringBorderThickness, cornerRadius, fillDirection);

			if (showIcon && display.getIcon() != null)
			{
				Point p = resolveElementPosition(ringX, ringY, width, height, iconSize, iconSize, iconPosition,
					ELEMENT_GAP);
				applyOffset(p, iconPosition, iconOffset);
				graphics.drawImage(display.getIcon(), p.x, p.y, iconSize, iconSize, null);
			}

			if (showTimeText)
			{
				String text = buff.isKillCounter() ? String.valueOf(seconds) : formatSeconds(seconds);
				int textWidth = fm.stringWidth(text);
				int textHeight = fm.getHeight();
				Point p = resolveElementPosition(ringX, ringY, width, height, textWidth, textHeight, textPosition,
					ELEMENT_GAP);
				applyOffset(p, textPosition, textOffset);
				drawStyledText(graphics, text, p.x, p.y + fm.getAscent(), textStyle);
			}

			if (showDescription)
			{
				Point p = resolveElementPosition(ringX, ringY, width, height, descriptionWidth, descriptionHeight,
					descriptionPosition, ELEMENT_GAP);
				applyOffset(p, descriptionPosition, descriptionOffset);
				drawStyledText(graphics, description, p.x, p.y + fm.getAscent(), textStyle);
			}

			Rectangle ringRect = new Rectangle(overlayBounds.x + ringX, overlayBounds.y + ringY, width, height);
			if (ringRect.contains(mouse))
			{
				hoveredBuff = buff;
				if (tooltipsEnabled)
				{
					tooltipManager.add(new Tooltip(buff.isKillCounter()
						? slayerTaskTooltip(buff)
						: buff.getLabel() + " (" + plugin.getCategoryName(category) + ")"));
				}
			}
		}

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

	/**
	 * Moves p by offset pixels in the direction its position points: vertically for
	 * Top/Bottom/Above/Below, horizontally for Left/Right/Outside left/Outside right, and both
	 * for the corners. Center has no direction, so it doesn't move.
	 */
	private static void applyOffset(Point p, ElementPosition position, int offset)
	{
		p.translate(offsetDirectionX(position) * offset, offsetDirectionY(position) * offset);
	}

	private static int offsetDirectionX(ElementPosition position)
	{
		switch (position)
		{
			case LEFT:
			case OUTSIDE_LEFT:
			case TOP_LEFT:
			case BOTTOM_LEFT:
				return -1;
			case RIGHT:
			case OUTSIDE_RIGHT:
			case TOP_RIGHT:
			case BOTTOM_RIGHT:
				return 1;
			default:
				return 0;
		}
	}

	private static int offsetDirectionY(ElementPosition position)
	{
		switch (position)
		{
			case TOP:
			case ABOVE:
			case TOP_LEFT:
			case TOP_RIGHT:
				return -1;
			case BOTTOM:
			case BELOW:
			case BOTTOM_LEFT:
			case BOTTOM_RIGHT:
				return 1;
			default:
				return 0;
		}
	}

	/** Grows margins (top, bottom, left, right) to fit an offset element placed around a ring at 0,0. */
	private void reserveForOffsetElement(int[] margins, int ringW, int ringH, int elemW, int elemH,
		ElementPosition position, int offset)
	{
		Point p = resolveElementPosition(0, 0, ringW, ringH, elemW, elemH, position, ELEMENT_GAP);
		applyOffset(p, position, offset);
		margins[0] = Math.max(margins[0], -p.y);
		margins[1] = Math.max(margins[1], p.y + elemH - ringH);
		margins[2] = Math.max(margins[2], -p.x);
		margins[3] = Math.max(margins[3], p.x + elemW - ringW);
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
