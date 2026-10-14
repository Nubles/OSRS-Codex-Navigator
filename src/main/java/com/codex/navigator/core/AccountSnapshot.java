package com.codex.navigator.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Experience;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * An immutable read of everything the planner needs, taken on the client thread
 * and handed to Swing. Quests that the client cannot report are simply absent.
 */
public final class AccountSnapshot
{
	private final boolean loggedIn;
	private final String playerName;
	private final Map<Skill, Integer> levels;
	private final Map<Skill, Integer> xp;
	private final Map<String, QuestState> quests;
	private final Set<String> diariesDone;
	private final Map<String, Integer> combatAchievementsDone;
	private final int questPoints;
	private final int combatLevel;
	private final int combatAchievementPoints;

	private AccountSnapshot(Builder b)
	{
		loggedIn = b.loggedIn;
		playerName = b.playerName;
		levels = Collections.unmodifiableMap(new EnumMap<>(b.levels));
		xp = Collections.unmodifiableMap(new EnumMap<>(b.xp));
		quests = Collections.unmodifiableMap(new HashMap<>(b.quests));
		diariesDone = Collections.unmodifiableSet(new HashSet<>(b.diariesDone));
		combatAchievementsDone = Collections.unmodifiableMap(new HashMap<>(b.combatAchievementsDone));
		questPoints = b.questPoints;
		combatLevel = b.combatLevel;
		combatAchievementPoints = b.combatAchievementPoints;
	}

	public static AccountSnapshot empty()
	{
		return new Builder().build();
	}

	public boolean isLoggedIn()
	{
		return loggedIn;
	}

	public String getPlayerName()
	{
		return playerName;
	}

	public int level(Skill skill)
	{
		return levels.getOrDefault(skill, skill == Skill.HITPOINTS ? 10 : 1);
	}

	public int xp(Skill skill)
	{
		Integer value = xp.get(skill);
		return value != null ? value : Experience.getXpForLevel(level(skill));
	}

	public int totalLevel()
	{
		int total = 0;
		for (Skill skill : Skill.values())
		{
			total += level(skill);
		}
		return total;
	}

	public QuestState quest(String name)
	{
		return quests.get(name);
	}

	public boolean questDone(String name)
	{
		return quests.get(name) == QuestState.FINISHED;
	}

	public boolean knowsQuest(String name)
	{
		return quests.containsKey(name);
	}

	public boolean diaryDone(String name)
	{
		return diariesDone.contains(name);
	}

	public int diariesCompleted()
	{
		return diariesDone.size();
	}

	public int combatAchievementsDone(String tier)
	{
		return combatAchievementsDone.getOrDefault(tier, 0);
	}

	public int getQuestPoints()
	{
		return questPoints;
	}

	public int getCombatLevel()
	{
		return combatLevel;
	}

	public int getCombatAchievementPoints()
	{
		return combatAchievementPoints;
	}

	/** Changes whenever anything the panel shows changes, so unchanged ticks can be skipped. */
	public int signature()
	{
		return java.util.Objects.hash(loggedIn, playerName, levels, xp, quests, diariesDone, combatAchievementsDone,
			questPoints, combatLevel, combatAchievementPoints);
	}

	public static final class Builder
	{
		private boolean loggedIn;
		private String playerName;
		private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		private final Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
		private final Map<String, QuestState> quests = new HashMap<>();
		private final Set<String> diariesDone = new HashSet<>();
		private final Map<String, Integer> combatAchievementsDone = new HashMap<>();
		private int questPoints;
		private int combatLevel = 3;
		private int combatAchievementPoints;

		public Builder loggedIn(boolean value)
		{
			loggedIn = value;
			return this;
		}

		public Builder playerName(String value)
		{
			playerName = value;
			return this;
		}

		public Builder skill(Skill skill, int level, int experience)
		{
			levels.put(skill, level);
			xp.put(skill, experience);
			return this;
		}

		public Builder level(Skill skill, int level)
		{
			levels.put(skill, level);
			return this;
		}

		public Builder quest(String name, QuestState state)
		{
			quests.put(name, state);
			return this;
		}

		public Builder diary(String name)
		{
			diariesDone.add(name);
			return this;
		}

		public Builder combatAchievements(String tier, int done)
		{
			combatAchievementsDone.put(tier, done);
			return this;
		}

		public Builder questPoints(int value)
		{
			questPoints = value;
			return this;
		}

		public Builder combatLevel(int value)
		{
			combatLevel = value;
			return this;
		}

		public Builder combatAchievementPoints(int value)
		{
			combatAchievementPoints = value;
			return this;
		}

		public AccountSnapshot build()
		{
			return new AccountSnapshot(this);
		}
	}
}
