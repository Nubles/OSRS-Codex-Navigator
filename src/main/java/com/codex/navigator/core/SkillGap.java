package com.codex.navigator.core;

import net.runelite.api.Experience;
import net.runelite.api.Skill;

/** A level a goal needs, and how far the account is from it. */
public final class SkillGap
{
	private final Skill skill;
	private final int current;
	private final int target;
	private final int currentXp;
	private final double pace;
	private final boolean boostable;

	public SkillGap(Skill skill, int current, int target, int currentXp)
	{
		this(skill, current, target, currentXp, 1, false);
	}

	/**
	 * @param pace how many times faster than the steady rates this player trains the skill (see {@link Pace})
	 * @param boostable true when every requirement behind this level may be met with the skill's everyday boost
	 *                  from the current level (see {@link Boosts}), so no training is needed for it
	 */
	public SkillGap(Skill skill, int current, int target, int currentXp, double pace, boolean boostable)
	{
		this.skill = skill;
		this.current = current;
		this.target = target;
		this.currentXp = currentXp;
		this.pace = pace;
		this.boostable = boostable && current < target;
	}

	/** True for an unmet level that a boost covers: it needs a pie or potion, not training. */
	public boolean isBoostable()
	{
		return boostable;
	}

	/** The pace the hours are worked out at; 1 for the steady rates. */
	public double getPace()
	{
		return pace;
	}

	public Skill getSkill()
	{
		return skill;
	}

	public int getCurrent()
	{
		return current;
	}

	public int getTarget()
	{
		return target;
	}

	public boolean isMet()
	{
		return current >= target;
	}

	public int xpToGo()
	{
		return Math.max(0, Experience.getXpForLevel(target) - currentXp);
	}

	/** About how long the gap takes to train at steady rates; see {@link Effort}. */
	public double hours()
	{
		return isMet() ? 0 : Effort.trainingHours(skill, currentXp, target, pace);
	}

	/** Progress toward the target measured in experience, so 98 of 99 reads as about 90%. */
	public double fraction()
	{
		if (isMet())
		{
			return 1;
		}
		int needed = Experience.getXpForLevel(target);
		return needed <= 0 ? 1 : Math.min(1, currentXp / (double) needed);
	}
}
