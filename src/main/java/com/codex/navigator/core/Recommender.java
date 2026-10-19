package com.codex.navigator.core;

import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.DiaryDef;
import com.codex.navigator.data.QuestDef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.QuestState;

/**
 * Picks what is worth doing right now. Every quest is weighed by what it opens, what it is worth and how
 * long it takes, and anything on the path to one of your pinned goals counts for far more: the best next
 * step is the one that moves you toward what you said you want.
 */
public final class Recommender
{
	/** A quest suggestion with the reason it was chosen. */
	public static final class Pick
	{
		private final QuestDef quest;
		private final String reason;
		private final double score;
		private final List<SkillGap> gaps;
		private final double hours;
		private final List<Goal> serves;

		Pick(QuestDef quest, String reason, double score, List<SkillGap> gaps, double hours, List<Goal> serves)
		{
			this.quest = quest;
			this.reason = reason;
			this.score = score;
			this.gaps = gaps;
			this.hours = hours;
			this.serves = serves;
		}

		public QuestDef getQuest()
		{
			return quest;
		}

		public String getReason()
		{
			return reason;
		}

		public double getScore()
		{
			return score;
		}

		/** Levels still missing, for "almost there" picks; empty for quests that are ready. */
		public List<SkillGap> getGaps()
		{
			return gaps;
		}

		/** About how long until it is done: the quest itself, plus any training it still waits on. */
		public double getHours()
		{
			return hours;
		}

		/** The pinned goals whose path runs through this quest, in pin order. */
		public List<Goal> getServes()
		{
			return serves;
		}
	}

	private static final int ALMOST_THERE_LEVELS = 6;
	/** A gap of more levels than {@link #ALMOST_THERE_LEVELS} still counts when it trains this quickly. */
	private static final double ALMOST_THERE_QUICK_HOURS = 3;
	/** Nothing that needs longer than this to train is "almost there", whatever its level count. */
	private static final double ALMOST_THERE_MAX_HOURS = 10;
	/** What being on the path to a pinned goal adds to a quest's worth, and the extra for the focus. */
	static final double GOAL_WEIGHT = 12;
	static final double FOCUS_WEIGHT = 6;
	/** How much diary progress counts as within reach. */
	static final double WITHIN_REACH = 0.5;

	private final Planner planner;
	private final CodexData data;

	public Recommender(Planner planner)
	{
		this.planner = planner;
		this.data = planner.getData();
	}

	public List<QuestDef> inProgress(AccountSnapshot account)
	{
		List<QuestDef> list = new ArrayList<>();
		for (QuestDef q : data.quests())
		{
			if (account.quest(q.name) == QuestState.IN_PROGRESS)
			{
				list.add(q);
			}
		}
		return list;
	}

	/**
	 * For each unfinished quest on the way to an unfinished pinned goal, the goals it leads to, in pin
	 * order. Finished goals lead nowhere, so a completed goal left pinned stops pulling its quests up.
	 */
	public Map<String, List<Goal>> goalReach(List<Goal> goals, AccountSnapshot account)
	{
		Map<String, List<Goal>> reach = new HashMap<>();
		for (Goal goal : goals)
		{
			Plan plan = planner.plan(goal, account);
			if (plan.isComplete())
			{
				continue;
			}
			for (Step step : plan.getPath())
			{
				if (step.getKind() == Step.Kind.QUEST && step.getStatus() != Step.Status.DONE && step.getQuest() != null)
				{
					List<Goal> list = reach.computeIfAbsent(step.getQuest(), k -> new ArrayList<>());
					if (!list.contains(goal))
					{
						list.add(goal);
					}
				}
			}
		}
		return reach;
	}

	/** Quests that can be started this minute, with no goals to weigh. */
	public List<Pick> readyNow(AccountSnapshot account, boolean includeMiniquests, int limit)
	{
		return readyNow(account, Collections.emptyList(), includeMiniquests, limit);
	}

	/**
	 * Quests that can be started this minute, best first. Quests on the way to the focus lead, then those
	 * on the way to other pinned goals, then the rest. Within each group a quest's worth is what it opens,
	 * its quest points and the goals it moves, divided by its length, so a short quest that opens as much
	 * as a long one comes first.
	 */
	public List<Pick> readyNow(AccountSnapshot account, List<Goal> goals, boolean includeMiniquests, int limit)
	{
		Map<String, List<Goal>> reach = goalReach(goals, account);
		Goal focus = goals.isEmpty() ? null : goals.get(0);
		List<Pick> picks = new ArrayList<>();
		for (QuestDef q : data.quests())
		{
			if (!eligible(q, account, includeMiniquests))
			{
				continue;
			}
			if (!planner.blockers(q, account).isEmpty())
			{
				continue;
			}
			int unlocks = data.unlockCount(q.name);
			List<Goal> serves = reach.getOrDefault(q.name, Collections.emptyList());
			double worth = unlocks * 2.0 + q.points * 1.5 - (q.miniquest ? 1 : 0) + goalWorth(serves, focus);
			double hours = Effort.questHours(q);
			double score = Math.max(0.2, worth) / (hours + 0.5);
			picks.add(new Pick(q, reason(q, unlocks, serves, focus), score, new ArrayList<>(), hours, serves));
		}
		// What the player asked for comes first: the focus's quests, then other goals', then the rest by worth.
		picks.sort(Comparator.comparingInt((Pick p) -> tier(p.getServes(), focus))
			.thenComparing(Comparator.comparingDouble(Pick::getScore).reversed())
			.thenComparing(p -> p.getQuest().name));
		return picks.size() > limit ? new ArrayList<>(picks.subList(0, limit)) : picks;
	}

	/** Quests held back only by levels, with no goals to weigh. */
	public List<Pick> almostThere(AccountSnapshot account, boolean includeMiniquests, int limit)
	{
		return almostThere(account, Collections.emptyList(), includeMiniquests, limit);
	}

	/**
	 * Quests held back only by levels that train quickly, the soonest first. Time is what counts, not the
	 * level count: five Agility levels and five Construction levels are very different evenings. A quest on
	 * the way to a pinned goal is moved forward as though its training took a third of the time.
	 */
	public List<Pick> almostThere(AccountSnapshot account, List<Goal> goals, boolean includeMiniquests, int limit)
	{
		Map<String, List<Goal>> reach = goalReach(goals, account);
		Goal focus = goals.isEmpty() ? null : goals.get(0);
		List<Pick> picks = new ArrayList<>();
		for (QuestDef q : data.quests())
		{
			if (!eligible(q, account, includeMiniquests))
			{
				continue;
			}
			List<SkillGap> gaps = planner.levelGapsOnly(q, account);
			// A quest a boost can start today is listed under boostNow instead.
			if (gaps == null || gaps.isEmpty() || planner.boostGaps(q, account) != null)
			{
				continue;
			}
			int levels = 0;
			double hours = 0;
			for (SkillGap gap : gaps)
			{
				levels += gap.getTarget() - gap.getCurrent();
				hours += gap.hours();
			}
			if (hours > ALMOST_THERE_MAX_HOURS || levels > ALMOST_THERE_LEVELS && hours > ALMOST_THERE_QUICK_HOURS)
			{
				continue;
			}
			gaps.sort(Comparator.comparingDouble(SkillGap::hours).reversed());
			StringBuilder reason = new StringBuilder();
			for (SkillGap gap : gaps)
			{
				if (reason.length() > 0)
				{
					reason.append(", ");
				}
				reason.append(gap.getSkill().getName()).append(' ').append(gap.getCurrent()).append(" to ").append(gap.getTarget());
			}
			List<Goal> serves = reach.getOrDefault(q.name, Collections.emptyList());
			double weight = serves.isEmpty() ? 1 : 3;
			picks.add(new Pick(q, reason.toString(), -hours / weight, gaps, hours, serves));
		}
		picks.sort(Comparator.comparingDouble(Pick::getScore).reversed().thenComparing(p -> p.getQuest().name));
		return picks.size() > limit ? new ArrayList<>(picks.subList(0, limit)) : picks;
	}

	/**
	 * Quests that can be started today with a pie or a potion: held back only by levels that may be boosted, each
	 * within reach of its everyday boost. Ranked like {@link #readyNow}, goals first. Each pick's gaps are the
	 * levels to boost and its reason names the first boost.
	 */
	public List<Pick> boostNow(AccountSnapshot account, List<Goal> goals, boolean includeMiniquests, int limit)
	{
		Map<String, List<Goal>> reach = goalReach(goals, account);
		Goal focus = goals.isEmpty() ? null : goals.get(0);
		List<Pick> picks = new ArrayList<>();
		for (QuestDef q : data.quests())
		{
			if (!eligible(q, account, includeMiniquests))
			{
				continue;
			}
			List<SkillGap> gaps = planner.boostGaps(q, account);
			if (gaps == null)
			{
				continue;
			}
			int unlocks = data.unlockCount(q.name);
			List<Goal> serves = reach.getOrDefault(q.name, Collections.emptyList());
			double worth = unlocks * 2.0 + q.points * 1.5 - (q.miniquest ? 1 : 0) + goalWorth(serves, focus);
			double hours = Effort.questHours(q);
			picks.add(new Pick(q, Planner.boostDetail(gaps), Math.max(0.2, worth) / (hours + 0.5), gaps, hours, serves));
		}
		picks.sort(Comparator.comparingInt((Pick p) -> tier(p.getServes(), focus))
			.thenComparing(Comparator.comparingDouble(Pick::getScore).reversed())
			.thenComparing(p -> p.getQuest().name));
		return picks.size() > limit ? new ArrayList<>(picks.subList(0, limit)) : picks;
	}

	/** 0 for a quest on the focus's path, 1 for another goal's, 2 for anything else. */
	private static int tier(List<Goal> serves, Goal focus)
	{
		return serves.isEmpty() ? 2 : focus != null && serves.contains(focus) ? 0 : 1;
	}

	private static double goalWorth(List<Goal> serves, Goal focus)
	{
		if (serves.isEmpty())
		{
			return 0;
		}
		return serves.size() * GOAL_WEIGHT + (focus != null && serves.contains(focus) ? FOCUS_WEIGHT : 0);
	}

	/**
	 * Unfinished diary tiers that are genuinely close, the closest first. Diaries are members content,
	 * so a free world gets none, and tiers under {@link #WITHIN_REACH} of their requirements are left out.
	 */
	public List<Plan> closestDiaries(AccountSnapshot account, int limit)
	{
		List<Plan> plans = new ArrayList<>();
		if (!account.isMembers())
		{
			return plans;
		}
		for (DiaryDef d : data.diaries())
		{
			if (!account.diaryDone(d.name))
			{
				Plan plan = planner.plan(Goal.diary(d.name), account);
				if (plan.getProgress() >= WITHIN_REACH)
				{
					plans.add(plan);
				}
			}
		}
		plans.sort(Comparator.comparingDouble(Plan::getProgress).reversed()
			.thenComparingInt(p -> data.diary(p.getGoal().getName()).tierRank()));
		return plans.size() > limit ? new ArrayList<>(plans.subList(0, limit)) : plans;
	}

	/** Not started, allowed by the miniquest setting, and startable on the current world. */
	private boolean eligible(QuestDef q, AccountSnapshot account, boolean includeMiniquests)
	{
		return (!q.miniquest || includeMiniquests)
			&& account.quest(q.name) == QuestState.NOT_STARTED
			&& (account.isMembers() || data.isFree(q));
	}

	private static String reason(QuestDef q, int unlocks, List<Goal> serves, Goal focus)
	{
		if (focus != null && serves.contains(focus))
		{
			return "For focus";
		}
		if (!serves.isEmpty())
		{
			return serves.size() == 1 ? "For a goal" : "For " + serves.size() + " goals";
		}
		if (unlocks >= 3)
		{
			return "Opens " + unlocks;
		}
		if (q.points >= 2)
		{
			return "+" + q.points + " QP";
		}
		return null;
	}
}
