package com.codex.navigator.core;

import com.codex.navigator.data.QuestDef;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * Rough time estimates: how long a level takes to train and how long a quest takes to play.
 *
 * <p>Rates are steady, ordinary methods rather than the fastest known ones, so an estimate errs a little long.
 * They exist to rank one choice against another ("an hour of Agility" against "eight hours of Smithing"),
 * never to promise a time, so the panel always shows them as "about".
 */
public final class Effort
{
	/** Experience per hour at level 1, 50 and 90; the rate between them is drawn as two straight lines. */
	private static final Map<Skill, int[]> RATES = new EnumMap<>(Skill.class);

	static
	{
		rate(Skill.ATTACK, 10_000, 40_000, 90_000);
		rate(Skill.STRENGTH, 10_000, 40_000, 90_000);
		rate(Skill.DEFENCE, 10_000, 40_000, 90_000);
		rate(Skill.RANGED, 12_000, 50_000, 100_000);
		rate(Skill.MAGIC, 12_000, 50_000, 100_000);
		rate(Skill.HITPOINTS, 4_000, 15_000, 32_000);
		rate(Skill.PRAYER, 15_000, 40_000, 80_000);
		rate(Skill.COOKING, 20_000, 100_000, 200_000);
		rate(Skill.WOODCUTTING, 5_000, 35_000, 75_000);
		rate(Skill.FLETCHING, 20_000, 100_000, 200_000);
		rate(Skill.FISHING, 5_000, 35_000, 65_000);
		rate(Skill.FIREMAKING, 20_000, 120_000, 250_000);
		rate(Skill.CRAFTING, 15_000, 80_000, 150_000);
		rate(Skill.SMITHING, 15_000, 60_000, 140_000);
		rate(Skill.MINING, 5_000, 35_000, 70_000);
		rate(Skill.HERBLORE, 10_000, 80_000, 200_000);
		rate(Skill.AGILITY, 6_000, 40_000, 65_000);
		rate(Skill.THIEVING, 10_000, 80_000, 200_000);
		rate(Skill.SLAYER, 5_000, 25_000, 50_000);
		rate(Skill.FARMING, 10_000, 60_000, 150_000);
		rate(Skill.RUNECRAFT, 8_000, 35_000, 70_000);
		rate(Skill.HUNTER, 8_000, 60_000, 120_000);
		rate(Skill.CONSTRUCTION, 20_000, 150_000, 400_000);
		rate(Skill.SAILING, 10_000, 50_000, 100_000);
	}

	private Effort()
	{
	}

	private static void rate(Skill skill, int atOne, int atFifty, int atNinety)
	{
		RATES.put(skill, new int[]{atOne, atFifty, atNinety});
	}

	/** Experience per hour while training at this level. */
	public static double xpPerHour(Skill skill, int level)
	{
		int[] r = RATES.getOrDefault(skill, new int[]{10_000, 40_000, 90_000});
		if (level <= 50)
		{
			return r[0] + (r[1] - r[0]) * (Math.max(1, level) - 1) / 49.0;
		}
		return r[1] + (r[2] - r[1]) * Math.min(1, (level - 50) / 40.0);
	}

	/**
	 * Hours to train from {@code currentXp} to the start of {@code target}, level by level so that
	 * the early levels count at early rates. Zero when the level is already reached.
	 */
	public static double trainingHours(Skill skill, int currentXp, int target)
	{
		return trainingHours(skill, currentXp, target, 1);
	}

	/**
	 * Hours to train to {@code target} for a player who trains {@code pace} times faster than the steady rates
	 * (see {@link Pace}). A pace of 1 is the table itself.
	 */
	public static double trainingHours(Skill skill, int currentXp, int target, double pace)
	{
		return tableHours(skill, currentXp, target) / (pace > 0 ? pace : 1);
	}

	/**
	 * The experience reached after {@code hours} of training from {@code currentXp} at the given pace, never past
	 * {@code capXp}. The inverse of {@link #trainingHours(Skill, int, int, double)}, for planning a fixed amount of time.
	 */
	public static int xpAfter(Skill skill, int currentXp, double hours, double pace, int capXp)
	{
		double left = hours * (pace > 0 ? pace : 1);
		int xp = Math.max(0, currentXp);
		int level = Math.max(1, Experience.getLevelForXp(xp));
		while (left > 0 && xp < capXp)
		{
			int levelEnd = level < Experience.MAX_REAL_LEVEL ? Math.min(capXp, Experience.getXpForLevel(level + 1)) : capXp;
			double rate = xpPerHour(skill, level);
			double needed = (levelEnd - xp) / rate;
			if (needed > left)
			{
				return (int) Math.min(capXp, xp + left * rate);
			}
			left -= needed;
			xp = levelEnd;
			level++;
		}
		return Math.min(xp, capXp);
	}

	private static double tableHours(Skill skill, int currentXp, int target)
	{
		int goal = Experience.getXpForLevel(Math.max(1, Math.min(target, Experience.MAX_REAL_LEVEL)));
		if (currentXp >= goal)
		{
			return 0;
		}
		double hours = 0;
		int level = Math.max(1, Experience.getLevelForXp(Math.max(0, currentXp)));
		int xp = Math.max(0, currentXp);
		while (xp < goal && level < Experience.MAX_REAL_LEVEL)
		{
			int levelEnd = Math.min(goal, Experience.getXpForLevel(level + 1));
			hours += (levelEnd - xp) / xpPerHour(skill, level);
			xp = levelEnd;
			level++;
		}
		return hours;
	}

	/** A typical first play-through of a quest, by difficulty. */
	public static double questHours(QuestDef quest)
	{
		if (quest.miniquest)
		{
			return 0.4;
		}
		switch (quest.difficultyRank())
		{
			case 1:
				return 0.3;
			case 2:
				return 0.6;
			case 3:
				return 1.0;
			case 4:
				return 1.6;
			case 5:
				return 3.0;
			default:
				return 0.5;
		}
	}

	/**
	 * A time for the panel: "about 20 min", "about 1.5 h", "about 12 h". Under a minute or so reads as
	 * "minutes"; whole hours past ten, since a decimal there is false precision.
	 */
	public static String format(double hours)
	{
		if (hours <= 0)
		{
			return "no time";
		}
		if (hours < 0.95)
		{
			int minutes = (int) Math.max(5, Math.round(hours * 60 / 5.0) * 5);
			return "~" + minutes + " min";
		}
		if (hours < 10)
		{
			double rounded = Math.round(hours * 2) / 2.0;
			return "~" + (rounded == Math.floor(rounded) ? String.valueOf((int) rounded) : String.valueOf(rounded)) + " h";
		}
		return "~" + Math.round(hours) + " h";
	}
}
