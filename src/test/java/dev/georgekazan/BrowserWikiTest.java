package dev.georgekazan;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BrowserWikiTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BrowserWikiPlugin.class);
		RuneLite.main(args);
	}
}