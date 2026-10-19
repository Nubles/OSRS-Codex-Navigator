package com.codex.navigator.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The pinned goals as other plugins see them. The plugin saves this, as JSON, under the RuneScape profile
 * key {@code codexnavigator.goalBoard}, so a companion plugin (another Codex plugin, say) can show or plan
 * around what the player is working toward without knowing anything about quests or diaries.
 *
 * <p>The shape is a contract: fields are only ever added, never renamed or removed, and {@link #version}
 * goes up only if that promise ever has to be broken. Numbers are rounded so the board changes when
 * something a reader would notice changes, not with every point of experience.
 */
public final class GoalBoard
{
	public static final int VERSION = 1;

	/** One pinned goal. */
	public static final class Entry
	{
		/** The same key as the {@code goals} list, such as {@code quest:Dragon Slayer II} or {@code diary:Ardougne Easy}. */
		String key;
		/** quest, diary or cape. */
		String type;
		String title;
		boolean complete;
		/** 0 to 1, two decimals. */
		double progress;
		/** The next step in plain words, such as "Train Agility to 60"; absent when complete. */
		String next;
		/** About how many hours of training and questing are left, to the half hour. */
		double hoursLeft;
		/** Unmet levels the goal needs, by skill name ("Agility": 60). */
		Map<String, Integer> skills = new LinkedHashMap<>();
		/** Unfinished quests on the way, prerequisites first, the goal's own quest last. */
		List<String> quests = new ArrayList<>();
	}

	int version = VERSION;
	/** The focus goal's key, or absent when nothing is pinned. */
	String focus;
	List<Entry> goals = new ArrayList<>();

	public static GoalBoard of(Planner planner, List<Goal> pinned, AccountSnapshot account)
	{
		GoalBoard board = new GoalBoard();
		board.focus = pinned.isEmpty() ? null : pinned.get(0).key();
		for (Goal goal : pinned)
		{
			Plan plan = planner.plan(goal, account);
			Entry e = new Entry();
			e.key = goal.key();
			e.type = goal.getType() == Goal.Type.QUEST ? "quest" : goal.getType() == Goal.Type.DIARY ? "diary" : "cape";
			e.title = plan.getTitle();
			e.complete = plan.isComplete();
			e.progress = Math.round(plan.getProgress() * 100) / 100.0;
			e.hoursLeft = Math.round(plan.getHoursLeft() * 2) / 2.0;
			Step next = plan.getNext();
			if (next != null)
			{
				e.next = next.getKind() == Step.Kind.QUEST
					? (next.getStatus() == Step.Status.IN_PROGRESS ? "Continue " : "Start ") + next.getTitle()
					: next.getTitle();
			}
			for (SkillGap gap : plan.getSkills())
			{
				if (!gap.isMet())
				{
					e.skills.put(gap.getSkill().getName(), gap.getTarget());
				}
			}
			for (Step step : plan.getPath())
			{
				if (step.getKind() == Step.Kind.QUEST && step.getStatus() != Step.Status.DONE)
				{
					e.quests.add(step.getQuest());
				}
			}
			board.goals.add(e);
		}
		return board;
	}

	public String getFocus()
	{
		return focus;
	}

	public int size()
	{
		return goals.size();
	}

	public Entry get(int i)
	{
		return goals.get(i);
	}

	public String key(int i)
	{
		return goals.get(i).key;
	}

	public double hoursLeft(int i)
	{
		return goals.get(i).hoursLeft;
	}

	public Map<String, Integer> skills(int i)
	{
		return goals.get(i).skills;
	}

	public String next(int i)
	{
		return goals.get(i).next;
	}
}
