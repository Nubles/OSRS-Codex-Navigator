package com.codex.navigator.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chompy bird hats and this account's chompy kills: the count, kept from the game's own chat lines, and how fast this
 * player really hunts, learned from the time between kills. Facts and their sources are in the pull request that added
 * this; every one is quoted from the OSRS Wiki unless marked otherwise.
 *
 * <p>The count is the game's: "Check kills" on an ogre bow says the total, and it is taken as it stands. Each kill
 * message after that adds one. Before the first check the total is not known, so kills seen are kept aside and added
 * once the bow has been checked. Jubbly birds count toward the hats too (OSRS Wiki, Western Provinces Diary: "chompy or
 * jubbly bird kills"), but only the bow's check sees them, since only the chompy kill message is matched here.
 */
public final class ChompyHunt
{
	/** One hat, as Rantz gives it. */
	public static final class Hat
	{
		private final String name;
		private final int kills;
		private final int itemId;

		Hat(String name, int kills, int itemId)
		{
			this.name = name;
			this.kills = kills;
			this.itemId = itemId;
		}

		/** The wiki's name for the hat, such as "Ogre forester". */
		public String getName()
		{
			return name;
		}

		public int getKills()
		{
			return kills;
		}

		/** The game's item id (RuneLite gameval {@code ItemID.CBHAT1} to {@code CBHAT18}, matching the wiki). */
		public int getItemId()
		{
			return itemId;
		}
	}

	/**
	 * Every hat, fewest kills first: OSRS Wiki, Chompy bird hunting, "Chompy hats" table, with each hat's item id from
	 * its own wiki page (2978 to 2995, the same as RuneLite's gameval {@code CBHAT1} to {@code CBHAT18}).
	 */
	public static final Hat[] HATS = {
		new Hat("Ogre bowman", 30, 2978),
		new Hat("Bowman", 40, 2979),
		new Hat("Ogre yeoman", 50, 2980),
		new Hat("Yeoman", 70, 2981),
		new Hat("Ogre marksman", 95, 2982),
		new Hat("Marksman", 125, 2983),
		new Hat("Ogre woodsman", 170, 2984),
		new Hat("Woodsman", 225, 2985),
		new Hat("Ogre forester", 300, 2986),
		new Hat("Forester", 400, 2987),
		new Hat("Ogre bowmaster", 550, 2988),
		new Hat("Bowmaster", 700, 2989),
		new Hat("Ogre expert", 1000, 2990),
		new Hat("Expert", 1300, 2991),
		new Hat("Ogre dragon archer", 1700, 2992),
		new Hat("Dragon archer", 2250, 2993),
		new Hat("Expert ogre dragon archer", 3000, 2994),
		new Hat("Expert dragon archer", 4000, 2995),
	};

	/** The quest that opens chompy hunting (OSRS Wiki: "becomes available after completion of the quest"). */
	public static final String QUEST = "Big Chompy Bird Hunting";

	/**
	 * The Western Provinces Diary's chompy hat tasks, by tier: the kills each needs and the task as the game's diary
	 * lists it (OSRS Wiki, Western Provinces Diary, and RuneLite's WesternDiaryRequirement for the Elite one).
	 */
	public static final String[] DIARY_TIERS = {"Easy", "Medium", "Hard", "Elite"};
	public static final int[] DIARY_KILLS = {30, 125, 300, 1000};
	public static final String[] DIARY_TASKS = {
		"Claim any Chompy bird hat from Rantz.",
		"Claim a Chompy bird hat from Rantz after registering at least 125 kills.",
		"Claim a Chompy bird hat from Rantz after registering at least 300 kills.",
		"Claim a Chompy bird hat from Rantz after registering at least 1000 kills.",
	};

	/** The kill message (OSRS Wiki, Chompy bird). */
	static final String KILL_MESSAGE = "You scratch a notch on your bow for the chompy bird kill.";
	/**
	 * "Check kills" on the bow: "You've scratched up a total of 1,234 chompy bird kills so far!". Unconfirmed on the
	 * wiki; the wording is from the LostCity 2004Scape content scripts (ogre_bow.rs2), so it is matched loosely.
	 */
	private static final Pattern CHECK = Pattern.compile("scratched up a total of ([0-9][0-9,]*) chompy bird kills?",
		Pattern.CASE_INSENSITIVE);
	/** "Check kills" before the quest is done (OSRS Wiki, Ogre bow). */
	static final String NO_KILLS = "You've scratched up no kills yet!";

	/** A longer pause than this between two kills ends the hunt: the time between is not counted. */
	static final long HUNT_GAP_MS = 5 * 60_000;
	/** A rate needs this many kills timed before it is shown. */
	static final int MIN_KILLS = 10;
	/** And this many minutes of hunting. */
	static final double MIN_MINUTES = 10;
	/** Older hunting fades once this many hours are timed, so a better setup soon shows in the estimate. */
	static final double MEMORY_HOURS = 6;
	/** No real counter goes past the comp ogre bow's 100,000 notches (OSRS Wiki, Comp ogre bow). */
	static final int MAX_KILLS = 100_000;

	private ChompyHunt()
	{
	}

	/** The first hat whose kills are above {@code kills}, or null once every hat is reached. */
	public static Hat nextHat(int kills)
	{
		for (Hat hat : HATS)
		{
			if (kills < hat.kills)
			{
				return hat;
			}
		}
		return null;
	}

	/** The best hat {@code kills} reach, or null below the first. */
	public static Hat bestHat(int kills)
	{
		Hat best = null;
		for (Hat hat : HATS)
		{
			if (kills >= hat.kills)
			{
				best = hat;
			}
		}
		return best;
	}

	/** The hat with this name, any case, with or without a trailing " hat"; null for none. */
	public static Hat hat(String name)
	{
		if (name == null)
		{
			return null;
		}
		String n = name.trim().toLowerCase(Locale.ROOT);
		if (n.endsWith(" hat"))
		{
			n = n.substring(0, n.length() - 4).trim();
		}
		for (Hat hat : HATS)
		{
			if (hat.name.toLowerCase(Locale.ROOT).equals(n))
			{
				return hat;
			}
		}
		return null;
	}

	/** 1-based index of a hat in {@link #HATS}, or 0. */
	public static int rank(Hat hat)
	{
		for (int i = 0; i < HATS.length; i++)
		{
			if (HATS[i] == hat)
			{
				return i + 1;
			}
		}
		return 0;
	}

	/** What a chat line says about chompy kills. */
	public static final class Heard
	{
		/** True for one kill. */
		final boolean kill;
		/** The bow's total, or -1 when the line gave none. */
		final int total;

		Heard(boolean kill, int total)
		{
			this.kill = kill;
			this.total = total;
		}

		public boolean isKill()
		{
			return kill;
		}

		public int getTotal()
		{
			return total;
		}
	}

	/** Reads a game message with its tags already taken out; null when it is nothing to do with chompy kills. */
	public static Heard hear(String message)
	{
		if (message == null)
		{
			return null;
		}
		String m = message.trim();
		if (m.equals(KILL_MESSAGE))
		{
			return new Heard(true, -1);
		}
		if (m.startsWith(NO_KILLS))
		{
			return new Heard(false, 0);
		}
		Matcher matcher = CHECK.matcher(m);
		if (matcher.find())
		{
			try
			{
				int n = Integer.parseInt(matcher.group(1).replace(",", ""));
				return n >= 0 && n <= MAX_KILLS ? new Heard(false, n) : null;
			}
			catch (NumberFormatException e)
			{
				return null;
			}
		}
		return null;
	}

	/** An immutable read of the log, for the planner and panel on any thread. */
	public static final class View
	{
		public static final View NONE = new View(-1, 0, 0, 0, 0);

		private final int kills;
		private final int unsynced;
		private final double perHour;
		private final double hours;
		private final int timedKills;

		View(int kills, int unsynced, double perHour, double hours, int timedKills)
		{
			this.kills = kills;
			this.unsynced = unsynced;
			this.perHour = perHour;
			this.hours = hours;
			this.timedKills = timedKills;
		}

		/** For tests and fixtures: a known count and a learned rate (0 for none). */
		public static View of(int kills, double perHour, double hours)
		{
			return new View(kills, 0, perHour, hours, perHour > 0 ? MIN_KILLS : 0);
		}

		/** Kills seen before the bow was ever checked, with no rate learned. */
		public static View unsynced(int seen)
		{
			return new View(-1, seen, 0, 0, 0);
		}

		/** True once the bow's "Check kills" has given the total. */
		public boolean isKnown()
		{
			return kills >= 0;
		}

		/** The account's chompy kills, or -1 before the bow has been checked. */
		public int getKills()
		{
			return kills;
		}

		/** Kills seen while the total was still unknown; added in when the bow is checked. */
		public int getUnsynced()
		{
			return unsynced;
		}

		/** This player's kills an hour, or 0 until enough hunting has been timed. */
		public double getPerHour()
		{
			return perHour;
		}

		/** Hours of hunting the rate was learned from. */
		public double getHours()
		{
			return hours;
		}

		/** This view with {@code more} kills counted, the rate unchanged: for planning "what if". */
		public View plus(int more)
		{
			return isKnown() ? new View(Math.min(MAX_KILLS, kills + Math.max(0, more)), unsynced, perHour, hours, timedKills) : this;
		}

				/** Hours to reach {@code target} kills at the learned rate; 0 when reached or no rate is known. */
		public double hoursTo(int target)
		{
			if (!isKnown() || perHour <= 0 || kills >= target)
			{
				return 0;
			}
			return (target - kills) / perHour;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof View))
			{
				return false;
			}
			View v = (View) o;
			return kills == v.kills && unsynced == v.unsynced && perHour == v.perHour && hours == v.hours;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(kills, unsynced, perHour, hours);
		}
	}

	/**
	 * The count and the timing, per RuneScape profile. Changed on the client thread only; {@link #view()} hands out an
	 * immutable copy.
	 */
	public static final class Log
	{
		private int kills = -1;
		private int unsynced;
		/** Kills timed: each one that came within {@link #HUNT_GAP_MS} of the last. */
		private double timedKills;
		/** Seconds those kills took. */
		private double seconds;
		private long lastKill = -1;
		private boolean changed;

		/** Takes in a chat line; true when it changed anything. */
		public boolean heard(Heard heard, long now, boolean learn)
		{
			if (heard == null)
			{
				return false;
			}
			if (heard.kill)
			{
				kill(now, learn);
			}
			else
			{
				total(heard.total);
			}
			return true;
		}

		void kill(long now, boolean learn)
		{
			if (kills >= 0)
			{
				kills = Math.min(MAX_KILLS, kills + 1);
			}
			else
			{
				unsynced++;
			}
			if (learn && lastKill >= 0)
			{
				long gap = now - lastKill;
				if (gap > 0 && gap <= HUNT_GAP_MS)
				{
					timedKills++;
					seconds += gap / 1000.0;
					double memory = MEMORY_HOURS * 3600;
					if (seconds > memory)
					{
						double scale = memory / seconds;
						seconds *= scale;
						timedKills *= scale;
					}
				}
			}
			lastKill = learn ? now : -1;
			changed = true;
		}

		/**
		 * The bow's total. It already holds every kill made, the ones seen before it too, so those are dropped. A total
		 * lower than the count kept (the count was off) is taken as it stands: the game is right.
		 */
		void total(int total)
		{
			kills = total;
			unsynced = 0;
			changed = true;
		}

		/** Forgets when the last kill was, as after logging out or hopping: the time away is not hunting. */
		public void pause()
		{
			lastKill = -1;
		}

		public View view()
		{
			double hours = seconds / 3600;
			double rate = timedKills >= MIN_KILLS && seconds >= MIN_MINUTES * 60 ? timedKills / hours : 0;
			return new View(kills, unsynced, Math.round(rate * 10) / 10.0, Math.round(hours * 10) / 10.0,
				(int) Math.round(timedKills));
		}

		/** True once since the last call if anything changed; for deciding when to save. */
		public boolean takeChanged()
		{
			boolean value = changed;
			changed = false;
			return value;
		}

		/** The log in a form to save: {@code {"kills":..,"unsynced":..,"timedKills":..,"seconds":..}}. */
		public String toJson(Gson gson)
		{
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("kills", kills);
			out.put("unsynced", unsynced);
			out.put("timedKills", Math.round(timedKills * 100) / 100.0);
			out.put("seconds", Math.round(seconds));
			return gson.toJson(out);
		}

		/**
		 * Replaces the log with a saved copy; a missing, damaged or odd value leaves that part empty.
		 *
		 * @return how many of the four parts were read, for keeping a damaged value aside
		 */
		public int load(Gson gson, String json)
		{
			kills = -1;
			unsynced = 0;
			timedKills = 0;
			seconds = 0;
			lastKill = -1;
			changed = false;
			JsonElement root = Saved.tree(gson, json);
			if (root == null || !root.isJsonObject())
			{
				return 0;
			}
			JsonObject o = root.getAsJsonObject();
			int read = 0;
			double k = number(o, "kills");
			if (k >= -1 && k <= MAX_KILLS && k == Math.floor(k))
			{
				kills = (int) k;
				read++;
			}
			double u = number(o, "unsynced");
			if (u >= 0 && u <= MAX_KILLS && u == Math.floor(u))
			{
				unsynced = (int) u;
				read++;
			}
			double t = number(o, "timedKills");
			double s = number(o, "seconds");
			if (t >= 0 && s >= 0 && t <= MAX_KILLS && s <= MEMORY_HOURS * 3600 * 2)
			{
				timedKills = t;
				seconds = s;
				read += 2;
			}
			return read;
		}

		private static double number(JsonObject o, String key)
		{
			JsonElement e = o.get(key);
			if (e == null || !e.isJsonPrimitive())
			{
				return Double.NaN;
			}
			JsonPrimitive p = e.getAsJsonPrimitive();
			if (!p.isNumber())
			{
				return Double.NaN;
			}
			try
			{
				double d = p.getAsDouble();
				return Double.isInfinite(d) ? Double.NaN : d;
			}
			catch (NumberFormatException ex)
			{
				return Double.NaN;
			}
		}
	}
}
