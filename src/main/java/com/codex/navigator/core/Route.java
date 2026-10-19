package com.codex.navigator.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * One route through every pinned goal at once. Each goal's path is laid over the others, so a quest or a
 * level that several goals share appears once, tagged with every goal it moves. The steps you can take
 * today come first, those shared by the most goals ahead of the rest, so an evening spent on the route
 * always moves as much as it can.
 */
public final class Route
{
	/** One thing to do, and the goals it moves. */
	public static final class Leg
	{
		private final Step step;
		private final List<Goal> serves;
		private final double hours;
		private final int target;

		Leg(Step step, List<Goal> serves, double hours, int target)
		{
			this.step = step;
			this.serves = Collections.unmodifiableList(serves);
			this.hours = hours;
			this.target = target;
		}

		/** The level a training step trains to; 0 for anything else. */
		public int getTarget()
		{
			return target;
		}

		public Step getStep()
		{
			return step;
		}

		/** The unfinished pinned goals this step moves, in pin order. */
		public List<Goal> getServes()
		{
			return serves;
		}

		/** About how long the step takes. */
		public double getHours()
		{
			return hours;
		}
	}

	private final List<Leg> legs;
	private final int blocked;
	private final int goals;

	private Route(List<Leg> legs, int blocked, int goals)
	{
		this.legs = Collections.unmodifiableList(legs);
		this.blocked = blocked;
		this.goals = goals;
	}

	/** Every step that can be taken now, best first. */
	public List<Leg> getLegs()
	{
		return legs;
	}

	/** Quests further down the route that are still waiting on something in {@link #getLegs()}. */
	public int getBlocked()
	{
		return blocked;
	}

	/** How many unfinished goals the route covers. */
	public int getGoals()
	{
		return goals;
	}

	public static Route build(Planner planner, List<Goal> pinned, AccountSnapshot account)
	{
		Map<String, Step> steps = new LinkedHashMap<>();
		Map<String, List<Goal>> serves = new LinkedHashMap<>();
		Map<String, Double> hours = new LinkedHashMap<>();
		Map<Skill, SkillGap> deepest = new EnumMap<>(Skill.class);
		Map<String, Boolean> waiting = new LinkedHashMap<>();
		Map<String, Integer> targets = new LinkedHashMap<>();
		int open = 0;
		for (Goal goal : pinned)
		{
			Plan plan = planner.plan(goal, account);
			if (plan.isComplete())
			{
				continue;
			}
			open++;
			for (Step step : plan.getPath())
			{
				if (step.getKind() != Step.Kind.QUEST || step.getStatus() == Step.Status.DONE)
				{
					continue;
				}
				String key = "q:" + step.getQuest();
				if (step.getStatus() == Step.Status.BLOCKED)
				{
					waiting.put(key, true);
					continue;
				}
				steps.putIfAbsent(key, step);
				add(serves, key, goal);
				double h = Effort.questHours(planner.getData().quest(step.getQuest()));
				hours.put(key, step.getStatus() == Step.Status.IN_PROGRESS ? h / 2 : h);
			}
			for (SkillGap gap : plan.getSkills())
			{
				// A level a boost covers is part of its quest's step, not something to train.
				if (gap.isMet() || gap.isBoostable())
				{
					continue;
				}
				String key = "s:" + gap.getSkill().name();
				SkillGap known = deepest.get(gap.getSkill());
				if (known == null || gap.getTarget() > known.getTarget())
				{
					deepest.put(gap.getSkill(), gap);
				}
				add(serves, key, goal);
			}
			// Quest points, a combat level or the diary's own tasks: whatever this goal needs that is no quest or level.
			Step next = plan.getNext();
			if (next != null && next.getKind() != Step.Kind.QUEST && next.getKind() != Step.Kind.TRAIN)
			{
				String key = "g:" + goal.key();
				steps.put(key, next);
				add(serves, key, goal);
				hours.put(key, 0.0);
			}
		}
		for (SkillGap gap : deepest.values())
		{
			String key = "s:" + gap.getSkill().name();
			steps.put(key, Planner.trainStep(gap));
			hours.put(key, gap.hours());
			targets.put(key, gap.getTarget());
		}

		List<Leg> legs = new ArrayList<>();
		for (Map.Entry<String, Step> e : steps.entrySet())
		{
			legs.add(new Leg(e.getValue(), serves.get(e.getKey()), hours.getOrDefault(e.getKey(), 0.0),
				targets.getOrDefault(e.getKey(), 0)));
		}
		Goal focus = pinned.isEmpty() ? null : pinned.get(0);
		legs.sort(Comparator.comparingInt(Route::urgency)
			.thenComparing(Comparator.comparingDouble((Leg l) -> worth(l, focus)).reversed())
			.thenComparing(l -> l.getStep().getTitle()));
		waiting.keySet().removeAll(steps.keySet());
		return new Route(legs, waiting.size(), open);
	}

	/**
	 * Goals moved per hour spent, the focus counting half a goal extra. A quest that moves two goals in half
	 * an hour beats twelve hours of training that moves three: both are needed, but one is progress tonight.
	 */
	static double worth(Leg leg, Goal focus)
	{
		double goals = leg.getServes().size() + (focus != null && leg.getServes().contains(focus) ? 0.5 : 0);
		// Quest points, a combat level or a diary's tasks have no estimate; count them as an hour, not as free.
		Step.Kind kind = leg.getStep().getKind();
		double hours = kind == Step.Kind.QUEST || kind == Step.Kind.TRAIN ? leg.getHours() : Math.max(1, leg.getHours());
		return goals / (hours + 0.5);
	}

	/** Quests already started come first: they are the cheapest progress there is. */
	private static int urgency(Leg leg)
	{
		return leg.getStep().getStatus() == Step.Status.IN_PROGRESS ? 0 : 1;
	}

	private static void add(Map<String, List<Goal>> serves, String key, Goal goal)
	{
		List<Goal> list = serves.computeIfAbsent(key, k -> new ArrayList<>());
		if (!list.contains(goal))
		{
			list.add(goal);
		}
	}
}
