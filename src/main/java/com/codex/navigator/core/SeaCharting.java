package com.codex.navigator.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Sea charting, the Sailing activity, as a collection: all 358 chart tasks with their kind, Sailing level and sea, which
 * of them this account has done, and what Navigator learns from the player's own charting (where each chart was made,
 * and how many charts an hour this player really makes). Facts and their sources are in the pull request that added
 * this; every one is quoted from the OSRS Wiki unless marked otherwise.
 *
 * <p>Which charts are done is the game's own: one varbit a task, {@code SAILING_CHARTING_..._COMPLETE} in RuneLite's
 * gameval, set to 1 once charted (that 1 means done is unconfirmed on the wiki; Quest Helper reads it the same way).
 *
 * <p>The wiki gives no coordinates for single tasks, so none are bundled. Each sea has a centre from the game cache
 * (unconfirmed: a cache dump, not the wiki), and once the player charts in a sea, the places they charted from stand in
 * for it, so "nearest" is measured from the player's own sailing.
 */
public final class SeaCharting
{
	/** A kind of chart task, with the tool it takes and the Sailing level that tool needs (OSRS Wiki, Sea charting, Tools). */
	public enum Kind
	{
		GENERIC("Generic", "Captain's log", 1),
		SPYGLASS("Spyglass", "Spyglass", 1),
		CRATE("Crate", "Crowbar", 12),
		CURRENT("Current", "Current duck", 22),
		DIVING("Diving", "Fishbowl helmet and diving apparatus", 38),
		WEATHER("Weather", "Portable weather station", 57);

		private final String label;
		private final String tool;
		private final int level;

		Kind(String label, String tool, int level)
		{
			this.label = label;
			this.tool = tool;
			this.level = level;
		}

		public String getLabel()
		{
			return label;
		}

		public String getTool()
		{
			return tool;
		}

		/** The Sailing level the tool needs; some tasks of the kind need more for their sea. */
		public int getLevel()
		{
			return level;
		}
	}

	/** A sea with chart tasks in it, in the game's own order of seas. */
	public static final class Sea
	{
		private final int index;
		private final String name;
		private final String ocean;
		private final int x;
		private final int y;

		Sea(int index, String name, String ocean, int x, int y)
		{
			this.index = index;
			this.name = name;
			this.ocean = ocean;
			this.x = x;
			this.y = y;
		}

		public int getIndex()
		{
			return index;
		}

		public String getName()
		{
			return name;
		}

		public String getOcean()
		{
			return ocean;
		}

		/** The centre of the sea from the game cache (unconfirmed: cache dump, not the wiki). */
		public int getX()
		{
			return x;
		}

		public int getY()
		{
			return y;
		}
	}

	/** One chart task. */
	public static final class Chart
	{
		private final int index;
		private final int varbit;
		private final Kind kind;
		private final int level;
		private final boolean levelConfirmed;
		private final Sea sea;
		private final String task;

		Chart(int index, int varbit, Kind kind, int level, boolean levelConfirmed, Sea sea, String task)
		{
			this.index = index;
			this.varbit = varbit;
			this.kind = kind;
			this.level = level;
			this.levelConfirmed = levelConfirmed;
			this.sea = sea;
			this.task = task;
		}

		/** Position in {@link #charts()}, 0 to 357. */
		public int getIndex()
		{
			return index;
		}

		/** The game's varbit for this task, 1 once charted. */
		public int getVarbit()
		{
			return varbit;
		}

		public Kind getKind()
		{
			return kind;
		}

		/** The Sailing level the wiki lists for this task. */
		public int getLevel()
		{
			return level;
		}

		/** False for the few levels the wiki leaves blank, taken from Quest Helper's chart list instead. */
		public boolean isLevelConfirmed()
		{
			return levelConfirmed;
		}

		public Sea getSea()
		{
			return sea;
		}

		/** The task as the wiki words it, which is the hint the Captain's log gives. */
		public String getTask()
		{
			return task;
		}
	}

	/** The name of the one charting goal. */
	public static final String GOAL_NAME = "Sea charting";
	/** Chart tasks in all (OSRS Wiki: "full completion of all 358 charting tasks"). */
	public static final int TOTAL = 358;
	/** Sailing needed for every task (OSRS Wiki: "Completing every charting task requires 78 Sailing"). */
	public static final int FULL_LEVEL = 78;
	/** The bonus for charting everything (OSRS Wiki: "grants an additional 100,000 experience"). */
	public static final int FULL_BONUS_XP = 100_000;
	/** Experience from the tasks themselves (OSRS Wiki: "a total of 691,270 Sailing experience from these tasks"). */
	public static final int TASK_XP = 691_270;
	/** Who pays out the ocean bonuses (OSRS Wiki: "must be claimed from Chartin' Charles McAtless in the Pandemonium Cave"). */
	public static final String CHARTS_NPC = "Chartin' Charles McAtless";

	/** A longer pause than this between two charts ends a charting trip: the time between is not counted. */
	static final long TRIP_GAP_MS = 20 * 60_000;
	/** A rate needs this many charts timed before it is shown. */
	static final int MIN_CHARTS = 5;
	/** And this many minutes of charting. */
	static final double MIN_MINUTES = 15;
	/** Older charting fades once this many hours are timed, so a better route soon shows in the estimate. */
	static final double MEMORY_HOURS = 8;
	/** World tiles a place may lie at; anything outside is damage. */
	static final int MAX_COORD = 16_384;

	private static final Data DATA = Data.load();

	private SeaCharting()
	{
	}

	/** Every chart task, in the game's order (by varbit). Empty only if the bundled list could not be read. */
	public static List<Chart> charts()
	{
		return DATA.charts;
	}

	/** Every sea with charts, in the game's order. */
	public static List<Sea> seas()
	{
		return DATA.seas;
	}

	/** The oceans, in the order their first sea comes. */
	public static List<String> oceans()
	{
		return DATA.oceans;
	}

	/** The chart with this varbit, or null. */
	public static Chart byVarbit(int varbit)
	{
		return DATA.byVarbit.get(varbit);
	}

	/** Charts of one kind in all. */
	public static int total(Kind kind)
	{
		int n = 0;
		for (Chart c : DATA.charts)
		{
			if (c.kind == kind)
			{
				n++;
			}
		}
		return n;
	}

	/** One of the nearest unfinished charts, with how far its sea lies, in tiles, or -1 when that is not known. */
	public static final class Near
	{
		private final Chart chart;
		private final int distance;
		private final boolean learned;

		Near(Chart chart, int distance, boolean learned)
		{
			this.chart = chart;
			this.distance = distance;
			this.learned = learned;
		}

		public Chart getChart()
		{
			return chart;
		}

		public int getDistance()
		{
			return distance;
		}

		/** True when the distance is to where the player charted in that sea before, rather than its centre. */
		public boolean isLearned()
		{
			return learned;
		}
	}

	/** An immutable read of this account's charting, for the planner and panel on any thread. */
	public static final class View
	{
		public static final View NONE = new View(false, new BitSet(), Collections.emptyMap(), 0, 0, 0, false, 0, 0);

		private final boolean known;
		private final BitSet done;
		private final Map<Integer, int[]> places;
		private final double perHour;
		private final double hours;
		private final int timed;
		private final boolean hasWhere;
		private final int whereX;
		private final int whereY;

		View(boolean known, BitSet done, Map<Integer, int[]> places, double perHour, double hours, int timed,
			boolean hasWhere, int whereX, int whereY)
		{
			this.known = known;
			this.done = (BitSet) done.clone();
			this.places = Collections.unmodifiableMap(new HashMap<>(places));
			this.perHour = perHour;
			this.hours = hours;
			this.timed = timed;
			this.hasWhere = hasWhere;
			this.whereX = whereX;
			this.whereY = whereY;
		}

		/** For tests and fixtures: these charts done (by index), a learned rate (0 for none), and where the player is. */
		public static View of(BitSet done, double perHour, double hours, int x, int y)
		{
			return new View(true, done, Collections.emptyMap(), perHour, hours, perHour > 0 ? MIN_CHARTS : 0,
				x > 0 || y > 0, x, y);
		}

		/** True once the game's varbits have been read: logged in. */
		public boolean isKnown()
		{
			return known;
		}

		public boolean isDone(Chart chart)
		{
			return chart != null && done.get(chart.index);
		}

		public int doneCount()
		{
			return done.cardinality();
		}

		public int doneOf(Kind kind)
		{
			int n = 0;
			for (Chart c : DATA.charts)
			{
				if (c.kind == kind && done.get(c.index))
				{
					n++;
				}
			}
			return n;
		}

		/** {done, total} for each sea, by sea index. */
		public int[][] bySea()
		{
			int[][] out = new int[DATA.seas.size()][2];
			for (Chart c : DATA.charts)
			{
				out[c.sea.index][1]++;
				if (done.get(c.index))
				{
					out[c.sea.index][0]++;
				}
			}
			return out;
		}

		/** {done, total} for each ocean, in {@link #oceans()} order. */
		public int[][] byOcean()
		{
			int[][] out = new int[DATA.oceans.size()][2];
			for (Chart c : DATA.charts)
			{
				int o = DATA.oceans.indexOf(c.sea.ocean);
				out[o][1]++;
				if (done.get(c.index))
				{
					out[o][0]++;
				}
			}
			return out;
		}

		/** Unfinished charts a player at {@code level} Sailing can take on. */
		public int openAt(int level)
		{
			int n = 0;
			for (Chart c : DATA.charts)
			{
				if (!done.get(c.index) && c.level <= level)
				{
					n++;
				}
			}
			return n;
		}

		/** The lowest Sailing level above {@code level} that opens an unfinished chart, or 0 when none does. */
		public int nextLevel(int level)
		{
			int best = 0;
			for (Chart c : DATA.charts)
			{
				if (!done.get(c.index) && c.level > level && (best == 0 || c.level < best))
				{
					best = c.level;
				}
			}
			return best;
		}

		/** Where the player charted this task from, or null when Navigator did not see it charted. */
		public int[] placeOf(Chart chart)
		{
			int[] p = chart == null ? null : places.get(chart.varbit);
			return p == null ? null : p.clone();
		}

		/** How many charts Navigator has seen made, and so knows the place of. */
		public int placesLearned()
		{
			return places.size();
		}

		/**
		 * Where a sea is, for "nearest": the middle of the places the player charted from in it when there are any,
		 * else its centre from the game cache. The second value of the pair says which.
		 */
		int[] anchor(Sea sea)
		{
			long sx = 0;
			long sy = 0;
			int n = 0;
			for (Map.Entry<Integer, int[]> e : places.entrySet())
			{
				Chart c = DATA.byVarbit.get(e.getKey());
				if (c != null && c.sea == sea)
				{
					sx += e.getValue()[0];
					sy += e.getValue()[1];
					n++;
				}
			}
			return n > 0 ? new int[]{(int) (sx / n), (int) (sy / n), 1} : new int[]{sea.x, sea.y, 0};
		}

		/**
		 * The nearest unfinished charts that {@code level} Sailing allows: by distance from where the player is to the
		 * chart's sea, then lower level first. With no position known, the game's order of seas.
		 */
		public List<Near> nearest(int level, int max)
		{
			Map<Sea, int[]> anchors = new HashMap<>();
			List<Near> out = new ArrayList<>();
			for (Chart c : DATA.charts)
			{
				if (done.get(c.index) || c.level > level)
				{
					continue;
				}
				int[] a = anchors.computeIfAbsent(c.sea, this::anchor);
				int d = hasWhere ? (int) Math.round(Math.hypot(a[0] - whereX, a[1] - whereY)) : -1;
				out.add(new Near(c, d, a[2] == 1));
			}
			out.sort(Comparator.comparingInt((Near n) -> n.distance)
				.thenComparingInt(n -> n.chart.sea.index)
				.thenComparingInt(n -> n.chart.level)
				.thenComparingInt(n -> n.chart.index));
			return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
		}

		/** This player's charts an hour, or 0 until enough charting has been timed. */
		public double getPerHour()
		{
			return perHour;
		}

		/** Hours of charting the rate was learned from. */
		public double getHours()
		{
			return hours;
		}

		/** Charts timed for the rate. */
		public int getTimed()
		{
			return timed;
		}

		/** Hours to chart everything still left at the learned rate; 0 when done or no rate is known. */
		public double hoursLeft()
		{
			int left = TOTAL - doneCount();
			return perHour <= 0 || left <= 0 ? 0 : left / perHour;
		}

		public boolean hasWhere()
		{
			return hasWhere;
		}

		public int getWhereX()
		{
			return whereX;
		}

		public int getWhereY()
		{
			return whereY;
		}

		/** This view seen from somewhere else; for the plugin as the player sails. */
		public View at(boolean has, int x, int y)
		{
			return new View(known, done, places, perHour, hours, timed, has, x, y);
		}

		/** Everything but where the player stands, which changes as they sail and is throttled apart. */
		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof View))
			{
				return false;
			}
			View v = (View) o;
			return known == v.known && done.equals(v.done) && places.size() == v.places.size() && perHour == v.perHour
				&& hours == v.hours;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(known, done, places.size(), perHour, hours);
		}

		/** Where the player is, for the part of the snapshot that may lag behind. */
		public int whereSignature()
		{
			return Objects.hash(hasWhere, whereX, whereY);
		}
	}

	/** Reads the done state from the game's varbits through {@code varbit}, which returns a varbit's value. */
	public static BitSet read(java.util.function.IntUnaryOperator varbit)
	{
		BitSet done = new BitSet(TOTAL);
		for (Chart c : DATA.charts)
		{
			if (varbit.applyAsInt(c.varbit) == 1)
			{
				done.set(c.index);
			}
		}
		return done;
	}

	/**
	 * What Navigator learns from the player's charting, per RuneScape profile: where each chart was made, and the time
	 * between charts. Changed on the client thread only; {@link #view} hands out an immutable copy.
	 */
	public static final class Log
	{
		private final Map<Integer, int[]> places = new TreeMap<>();
		private double timed;
		private double seconds;
		private long last = -1;
		private boolean changed;

		/**
		 * A chart the player just made (its varbit went to 1 while they played): where they were, and the time since
		 * the last one when the trip goes on.
		 *
		 * @param where the player's world tile {x, y}, or null when not known
		 */
		public void charted(Chart chart, long now, int[] where, boolean learn)
		{
			if (chart == null)
			{
				return;
			}
			if (where != null && valid(where[0], where[1]) && !places.containsKey(chart.varbit))
			{
				places.put(chart.varbit, new int[]{where[0], where[1]});
				changed = true;
			}
			if (learn && last >= 0)
			{
				long gap = now - last;
				if (gap > 0 && gap <= TRIP_GAP_MS)
				{
					timed++;
					seconds += gap / 1000.0;
					double memory = MEMORY_HOURS * 3600;
					if (seconds > memory)
					{
						double scale = memory / seconds;
						seconds *= scale;
						timed *= scale;
					}
					changed = true;
				}
			}
			last = learn ? now : -1;
		}

		/** Forgets when the last chart was, as after logging out or hopping: the time away is not charting. */
		public void pause()
		{
			last = -1;
		}

		/** The log with the game's done state and where the player is now. */
		public View view(boolean known, BitSet done, boolean hasWhere, int x, int y)
		{
			double hours = seconds / 3600;
			double rate = timed >= MIN_CHARTS && seconds >= MIN_MINUTES * 60 ? timed / hours : 0;
			return new View(known, done, places, Math.round(rate * 10) / 10.0, Math.round(hours * 10) / 10.0,
				(int) Math.round(timed), hasWhere, x, y);
		}

		/** True once since the last call if anything changed; for deciding when to save. */
		public boolean takeChanged()
		{
			boolean value = changed;
			changed = false;
			return value;
		}

		/** The log in a form to save: {@code {"places":{"18574":[3027,3194],..},"timed":..,"seconds":..}}. */
		public String toJson(Gson gson)
		{
			Map<String, Object> out = new LinkedHashMap<>();
			Map<String, int[]> p = new LinkedHashMap<>();
			places.forEach((k, v) -> p.put(String.valueOf(k), v));
			out.put("places", p);
			out.put("timed", Math.round(timed * 100) / 100.0);
			out.put("seconds", Math.round(seconds));
			return gson.toJson(out);
		}

		/**
		 * Replaces the log with a saved copy; a missing, damaged or odd value leaves that part empty, and an odd place
		 * is skipped without losing the others.
		 *
		 * @return 2 when both parts (places, timing) were read in full, less when part was passed over
		 */
		public int load(Gson gson, String json)
		{
			places.clear();
			timed = 0;
			seconds = 0;
			last = -1;
			changed = false;
			JsonElement root = Saved.tree(gson, json);
			if (root == null || !root.isJsonObject())
			{
				return 0;
			}
			JsonObject o = root.getAsJsonObject();
			int read = 0;
			JsonElement p = o.get("places");
			if (p != null && p.isJsonObject())
			{
				boolean all = true;
				for (Map.Entry<String, JsonElement> e : p.getAsJsonObject().entrySet())
				{
					int[] xy = place(e.getValue());
					Integer varbit = varbit(e.getKey());
					if (xy == null || varbit == null || !DATA.byVarbit.containsKey(varbit))
					{
						all = false;
						continue;
					}
					places.put(varbit, xy);
				}
				read += all ? 1 : 0;
			}
			double t = number(o.get("timed"));
			double s = number(o.get("seconds"));
			if (t >= 0 && s >= 0 && t <= TOTAL * 10 && s <= MEMORY_HOURS * 3600 * 2)
			{
				timed = t;
				seconds = s;
				read++;
			}
			return read;
		}

		private static Integer varbit(String key)
		{
			try
			{
				return Integer.valueOf(key);
			}
			catch (NumberFormatException e)
			{
				return null;
			}
		}

		private static int[] place(JsonElement e)
		{
			if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() != 2)
			{
				return null;
			}
			double x = number(e.getAsJsonArray().get(0));
			double y = number(e.getAsJsonArray().get(1));
			if (x != Math.floor(x) || y != Math.floor(y) || !valid((int) x, (int) y))
			{
				return null;
			}
			return new int[]{(int) x, (int) y};
		}

		private static double number(JsonElement e)
		{
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

	static boolean valid(int x, int y)
	{
		return x > 0 && y > 0 && x < MAX_COORD && y < MAX_COORD;
	}

	/** The bundled list, read once. */
	private static final class Data
	{
		final List<Chart> charts;
		final List<Sea> seas;
		final List<String> oceans;
		final Map<Integer, Chart> byVarbit;

		private Data(List<Chart> charts, List<Sea> seas, List<String> oceans)
		{
			this.charts = Collections.unmodifiableList(charts);
			this.seas = Collections.unmodifiableList(seas);
			this.oceans = Collections.unmodifiableList(oceans);
			Map<Integer, Chart> map = new HashMap<>();
			for (Chart c : charts)
			{
				map.put(c.varbit, c);
			}
			this.byVarbit = Collections.unmodifiableMap(map);
		}

		static Data load()
		{
			List<Sea> seas = new ArrayList<>();
			List<String> oceans = new ArrayList<>();
			Map<String, Sea> seaByName = new HashMap<>();
			List<Chart> charts = new ArrayList<>();
			try
			{
				for (String[] row : rows("/com/codex/navigator/seas.tsv", 4))
				{
					Sea sea = new Sea(seas.size(), row[0], row[1], Integer.parseInt(row[2]), Integer.parseInt(row[3]));
					seas.add(sea);
					seaByName.put(sea.name, sea);
					if (!oceans.contains(sea.ocean))
					{
						oceans.add(sea.ocean);
					}
				}
				for (String[] row : rows("/com/codex/navigator/sea-charts.tsv", 6))
				{
					Sea sea = seaByName.get(row[4]);
					Kind kind = Kind.valueOf(row[1]);
					if (sea == null)
					{
						throw new IOException("no sea " + row[4]);
					}
					charts.add(new Chart(charts.size(), Integer.parseInt(row[0]), kind, Integer.parseInt(row[2]),
						"wiki".equals(row[3]), sea, row[5]));
				}
			}
			catch (IOException | RuntimeException e)
			{
				return new Data(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
			}
			return new Data(charts, seas, oceans);
		}

		private static List<String[]> rows(String resource, int columns) throws IOException
		{
			List<String[]> out = new ArrayList<>();
			try (InputStream in = SeaCharting.class.getResourceAsStream(resource))
			{
				if (in == null)
				{
					throw new IOException("missing " + resource);
				}
				BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
				String line;
				while ((line = reader.readLine()) != null)
				{
					if (line.isEmpty() || line.startsWith("#"))
					{
						continue;
					}
					String[] cells = line.split("\t", -1);
					if (cells.length != columns)
					{
						throw new IOException("bad row in " + resource);
					}
					out.add(cells);
				}
			}
			return out;
		}
	}
}
