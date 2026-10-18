package com.codex.navigator.core;

import java.util.Objects;

/** Something the player wants: a quest, a diary tier or the quest cape. */
public final class Goal
{
	public enum Type
	{
		QUEST("quest"),
		DIARY("diary"),
		QUEST_CAPE("cape");

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
