package com.codex.navigator.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Quests that ask a one-life account to take a real risk: a boss fight hardcore players prepare for, or a trip into
 * the Wilderness. Kept short on purpose: only quests whose danger is well known, each with one plain line saying what
 * it is. A quest that is not listed is not promised to be safe; it simply has no fight of this kind.
 */
public final class Hazards
{
	/** One quest's risk. */
	public static final class Hazard
	{
		public enum Kind
		{
			/** A fight hardcore players gear and plan for. */
			BOSS,
			/** Part of the quest is played in the Wilderness, where other players can attack. */
			WILDERNESS
		}

		private final Kind kind;
		private final String line;

		Hazard(Kind kind, String line)
		{
			this.kind = kind;
			this.line = line;
		}

		public Kind getKind()
		{
			return kind;
		}

		/** What the danger is, in a sentence, such as "Elvarg breathes dragonfire: bring an anti-dragon shield". */
		public String getLine()
		{
			return line;
		}

		/** A pill-sized name for it, short enough to sit beside a row's time and level. */
		public String getLabel()
		{
			return kind == Kind.WILDERNESS ? "Wildy" : "Boss";
		}
	}

	private static final Map<String, Hazard> HAZARDS = new HashMap<>();

	static
	{
		boss("Dragon Slayer I", "Elvarg breathes dragonfire: bring an anti-dragon shield");
		boss("Monkey Madness I", "Ends with the Jungle Demon, a level 195 boss");
		boss("Monkey Madness II", "Hard fights near the end, Kruk among them");
		boss("Desert Treasure I", "Four boss fights, one for each diamond");
		boss("Desert Treasure II - The Fallen Empire", "Four hard boss fights, one for each medallion");
		boss("Recipe for Disaster - Culinaromancer", "Six boss fights in a row");
		boss("Legends' Quest", "Ends with fights against the demon Nezikchened");
		boss("Dragon Slayer II", "Vorkath, Galvek and a run of dragons");
		boss("Song of the Elves", "Ends with the Fragment of Seren");
		boss("Sins of the Father", "Ends with Vanstrom Klause");
		boss("Horror from the Deep", "Ends with the Dagannoth Mother");
		boss("Underground Pass", "Kalrag and three demons, with traps along the way");
		boss("Haunted Mine", "Treus Dayth, a level 95 ghost");
		boss("The Fremennik Isles", "Ends with the Ice Troll King");
		boss("Shilo Village", "Nazastarool, fought in three forms");
		boss("Beneath Cursed Sands", "Ends with the Champion of Scabaras");
		boss("A Night at the Theatre", "Boss fights from the Theatre of Blood");
		boss("While Guthix Sleeps", "Hard fights near the end");
		boss("The Great Brain Robbery", "Ends with the Barrelchest");
		boss("Perilous Moons", "Ends with the three Moons of Peril");
		boss("Swan Song", "Ends with the Sea Troll Queen");
		wilderness("Family Crest", "Chronozon waits in the Wilderness");
		wilderness("Devious Minds", "A trip through the Abyss, reached from the Wilderness");
		wilderness("Enter the Abyss", "Starts with the Zamorak Mage in the Wilderness");
		wilderness("Mage Arena I", "Kolodion, deep in the Wilderness");
		wilderness("Mage Arena II", "Three god bosses across the deep Wilderness");
		wilderness("Curse of the Empty Lord", "Visits across the Wilderness");
	}

	private Hazards()
	{
	}

	/** The quest's risk, or null when it has none of these. */
	public static Hazard of(String quest)
	{
		return quest == null ? null : HAZARDS.get(quest);
	}

	/** Every quest listed, for checking the names against the quest data. */
	static Iterable<String> names()
	{
		return HAZARDS.keySet();
	}

	private static void boss(String quest, String line)
	{
		HAZARDS.put(quest, new Hazard(Hazard.Kind.BOSS, line));
	}

	private static void wilderness(String quest, String line)
	{
		HAZARDS.put(quest, new Hazard(Hazard.Kind.WILDERNESS, line));
	}
}
