package com.codex.navigator.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The road to a goal as the Compass draws it: the quests on the way as stops, the few just behind you, the one to do
 * next, and what follows, with the goal itself as the flag at the end. A long road (the quest cape has two hundred
 * stops) keeps only a window around where you stand and counts the rest, so it always fits one card.
 */
public final class Road
{
	public enum State
	{
		DONE,
		/** The stop to make next: the first quest under way, or else the first that can be started. */
		NEXT,
		READY,
		BLOCKED
	}

	/** One quest on the road. */
	public static final class Stop
	{
		private final String quest;
		private final State state;
		private final String hazard;
		private final boolean started;

		Stop(String quest, State state, String hazard, boolean started)
		{
			this.quest = quest;
			this.state = state;
			this.hazard = hazard;
			this.started = started;
		}

		/** True for a quest already under way. */
		public boolean isStarted()
		{
			return started;
		}

		public String getQuest()
		{
			return quest;
		}

		public State getState()
		{
			return state;
		}

		/** "Boss" or "Wildy" on a one-life account when the quest asks for that risk, otherwise null. */
		public String getHazard()
		{
			return hazard;
		}
	}

	/** Done stops kept on the road behind the next one. */
	static final int BEHIND = 2;

	private final List<Stop> stops;
	private final int doneBefore;
	private final int moreAfter;
	private final int done;
	private final int total;

	private Road(List<Stop> stops, int doneBefore, int moreAfter, int done, int total)
	{
		this.stops = Collections.unmodifiableList(stops);
		this.doneBefore = doneBefore;
		this.moreAfter = moreAfter;
		this.done = done;
		this.total = total;
	}

	/**
	 * @param plan     the goal's plan
	 * @param oneLife  whether risky quests are marked, as on the rest of the Compass
	 * @param maxStops the most stops drawn, at least 3
	 * @return the road, or null when the goal has fewer than two quests on its way (a level goal, a lone quest)
	 */
	public static Road of(Plan plan, boolean oneLife, int maxStops)
	{
		if (plan == null || plan.isComplete())
		{
			return null;
		}
		maxStops = Math.max(3, maxStops);
		String goalQuest = plan.getGoal() != null && plan.getGoal().getType() == Goal.Type.QUEST ? plan.getGoal().getName() : null;
		List<Step> quests = new ArrayList<>();
		for (Step step : plan.getPath())
		{
			// The goal's own quest is the flag, not a stop.
			if (step.getKind() == Step.Kind.QUEST && step.getQuest() != null && !step.getQuest().equals(goalQuest))
			{
				quests.add(step);
			}
		}
		if (quests.size() < 2 && !(goalQuest != null && quests.size() == 1))
		{
			return null;
		}

		// The planner's own next step leads when it is a quest on the road, so the road and the focus card agree.
		int next = -1;
		Step planned = plan.getNext();
		for (int i = 0; i < quests.size() && next < 0 && planned != null && planned.getKind() == Step.Kind.QUEST; i++)
		{
			if (quests.get(i).getQuest().equals(planned.getQuest()) && quests.get(i).getStatus() != Step.Status.DONE)
			{
				next = i;
			}
		}
		for (int i = 0; i < quests.size() && next < 0; i++)
		{
			if (quests.get(i).getStatus() == Step.Status.IN_PROGRESS)
			{
				next = i;
			}
		}
		for (int i = 0; i < quests.size() && next < 0; i++)
		{
			if (quests.get(i).getStatus() == Step.Status.READY)
			{
				next = i;
			}
		}
		for (int i = 0; i < quests.size() && next < 0; i++)
		{
			if (quests.get(i).getStatus() != Step.Status.DONE)
			{
				next = i;
			}
		}

		int done = 0;
		for (Step s : quests)
		{
			if (s.getStatus() == Step.Status.DONE)
			{
				done++;
			}
		}

		// The window: a couple of finished stops for a sense of the road travelled, the next one, then what follows.
		List<Integer> behind = new ArrayList<>();
		int anchor = next < 0 ? quests.size() : next;
		for (int i = anchor - 1; i >= 0 && behind.size() < BEHIND; i--)
		{
			if (quests.get(i).getStatus() == Step.Status.DONE)
			{
				behind.add(0, i);
			}
		}
		// The next stop comes first after them, then every other unfinished quest in path order.
		List<Integer> shown = new ArrayList<>(behind);
		if (next >= 0)
		{
			shown.add(next);
		}
		for (int i = 0; i < quests.size() && shown.size() < maxStops; i++)
		{
			if (i != next && quests.get(i).getStatus() != Step.Status.DONE)
			{
				shown.add(i);
			}
		}
		List<Stop> stops = new ArrayList<>();
		int notDoneShown = 0;
		for (int i : shown)
		{
			Step s = quests.get(i);
			State state = i == next ? State.NEXT
				: s.getStatus() == Step.Status.DONE ? State.DONE
				: s.getStatus() == Step.Status.BLOCKED || s.getStatus() == Step.Status.UNKNOWN ? State.BLOCKED : State.READY;
			if (state != State.DONE)
			{
				notDoneShown++;
			}
			Hazards.Hazard hazard = oneLife ? Hazards.of(s.getQuest()) : null;
			stops.add(new Stop(s.getQuest(), state, hazard == null ? null : hazard.getLabel(),
				s.getStatus() == Step.Status.IN_PROGRESS));
		}
		int notDone = quests.size() - done;
		return new Road(stops, done - behind.size(), Math.max(0, notDone - notDoneShown), done, quests.size());
	}

	public List<Stop> getStops()
	{
		return stops;
	}

	/** Finished quests on the road before the first stop drawn. */
	public int getDoneBefore()
	{
		return doneBefore;
	}

	/** Unfinished quests after the last stop drawn, before the flag. */
	public int getMoreAfter()
	{
		return moreAfter;
	}

	public int getDone()
	{
		return done;
	}

	/** Quests on the road, the goal's own quest not counted. */
	public int getTotal()
	{
		return total;
	}
}
