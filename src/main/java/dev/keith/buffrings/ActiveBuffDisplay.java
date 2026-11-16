package dev.keith.buffrings;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;

/**
 * Carries an endTime + peak rather than a precomputed fraction/seconds, so the owning overlay
 * can compute the live remaining time fresh on every render frame (matching how RuneLite's own
 * Timer infobox stays perfectly real-time instead of only updating once per game tick).
 *
 * The one exception is frozenRemainingMillis, used only by the debug "force show every ring"
 * preview: a fixed value that getRemainingMillis() returns as-is, completely bypassing the live
 * now/endTime computation, so the preview is an actual still image rather than something that
 * recomputes to a very slightly different value every render frame.
 */
class ActiveBuffDisplay
{
	private final TrackedBuff buff;
	private final Instant endTime;
	private final Long frozenRemainingMillis;
	private final long peakMillis;
	private final Color color;
	private final BufferedImage icon;

	ActiveBuffDisplay(TrackedBuff buff, Instant endTime, long peakMillis, Color color, BufferedImage icon)
	{
		this.buff = buff;
		this.endTime = endTime;
		this.frozenRemainingMillis = null;
		this.peakMillis = peakMillis;
		this.color = color;
		this.icon = icon;
	}

	ActiveBuffDisplay(TrackedBuff buff, long frozenRemainingMillis, long peakMillis, Color color, BufferedImage icon)
	{
		this.buff = buff;
		this.endTime = null;
		this.frozenRemainingMillis = frozenRemainingMillis;
		this.peakMillis = peakMillis;
		this.color = color;
		this.icon = icon;
	}

	TrackedBuff getBuff()
	{
		return buff;
	}

	long getRemainingMillis(Instant now)
	{
		if (frozenRemainingMillis != null)
		{
			return frozenRemainingMillis;
		}
		return Math.max(0, Duration.between(now, endTime).toMillis());
	}

	long getPeakMillis()
	{
		return peakMillis;
	}

	Color getColor()
	{
		return color;
	}

	BufferedImage getIcon()
	{
		return icon;
	}
}
