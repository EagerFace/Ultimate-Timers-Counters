package dev.keith.buffrings;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("buffrings")
public interface BuffRingsConfig extends Config
{
	@ConfigItem(
		keyName = "showAntifire",
		name = "Antifire potions",
		description = "Show a countdown ring for antifire and super antifire",
		position = 1
	)
	default boolean showAntifire()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showStamina",
		name = "Stamina potions",
		description = "Show a countdown ring for the stamina potion's run-energy-drain immunity",
		position = 2
	)
	default boolean showStamina()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDivine",
		name = "Divine potions",
		description = "Show a countdown ring for divine potion effects",
		position = 3
	)
	default boolean showDivine()
	{
		return true;
	}

	@Range(min = 16, max = 80)
	@ConfigItem(
		keyName = "ringDiameter",
		name = "Ring diameter",
		description = "Diameter of each countdown ring, in pixels",
		position = 4
	)
	default int ringDiameter()
	{
		return 32;
	}

	@ConfigItem(
		keyName = "showText",
		name = "Show seconds remaining",
		description = "Display the time left in the center of each ring",
		position = 5
	)
	default boolean showText()
	{
		return true;
	}
}
