package com.codex.navigator.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * How fast this player really trains each skill, learned from the experience they gain while the plugin runs.
 *
 * <p>Experience is measured against what {@link Effort}'s steady rates would have given in the same time at the
 * same level, so a player who trains Agility 40% faster than the table reads as 1.4x at every level, and the
 * table's shape (slow at first, quicker later) still holds. Only time spent actively training counts: a drop
 * more than {@link #BOUT_GAP_MS} after the previous one in that skill starts a fresh bout, and the gap is not
 * counted. Lumps far beyond any training method (quest rewards, lamps, tomes) are ignored.
 *
 * <p>Older play fades: once a skill holds {@link #MEMORY_HOURS} of table-rate experience, every sample is scaled
 * down so new training keeps moving the estimate. A skill needs {@link #MIN_MINUTES} of training before it
 * counts at all, and reaches full weight after an hour.
 */
public final class Pace
{
	/** A longer pause than this between drops in one skill ends the bout. */
	static final long BOUT_GAP_MS = 150_000;
	/** A drop more than this many times the table's rate for the time since the last one is a lump, not training. */
	static final double LUMP_RATIO = 25;
	/** Drops below this much experience are never treated as lumps, however quick. */
	static final int LUMP_MIN_XP = 1_500;
	static final double MIN_MINUTES = 15;
	static final double FULL_WEIGHT_MINUTES = 60;
	static final double MEMORY_HOURS = 5;
	static final double MIN_FACTOR = 0.25;
	static final double MAX_FACTOR = 4;

	/** What has been learned about one skill. */
	static final class Sample
	{
		/** Experience gained while training. */
		double xp;
		/** What the steady rates would have given over the same time at the same levels. */
		double expected;
		/** Time spent training, in seconds. */
		double seconds;
	}

	private final Map<Skill, Sample> samples = new EnumMap<>(Skill.class);
	private final Map<Skill, long[]> last = new EnumMap<>(Skill.class);
	private boolean changed;

	/**
	 * Records the skill's experience as read now. The first read of a skill (each login or world hop) only sets a
	 * starting point.
	 */
	public void observe(Skill skill, int xp, long now)
	{
		long[] previous = last.get(skill);
		if (previous != null && xp == previous[1])
		{
			// A boosted or drained level changes the stat without any experience; the bout's clock must not move.
			return;
		}
		last.put(skill, new long[]{now, xp});
		if (previous == null)
		{
			return;
		}
		long gap = now - previous[0];
		long gained = xp - previous[1];
		if (gained <= 0 || gap <= 0 || gap > BOUT_GAP_MS)
		{
			return;
		}
		int level = Experience.getLevelForXp((int) Math.max(0, previous[1]));
		double seconds = gap / 1000.0;
		double expected = Effort.xpPerHour(skill, level) * seconds / 3600;
		if (gained > LUMP_MIN_XP && gained > expected * LUMP_RATIO)
		{
			return;
		}
		Sample sample = samples.computeIfAbsent(skill, k -> new Sample());
		sample.xp += gained;
		sample.expected += expected;
		sample.seconds += seconds;
		double memory = MEMORY_HOURS * Effort.xpPerHour(skill, level);
		if (sample.expected > memory)
		{
			double scale = memory / sample.expected;
			sample.xp *= scale;
			sample.expected *= scale;
			sample.seconds *= scale;
		}
		changed = true;
	}

	/** Forgets the starting points, as after logging out or hopping, so the gap is never counted as training. */
	public void pause()
	{
		last.clear();
	}

	/**
	 * How many times faster than the steady rates this player trains the skill: 1 until there is enough
	 * training to say, then moving from 1 toward the measured figure over the first hour.
	 */
	public double factor(Skill skill)
	{
		Sample sample = samples.get(skill);
		if (sample == null || sample.seconds < MIN_MINUTES * 60 || sample.expected <= 0)
		{
			return 1;
		}
		double measured = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, sample.xp / sample.expected));
		double weight = Math.min(1, sample.seconds / (FULL_WEIGHT_MINUTES * 60));
		return 1 + (measured - 1) * weight;
	}

	/** Hours of training learned from, for the panel ("learned from 2.5 h"). */
	public double hours(Skill skill)
	{
		Sample sample = samples.get(skill);
		return sample == null ? 0 : sample.seconds / 3600;
	}

	/** The factor of every skill with enough training to count, rounded to two decimals so tiny drifts stay quiet. */
	public Map<Skill, Double> factors()
	{
		Map<Skill, Double> out = new EnumMap<>(Skill.class);
		for (Skill skill : samples.keySet())
		{
			double f = factor(skill);
			if (f != 1)
			{
				out.put(skill, Math.round(f * 100) / 100.0);
			}
		}
		return Collections.unmodifiableMap(out);
	}

	/** The hours learned from, for every skill in {@link #factors()}, to the tenth of an hour. */
	public Map<Skill, Double> hoursLearned()
	{
		Map<Skill, Double> out = new EnumMap<>(Skill.class);
		for (Skill skill : factors().keySet())
		{
			out.put(skill, Math.round(hours(skill) * 10) / 10.0);
		}
		return Collections.unmodifiableMap(out);
	}

	/** True once since the last call if anything new was learned; for deciding when to save. */
	public boolean takeChanged()
	{
		boolean value = changed;
		changed = false;
		return value;
	}

	/** The samples in a form Gson can save: skill name to {xp, expected, seconds}, rounded. */
	public Map<String, double[]> toSaved()
	{
		Map<String, double[]> out = new LinkedHashMap<>();
		for (Map.Entry<Skill, Sample> e : samples.entrySet())
		{
			Sample s = e.getValue();
			out.put(e.getKey().name(), new double[]{Math.round(s.xp), Math.round(s.expected), Math.round(s.seconds)});
		}
		return out;
	}

	/** Replaces everything learned with a saved copy; unknown skills and malformed rows are skipped. */
	public void load(Map<String, double[]> saved)
	{
		samples.clear();
		last.clear();
		changed = false;
		if (saved == null)
		{
			return;
		}
		for (Map.Entry<String, double[]> e : saved.entrySet())
		{
			Skill skill = Planner.skill(e.getKey() == null ? "" : e.getKey());
			double[] row = e.getValue();
			if (skill == null || row == null || row.length < 3 || !(row[0] >= 0 && row[1] > 0 && row[2] >= 0)
				|| Double.isInfinite(row[0]) || Double.isInfinite(row[1]) || Double.isInfinite(row[2]))
			{
				continue;
			}
			Sample s = new Sample();
			s.xp = row[0];
			s.expected = row[1];
			s.seconds = row[2];
			samples.put(skill, s);
		}
	}
}
