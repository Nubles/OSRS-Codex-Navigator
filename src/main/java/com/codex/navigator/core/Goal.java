package com.codex.navigator.core;

import java.util.Locale;
import java.util.Objects;
import net.runelite.api.Skill;

/** Something the player wants: a quest, a diary tier, the quest cape or a level in a skill. */
public final class Goal
{
	public enum Type
	{
		QUEST("quest"),
		DIARY("diary"),
		QUEST_CAPE("cape"),
		/** A level in one skill, named like {@code Agility 70}. */
		SKILL("skill");

		private final String prefix;

		Type(String prefix)
		{
			this.prefix = prefix;
		}
	}

	public static final Goal QUEST_CAPE = new Goal(Type.QUEST_CAPE, "Quest point cape");

	private final Type type;
	private final String name;

	public Goal(Type type, String name)
	{
		this.type = type;
		this.name = name;
	}

	public static Goal quest(String name)
	{
		return new Goal(Type.QUEST, name);
	}

	public static Goal diary(String name)
	{
		return new Goal(Type.DIARY, name);
	}

	/** A level goal, such as 70 Agility; null for a skill without levels or a level outside 2 to 99. */
	public static Goal level(Skill skill, int level)
	{
		if (skill == null || skill == Skill.OVERALL || level < 2 || level > 99)
		{
			return null;
		}
		return new Goal(Type.SKILL, skill.getName() + " " + level);
	}

	/** The skill of a level goal, or null for any other goal. */
	public Skill getSkill()
	{
		if (type != Type.SKILL)
		{
			return null;
		}
		int space = name.lastIndexOf(' ');
		if (space <= 0)
		{
			return null;
		}
		try
		{
			Skill skill = Skill.valueOf(name.substring(0, space).toUpperCase(Locale.ROOT));
			return skill == Skill.OVERALL ? null : skill;
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	/** The level a level goal aims for, or 0 for any other goal. */
	public int getLevel()
	{
		if (type != Type.SKILL)
		{
			return 0;
		}
		int space = name.lastIndexOf(' ');
		try
		{
			int level = Integer.parseInt(name.substring(space + 1));
			return level >= 2 && level <= 99 ? level : 0;
		}
		catch (NumberFormatException | IndexOutOfBoundsException e)
		{
			return 0;
		}
	}

	public Type getType()
	{
		return type;
	}

	public String getName()
	{
		return name;
	}

	/** A stable string for saving pinned goals, such as {@code quest:Dragon Slayer I}. */
	public String key()
	{
		return type.prefix + ":" + name;
	}

	public static Goal fromKey(String key)
	{
		int split = key.indexOf(':');
		if (split < 0)
		{
			return null;
		}
		String prefix = key.substring(0, split);
		String name = key.substring(split + 1).trim();
		for (Type type : Type.values())
		{
			if (type.prefix.equals(prefix))
			{
				if (type == Type.QUEST_CAPE)
				{
					return QUEST_CAPE;
				}
				if (type == Type.SKILL)
				{
					// Rebuilt from its parts, so "skill:agility 70" and other spellings land on one goal.
					Goal parsed = new Goal(Type.SKILL, name);
					return level(parsed.getSkill(), parsed.getLevel());
				}
				return name.isEmpty() ? null : new Goal(type, name);
			}
		}
		return null;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Goal))
		{
			return false;
		}
		Goal other = (Goal) o;
		return type == other.type && name.equals(other.name);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(type, name);
	}

	@Override
	public String toString()
	{
		return key();
	}
}
