package dev.keith.ultimatetimers;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(UltimateTimersConfig.GROUP)
public interface UltimateTimersConfig extends Config
{
	String GROUP = "ultimatetimers";

	@ConfigSection(
		name = "Debug",
		description = "Tools for arranging and previewing timers without triggering the real effects",
		position = 2,
		closedByDefault = true
	)
	String debugSection = "debugSection";

	@Range(min = 1, max = 3600)
	@ConfigItem(
		keyName = "debugPreviewTotalSeconds",
		name = "Preview total time (seconds)",
		description = "The \"out of\" value for the frozen preview timer shown by a category's \"Force show\" "
			+ "setting (in its settings dialog in the side panel)",
		position = 1,
		section = debugSection
	)
	default int debugPreviewTotalSeconds()
	{
		return 60;
	}

	@Range(min = 0, max = 3600)
	@ConfigItem(
		keyName = "debugPreviewRemainingSeconds",
		name = "Preview remaining time (seconds)",
		description = "The remaining-time value for the frozen preview timer shown by a category's \"Force show\" "
			+ "setting (in its settings dialog in the side panel)",
		position = 2,
		section = debugSection
	)
	default int debugPreviewRemainingSeconds()
	{
		return 50;
	}
}
