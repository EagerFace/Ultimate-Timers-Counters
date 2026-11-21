package dev.keith.ultimatetimers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Map;
import org.junit.Test;

public class SettingsImportTest
{
	private static final Gson GSON = new Gson();

	private static Map<String, Map<String, String>> parse(String json)
	{
		return UltimateTimersPlugin.parseSettings(GSON, json);
	}

	@Test
	public void keepsOwnSettingsAndDropsEverythingElse()
	{
		Map<String, Map<String, String>> settings = parse("{\"format\":\"ultimate-timers-settings\",\"version\":1,"
			+ "\"settings\":{"
			+ "\"ultimatetimers\":{\"ringWidth\":\"40\"},"
			+ "\"ultimatetimerstimercolors\":{\"STAMINA\":\"-16711936\"},"
			+ "\"runelite\":{\"UltimateTimersGroup_default_preferredLocation\":\"10:20\","
			+ "\"ultimatetimersplugin\":\"false\",\"someotherplugin\":\"true\"},"
			+ "\"someotherplugin\":{\"key\":\"value\"}}}");

		assertEquals("40", settings.get("ultimatetimers").get("ringWidth"));
		assertEquals("-16711936", settings.get("ultimatetimerstimercolors").get("STAMINA"));
		// Overlay positions are ours; the plugin on/off switch and other plugins' keys aren't.
		assertEquals(1, settings.get("runelite").size());
		assertTrue(settings.get("runelite").containsKey("UltimateTimersGroup_default_preferredLocation"));
		assertFalse(settings.containsKey("someotherplugin"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNonJson()
	{
		parse("this is not json");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsOtherFileFormats()
	{
		parse("{\"format\":\"something-else\",\"version\":1,\"settings\":{\"ultimatetimers\":{\"a\":\"1\"}}}");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNewerVersions()
	{
		parse("{\"format\":\"ultimate-timers-settings\",\"version\":99,\"settings\":{\"ultimatetimers\":{\"a\":\"1\"}}}");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsMissingVersion()
	{
		parse("{\"format\":\"ultimate-timers-settings\",\"settings\":{\"ultimatetimers\":{\"a\":\"1\"}}}");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsFilesWithNothingOfOurs()
	{
		parse("{\"format\":\"ultimate-timers-settings\",\"version\":1,\"settings\":{\"someotherplugin\":{\"a\":\"1\"}}}");
	}
}
