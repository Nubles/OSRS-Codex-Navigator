package com.codex.navigator.core;

import java.util.Collections;
import java.util.List;

/** The planner's answer for one goal: how far along it is, the path, and the single next step. */
public final class Plan
{
	private final Goal goal;
	private final String title;
	private final String subtitle;
	private final boolean complete;
	private final double progress;
	private final List<Step> path;
	private final List<SkillGap> skills;
	private final Step next;
	private final int questsDone;
	private final int questsTotal;
	private final int questPointTarget;
	private final int combatTarget;

	public Plan(Goal goal, String title, String subtitle, boolean complete, double progress, List<Step> path,
		List<SkillGap> skills, Step next, int questsDone, int questsTotal)
	{
		this(goal, title, subtitle, complete, progress, path, skills, next, questsDone, questsTotal, 0, 0);
	}

	public Plan(Goal goal, String title, String subtitle, boolean complete, double progress, List<Step> path,
		List<SkillGap> skills, Step next, int questsDone, int questsTotal, int questPointTarget, int combatTarget)
	{
		this.questPointTarget = questPointTarget;
		this.combatTarget = combatTarget;
		this.goal = goal;
		this.title = title;
		this.subtitle = subtitle;
		this.complete = complete;
		this.progress = progress;
		this.path = Collections.unmodifiableList(path);
		this.skills = Collections.unmodifiableList(skills);
		this.next = next;
		this.questsDone = questsDone;
		this.questsTotal = questsTotal;
	}

	public Goal getGoal()
	{
		return goal;
	}

	public String getTitle()
	{
		return title;
	}

	public String getSubtitle()
	{
		return subtitle;
	}

	public boolean isComplete()
	{
		return complete;
	}

	/** 0 to 1. */
	public double getProgress()
	{
		return progress;
	}

	/** Quests and checks in the order they can be done, prerequisites first. */
	public List<Step> getPath()
	{
		return path;
	}

	/** Every level the goal needs, unmet ones first. */
	public List<SkillGap> getSkills()
	{
		return skills;
	}

	/** The one thing to do next, or null when the goal is complete. */
	public Step getNext()
	{
		return next;
	}

	public int getQuestsDone()
	{
		return questsDone;
	}

	public int getQuestsTotal()
	{
		return questsTotal;
	}

	/** The quest points still needed somewhere on the path, or 0 when nothing asks for any. */
	public int getQuestPointTarget()
	{
		return questPointTarget;
	}

	/** The combat level still needed somewhere on the path, or 0 when nothing asks for one. */
	public int getCombatTarget()
	{
		return combatTarget;
	}

	/**
	 * True for a diary whose every listed requirement is met: only the in-game tasks remain, which the
	 * client does not expose, so its progress is an estimate rather than a measured percentage.
	 */
	public boolean isAwaitingTasks()
	{
		return !complete && next != null && next.getKind() == Step.Kind.DIARY_TASKS;
	}
}
