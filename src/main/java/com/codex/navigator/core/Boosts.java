package com.codex.navigator.core;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * The everyday boost for each skill: one item a player can buy or make, and how many levels it adds. A quest
 * requirement marked boostable can be met with it instead of training, if the gap is no bigger than the boost.
 *
 * <p>Only plain, repeatable boosts are listed: pies, potions and the dwarven stout. Spicy stew, special
 * attacks and minigame rewards are left out because they are random, locked or need the skill already.
 * Skills with no such boost (Prayer, Woodcutting, Firemaking and so on) have none here, so the Codex says only
 * that the requirement can be boosted, never which item does it.
 */
public final class Boosts
{
	/** One boost: what to use and how many levels it gives at a given level. */
	public static final class Boost
	{
		private final String item;
		private final int flat;
		private final int percent;

		Boost(String item, int flat, int percent)
		{
			this.item = item;
			this.flat = flat;
			this.percent = percent;
		}

		/** The item in plain words, such as "a summer pie". */
		public String getItem()
		{
			return item;
		}

		/** Levels added at this real level: a flat amount plus a share of the level, rounded down. */
		public int amount(int level)
		{
			return flat + level * percent / 100;
		}
	}

	private static final Map<Skill, Boost> BOOSTS = new EnumMap<>(Skill.class);

	static
	{
		BOOSTS.put(Skill.AGILITY, new Boost("a summer pie", 5, 0));
		BOOSTS.put(Skill.ATTACK, new Boost("a super attack", 5, 15));
		BOOSTS.put(Skill.STRENGTH, new Boost("a super strength", 5, 15));
		BOOSTS.put(Skill.DEFENCE, new Boost("a super defence", 5, 15));
		BOOSTS.put(Skill.RANGED, new Boost("a ranging potion", 4, 10));
		BOOSTS.put(Skill.MAGIC, new Boost("a magic potion", 4, 0));
		BOOSTS.put(Skill.FISHING, new Boost("an admiral pie", 5, 0));
		BOOSTS.put(Skill.CRAFTING, new Boost("a mushroom pie", 4, 0));
		BOOSTS.put(Skill.FLETCHING, new Boost("a dragonfruit pie", 4, 0));
		BOOSTS.put(Skill.HERBLORE, new Boost("a botanical pie", 4, 0));
		BOOSTS.put(Skill.SLAYER, new Boost("a wild pie", 5, 0));
		BOOSTS.put(Skill.FARMING, new Boost("a garden pie", 3, 0));
		BOOSTS.put(Skill.HUNTER, new Boost("a hunter potion", 3, 0));
		BOOSTS.put(Skill.MINING, new Boost("a dwarven stout", 1, 0));
		BOOSTS.put(Skill.SMITHING, new Boost("a dwarven stout", 1, 0));
	}

	private Boosts()
	{
	}

	/** The everyday boost for a skill, or null when it has none. */
	public static Boost of(Skill skill)
	{
		return BOOSTS.get(skill);
	}

	/** True when the skill's everyday boost lifts {@code current} to at least {@code target}. */
	public static boolean reaches(Skill skill, int current, int target)
	{
		Boost boost = BOOSTS.get(skill);
		return boost != null && current + boost.amount(current) >= target;
	}

	/**
	 * A short line for a list row: "Summer pie for Agility 56", or "Boost Agility to 56" for a skill with no
	 * everyday boost.
	 */
	public static String brief(Skill skill, int target)
	{
		Boost boost = BOOSTS.get(skill);
		if (boost == null)
		{
			return "Boost " + skill.getName() + " to " + target;
		}
		String item = boost.getItem().replaceFirst("^an? ", "");
		return Character.toUpperCase(item.charAt(0)) + item.substring(1) + " for " + skill.getName() + " " + target;
	}

	/** "Agility 52 to 56 with a summer pie (+5)", or without the item for a skill that has no everyday boost. */
	public static String describe(Skill skill, int current, int target)
	{
		Boost boost = BOOSTS.get(skill);
		String base = "Boost " + skill.getName() + " " + current + " to " + target;
		return boost == null ? base : base + " with " + boost.getItem() + " (+" + boost.amount(current) + ")";
	}
}
