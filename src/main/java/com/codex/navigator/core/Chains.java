package com.codex.navigator.core;

import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.DiaryDef;
import com.codex.navigator.data.QuestDef;
import net.runelite.api.QuestState;

/**
 * What naturally comes after a goal: the next tier of a diary, or the quest a finished quest leads
 * straight into. It lets a finished goal hand over to the next link in its chain with one click.
 */
public final class Chains
{
	private static final String[] TIERS = {"Easy", "Medium", "Hard", "Elite"};

	private Chains()
	{
	}

	/**
	 * The goal that follows this one, or null when nothing does. For a quest, the unfinished quests that
	 * list it as a direct requirement are weighed: one you can start now beats one still blocked, then the
	 * one that opens the most. Quests in the same series are preferred, since that is what a player means
	 * by "the next one".
	 */
	public static Goal next(Planner planner, Goal goal, AccountSnapshot account, boolean includeMiniquests)
	{
		CodexData data = planner.getData();
		switch (goal.getType())
		{
			case DIARY:
			{
				DiaryDef diary = data.diary(goal.getName());
				if (diary == null)
				{
					return null;
				}
				for (int tier = diary.tierRank(); tier < TIERS.length; tier++)
				{
					String name = diary.area + " " + TIERS[tier];
					if (data.diary(name) != null && !account.diaryDone(name))
					{
						return Goal.diary(name);
					}
				}
				return null;
			}
			case QUEST:
			{
				QuestDef quest = data.quest(goal.getName());
				if (quest == null)
				{
					return null;
				}
				QuestDef best = null;
				double bestScore = Double.NEGATIVE_INFINITY;
				for (String name : data.questsNeeding(quest.name))
				{
					QuestDef q = data.quest(name);
					if (q == null || account.questDone(q.name) || q.miniquest && !includeMiniquests
						|| !account.isMembers() && !data.isFree(q))
					{
						continue;
					}
					double score = data.unlockCount(q.name);
					if (planner.blockers(q, account).isEmpty() || account.quest(q.name) == QuestState.IN_PROGRESS)
					{
						score += 1000;
					}
					if (quest.series != null && quest.series.equals(q.series))
					{
						score += 500;
					}
					if (score > bestScore)
					{
						best = q;
						bestScore = score;
					}
				}
				return best == null ? null : Goal.quest(best.name);
			}
			default:
				return null;
		}
	}
}
