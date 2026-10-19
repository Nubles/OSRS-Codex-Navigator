package com.codex.navigator;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(CodexNavigatorConfig.GROUP)
public interface CodexNavigatorConfig extends Config
{
	String GROUP = "codexnavigator";

	@ConfigSection(
		name = "In game",
		description = "What Navigator shows over the game and in your chatbox",
		position = 0
	)
	String inGame = "inGame";

	@ConfigSection(
		name = "Planning",
		description = "What Navigator suggests and how it times your goals",
		position = 1
	)
	String planning = "planning";

	@ConfigSection(
		name = "Cards",
		description = "What goes on the road card you save or copy",
		position = 2
	)
	String cards = "cards";

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show focus in game",
		description = "Shows your focus goal and its next step in a small overlay",
		position = 1,
		section = inGame
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "suggestWithoutFocus",
		name = "Suggest with no focus",
		description = "With no goal pinned, the overlay suggests the best quest you can start right now",
		position = 2,
		section = inGame
	)
	default boolean suggestWithoutFocus()
	{
		return true;
	}

	@ConfigItem(
		keyName = "announceCompletions",
		name = "Announce finished goals",
		description = "Posts a line in your chatbox, seen only by you, the moment a pinned goal is complete",
		position = 3,
		section = inGame
	)
	default boolean announceCompletions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "includeMiniquests",
		name = "Include miniquests",
		description = "Lists and suggests miniquests alongside quests",
		position = 1,
		section = planning
	)
	default boolean includeMiniquests()
	{
		return true;
	}

	@ConfigItem(
		keyName = "learnPace",
		name = "Learn my pace",
		description = "Times training estimates by how fast you really train each skill, learned while you play",
		position = 2,
		section = planning
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
		position = 3,
		section = planning
	)
	default boolean oneLifeCare()
	{
		return true;
	}

	@ConfigItem(
		keyName = "nameOnCard",
		name = "Name on cards",
		description = "Write your character's name on the road card; turn off to share it without it",
		position = 1,
		section = cards
	)
	default boolean nameOnCard()
	{
		return true;
	}
}
