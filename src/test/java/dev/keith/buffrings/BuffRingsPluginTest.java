package dev.keith.buffrings;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BuffRingsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BuffRingsPlugin.class);
		RuneLite.main(args);
	}
}
