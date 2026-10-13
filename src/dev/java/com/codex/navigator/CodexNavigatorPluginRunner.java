package com.codex.navigator;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CodexNavigatorPluginRunner
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CodexNavigatorPlugin.class);
		RuneLite.main(args);
	}
}
