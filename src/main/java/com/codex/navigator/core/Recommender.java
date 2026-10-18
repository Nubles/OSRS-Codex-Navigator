package com.codex.navigator.core;

import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.DiaryDef;
import com.codex.navigator.data.QuestDef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.QuestState;

/** Picks what is worth doing right now when no goal is pinned, or alongside the pinned ones. */
public final class Recommender
{
	/** A quest suggestion with the reason it was chosen. */
	public static final class Pick
	{
		private final QuestDef quest;
		private final String reason;
		private final double score;
		private final List<SkillGap> gaps;

		Pick(QuestDef quest, String reason, double score, List<SkillGap> gaps)
		{
			this.quest = quest;
			this.reason = reason;
			this.score = score;
			this.gaps = gaps;
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
	}

	private static final int ALMOST_THERE_LEVELS = 6;
	/** How far along a diary's requirements must be to count as within reach. */
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

	/** Quests that can be started this minute, the ones that open the most doors first. */
	public List<Pick> readyNow(AccountSnapshot account, boolean includeMiniquests, int limit)
	{
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
			double score = unlocks * 2.0 + q.points * 1.5 - q.difficultyRank() * 0.4 - (q.miniquest ? 1 : 0);
			picks.add(new Pick(q, reason(q, unlocks), score, new ArrayList<>()));
		}
		picks.sort(Comparator.comparingDouble(Pick::getScore).reversed());
		return picks.size() > limit ? new ArrayList<>(picks.subList(0, limit)) : picks;
	}

	/** Quests held back only by a few levels, closest first. */
	public List<Pick> almostThere(AccountSnapshot account, boolean includeMiniquests, int limit)
	{
		List<Pick> picks = new ArrayList<>();
		for (QuestDef q : data.quests())
		{
			if (!eligible(q, account, includeMiniquests))
			{
				continue;
			}
			List<SkillGap> gaps = planner.levelGapsOnly(q, account);
			if (gaps == null || gaps.isEmpty())
			{
				continue;
			}
			int levels = 0;
			int xp = 0;
			for (SkillGap gap : gaps)
			{
				levels += gap.getTarget() - gap.getCurrent();
				xp += gap.xpToGo();
			}
			if (levels > ALMOST_THERE_LEVELS)
			{
				continue;
			}
			StringBuilder reason = new StringBuilder();
			for (SkillGap gap : gaps)
			{
				if (reason.length() > 0)
				{
					reason.append(", ");
				}
				reason.append(gap.getSkill().getName()).append(' ').append(gap.getCurrent()).append(" to ").append(gap.getTarget());
			}
			picks.add(new Pick(q, reason.toString(), -xp, gaps));
		}
		picks.sort(Comparator.comparingDouble(Pick::getScore).reversed());
		return picks.size() > limit ? new ArrayList<>(picks.subList(0, limit)) : picks;
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

	private static String reason(QuestDef q, int unlocks)
	{
		if (unlocks >= 3)
		{
			return "Opens " + unlocks + " more";
		}
		if (q.points >= 2)
		{
			return "+" + q.points + " QP";
		}
		return null;
	}
}
