package com.codex.navigator;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(CodexNavigatorConfig.GROUP)
public interface CodexNavigatorConfig extends Config
{
	String GROUP = "codexnavigator";

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show focus in game",
		description = "Shows your focus goal and its next step in a small overlay",
		position = 1
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "suggestWithoutFocus",
		name = "Suggest when unfocused",
		description = "With no goal pinned, the overlay suggests the best quest you can start right now",
		position = 2
	)
	default boolean suggestWithoutFocus()
	{
		return true;
	}

	@ConfigItem(
		keyName = "includeMiniquests",
		name = "Include miniquests",
		description = "Lists and suggests miniquests alongside quests",
		position = 3
	)
	default boolean includeMiniquests()
	{
		return true;
	}

	@ConfigItem(
		keyName = "announceCompletions",
		name = "Announce finished goals",
		description = "Posts a line in your chatbox the moment a pinned goal is complete",
		position = 4
	)
	default boolean announceCompletions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "learnPace",
		name = "Learn my pace",
		description = "Times training estimates by how fast you really train each skill, learned while you play",
		position = 5
	)
	default boolean learnPace()
	{
		return true;
	}

	@ConfigItem(
		keyName = "oneLifeCare",
		name = "Hardcore care",
		description = "On a one-life account (hardcore ironman, a Hardcore World, or marked one-life in Codex Guardian),"
			+ " quests with a deadly fight or a Wilderness trip are marked and suggested after safer ones",
		position = 6
	)
	default boolean oneLifeCare()
	{
		return true;
	}
}
