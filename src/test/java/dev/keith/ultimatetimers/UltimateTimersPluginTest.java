package dev.keith.ultimatetimers;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class UltimateTimersPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(UltimateTimersPlugin.class);
		RuneLite.main(args);
	}
}
