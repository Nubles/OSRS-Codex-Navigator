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

	public SkillGap(Skill skill, int current, int target, int currentXp)
	{
		this.skill = skill;
		this.current = current;
		this.target = target;
		this.currentXp = currentXp;
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
		return isMet() ? 0 : Effort.trainingHours(skill, currentXp, target);
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
