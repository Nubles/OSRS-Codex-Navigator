package com.codex.navigator.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The account's combat achievements, task by task, as the game itself lists them: every task the game's own Combat
 * Achievements window shows (read from its tier lists in the game cache), which of them are done (the game's task
 * completion bits), the points each tier's reward needs (the game's own thresholds) and the kill counts known for each
 * monster. Immutable and plain, so the planner and panel can read it on any thread.
 */
public final class CombatTasks
{
	/** The tiers, easiest first: the game's own tier names, as on the OSRS Wiki's Combat Achievements page. */
	public static final String[] TIERS = {"Easy", "Medium", "Hard", "Elite", "Master", "Grandmaster"};

	/** Points a task of each tier is worth (OSRS Wiki, Combat Achievements: "Points per task"). */
	static final int[] POINTS = {1, 2, 3, 4, 5, 6};

	/**
	 * Points each tier's reward needs, as the OSRS Wiki gives them today. Only a fallback: the game sends its own
	 * thresholds, which move when new tasks come out, and those are used whenever they are known.
	 */
	static final int[] WIKI_THRESHOLDS = {41, 169, 436, 1100, 1965, 2697};

	/** Each tier's best-known rewards, quoted from the OSRS Wiki's Combat Achievements reward overview. */
	static final String[][] REWARDS = {
		{"Ghommal's hilt 1", "Antique lamp worth 5,000 experience in any skill of at least level 20",
			"Three daily teleports to the God Wars Dungeon entrance (by the large boulder)"},
		{"Ghommal's hilt 2", "Antique lamp worth 10,000 experience in any skill of at least level 30",
			"Five daily teleports to the God Wars Dungeon entrance"},
		{"Ghommal's hilt 3", "Antique lamp worth 15,000 experience in any skill of at least level 40",
			"Unlimited teleports to the God Wars Dungeon entrance"},
		{"Ghommal's hilt 4", "Antique lamp worth 25,000 experience in any skill of at least level 50",
			"Three daily teleports to Mor Ul Rek (south-east of the inner city bank)"},
		{"Ghommal's hilt 5", "Antique lamp worth 35,000 experience in any skill of at least level 60",
			"Five daily teleports to Mor Ul Rek"},
		{"Ghommal's hilt 6", "Antique lamp worth 50,000 experience in any skill of at least level 70",
			"Unlimited teleports to Mor Ul Rek"},
	};

	/** The game's name for kill count tasks (its task type list). */
	public static final String KILL_COUNT = "Kill Count";
	/** The game's monster for tasks that belong to no one boss. */
	static final String OTHER = "Other";

	/** "Kill Vorkath 50 times.", "Open the Barrows chest 10 times.": the number a kill count task asks for. */
	private static final Pattern TIMES = Pattern.compile("\\b([0-9][0-9,]*) times\\b");

	public static final CombatTasks EMPTY = new CombatTasks(Collections.emptyList(), new BitSet(), null,
		Collections.emptyMap(), null);

	/** One task, as the game describes it. */
	public static final class Task
	{
		private final int id;
		private final String name;
		private final String description;
		/** 1 for Easy to 6 for Grandmaster. */
		private final int tier;
		private final String type;
		private final String monster;

		public Task(int id, String name, String description, int tier, String type, String monster)
		{
			this.id = id;
			this.name = name == null ? "" : name;
			this.description = description == null ? "" : description;
			this.tier = tier;
			this.type = type == null ? "" : type;
			this.monster = monster == null || monster.isEmpty() ? OTHER : monster;
		}

		public int getId()
		{
			return id;
		}

		public String getName()
		{
			return name;
		}

		public String getDescription()
		{
			return description;
		}

		public int getTier()
		{
			return tier;
		}

		public String getTierName()
		{
			return tierName(tier);
		}

		public String getType()
		{
			return type;
		}

		public String getMonster()
		{
			return monster;
		}

		public int getPoints()
		{
			return tier >= 1 && tier <= POINTS.length ? POINTS[tier - 1] : 0;
		}

		/** True when the task belongs to one boss or monster rather than to combat in general. */
		public boolean hasMonster()
		{
			return !OTHER.equals(monster);
		}

		/** For a kill count task, the kills it asks for ("50 times"); 0 for any other task or when it names none. */
		public int killsNeeded()
		{
			if (!KILL_COUNT.equals(type))
			{
				return 0;
			}
			Matcher m = TIMES.matcher(description);
			if (!m.find())
			{
				return 0;
			}
			try
			{
				return Integer.parseInt(m.group(1).replace(",", ""));
			}
			catch (NumberFormatException e)
			{
				return 0;
			}
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Task))
			{
				return false;
			}
			Task t = (Task) o;
			return id == t.id && tier == t.tier && name.equals(t.name) && monster.equals(t.monster) && type.equals(t.type)
				&& description.equals(t.description);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(id, name, tier);
		}

		@Override
		public String toString()
		{
			return name + " (" + getTierName() + ", " + monster + ")";
		}
	}

	/** An unfinished task with why it is near for this account. */
	public static final class Near
	{
		private final Task task;
		private final double score;
		private final String why;
		private final int kills;
		private final double progress;

		Near(Task task, double score, String why, int kills, double progress)
		{
			this.task = task;
			this.score = score;
			this.why = why;
			this.kills = kills;
			this.progress = progress;
		}

		public Task getTask()
		{
			return task;
		}

		/** Higher is nearer. Only for ordering. */
		public double getScore()
		{
			return score;
		}

		/** Why it is near, in plain words: "38 of 50 kills", "You have 212 Vorkath kills", "3 of 9 Vorkath tasks done", or only the monster when nothing is known. */
		public String getWhy()
		{
			return why;
		}

		/** The kill count known for the task's monster, or -1. */
		public int getKills()
		{
			return kills;
		}

		/** For a kill count task with a known count, how far along it is (0 to 1); otherwise -1. */
		public double getProgress()
		{
			return progress;
		}
	}

	private final List<Task> tasks;
	private final BitSet done;
	private final int[] thresholds;
	private final Map<String, Integer> kills;
	private final String killSource;

	/**
	 * @param tasks      every task, as the game lists them
	 * @param done       task ids the game marks complete
	 * @param thresholds the game's points for each tier's reward, Easy first; null or zeros where unknown
	 * @param kills      kill counts by the game's monster name, from whichever source knew them
	 * @param killSource where the kill counts came from, said under the list ("Boss Pilot"), or null
	 */
	public CombatTasks(List<Task> tasks, BitSet done, int[] thresholds, Map<String, Integer> kills, String killSource)
	{
		this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
		this.done = (BitSet) done.clone();
		this.thresholds = new int[TIERS.length];
		for (int i = 0; i < TIERS.length; i++)
		{
			int t = thresholds != null && i < thresholds.length ? thresholds[i] : 0;
			this.thresholds[i] = t > 0 ? t : WIKI_THRESHOLDS[i];
		}
		this.kills = Collections.unmodifiableMap(new HashMap<>(kills));
		this.killSource = killSource;
	}

	public static String tierName(int tier)
	{
		return tier >= 1 && tier <= TIERS.length ? TIERS[tier - 1] : "";
	}

	/** 1 for "Easy" to 6 for "Grandmaster", ignoring case; 0 for anything else. */
	public static int tierOf(String name)
	{
		for (int i = 0; i < TIERS.length; i++)
		{
			if (TIERS[i].equalsIgnoreCase(name == null ? "" : name.trim()))
			{
				return i + 1;
			}
		}
		return 0;
	}

	/** The reward lines for a tier, quoted from the OSRS Wiki. */
	public static List<String> rewards(int tier)
	{
		return tier >= 1 && tier <= REWARDS.length ? Arrays.asList(REWARDS[tier - 1]) : Collections.emptyList();
	}

	/** True once the game's task list has been read. */
	public boolean isKnown()
	{
		return !tasks.isEmpty();
	}

	public List<Task> tasks()
	{
		return tasks;
	}

	public boolean isDone(Task task)
	{
		return task.getId() >= 0 && done.get(task.getId());
	}

	/** Points a tier's reward needs: the game's own figure when known, else the wiki's. */
	public int threshold(int tier)
	{
		return tier >= 1 && tier <= TIERS.length ? thresholds[tier - 1] : 0;
	}

	public int tasksIn(int tier)
	{
		int n = 0;
		for (Task t : tasks)
		{
			n += t.getTier() == tier ? 1 : 0;
		}
		return n;
	}

	public int doneIn(int tier)
	{
		int n = 0;
		for (Task t : tasks)
		{
			n += t.getTier() == tier && isDone(t) ? 1 : 0;
		}
		return n;
	}

	/** Points from the tasks marked done; the game's own total is preferred where it is known. */
	public int pointsFromTasks()
	{
		int n = 0;
		for (Task t : tasks)
		{
			n += isDone(t) ? t.getPoints() : 0;
		}
		return n;
	}

	/** The first tier whose reward these points do not reach yet, or 0 when every tier is reached. */
	public int nextTier(int points)
	{
		for (int tier = 1; tier <= TIERS.length; tier++)
		{
			if (points < threshold(tier))
			{
				return tier;
			}
		}
		return 0;
	}

	/** The kill count known for a monster, or -1. */
	public int kills(String monster)
	{
		Integer k = monster == null ? null : kills.get(monster);
		return k == null ? -1 : k;
	}

	public Map<String, Integer> allKills()
	{
		return kills;
	}

	public String getKillSource()
	{
		return killSource;
	}

	/** Tasks done and in all for each monster, by the game's monster name. */
	public Map<String, int[]> byMonster()
	{
		Map<String, int[]> out = new LinkedHashMap<>();
		for (Task t : tasks)
		{
			int[] c = out.computeIfAbsent(t.getMonster(), k -> new int[2]);
			c[0] += isDone(t) ? 1 : 0;
			c[1]++;
		}
		return out;
	}

	/**
	 * The unfinished tasks nearest for this account, nearest first. Near means the account already does it: a boss
	 * with a kill count, a monster with tasks already done there, and a kill count task already part way. The tier
	 * only breaks ties toward the easier task, since the game rates its own tiers by difficulty.
	 *
	 * @param tier  only tasks of this tier, or 0 for every tier
	 * @param limit at most this many
	 */
	public List<Near> nearest(int tier, int limit)
	{
		Map<String, int[]> monsters = byMonster();
		List<Near> out = new ArrayList<>();
		for (Task t : tasks)
		{
			if (isDone(t) || tier != 0 && t.getTier() != tier)
			{
				continue;
			}
			out.add(near(t, monsters.get(t.getMonster())));
		}
		out.sort(Comparator.comparingDouble(Near::getScore).reversed()
			.thenComparingInt(n -> n.getTask().getTier())
			.thenComparing(n -> n.getTask().getName()));
		return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
	}

	Near near(Task t, int[] monster)
	{
		int kc = t.hasMonster() ? kills(t.getMonster()) : -1;
		int doneHere = monster == null ? 0 : monster[0];
		int allHere = monster == null ? 0 : monster[1];
		int needed = t.killsNeeded();
		double score = 0;
		double progress = -1;
		String why = null;
		if (needed > 0 && kc >= 0)
		{
			progress = Math.min(1, kc / (double) needed);
			score += 4 * progress;
			why = kc >= needed ? "Kill count " + kc + ", past the " + needed + " it asks"
				: kc + " of " + needed + " kills";
		}
		if (kc > 0)
		{
			score += 3 + Math.min(2, Math.log10(kc + 1));
			if (why == null)
			{
				why = "You have " + kc + " " + t.getMonster() + (kc == 1 ? " kill" : " kills");
			}
		}
		if (t.hasMonster() && doneHere > 0)
		{
			score += 1 + 2.0 * doneHere / Math.max(1, allHere);
			if (why == null)
			{
				why = doneHere + " of " + allHere + " " + t.getMonster() + " tasks done";
			}
		}
		score -= 0.75 * (t.getTier() - 1);
		if (why == null)
		{
			// Nothing done or fought there yet: name where the task is, which is what a player looks for.
			why = t.hasMonster() ? t.getMonster() : "Any monster";
		}
		return new Near(t, score, why, kc, progress);
	}

	/**
	 * How many of the nearest unfinished tasks it takes to earn {@code points} more, taking them nearest first; 0 when
	 * nothing more is needed and -1 when the unfinished tasks cannot add up to it.
	 */
	public int tasksToEarn(int points)
	{
		if (points <= 0)
		{
			return 0;
		}
		int sum = 0;
		int n = 0;
		for (Near near : nearest(0, Integer.MAX_VALUE))
		{
			sum += near.getTask().getPoints();
			n++;
			if (sum >= points)
			{
				return n;
			}
		}
		return -1;
	}

	/** Changes when anything shown changes; kept apart from equals so snapshots compare cheaply. */
	public int signature()
	{
		return Objects.hash(tasks.size(), done, Arrays.hashCode(thresholds), kills);
	}

	/** "Tombs of Amascut: Expert Mode" and "tombs of amascut expert mode" both read "tombsofamascutexpertmode". */
	public static String normalise(String name)
	{
		return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}
}
