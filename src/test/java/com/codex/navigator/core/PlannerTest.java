package com.codex.navigator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.DiaryDef;
import com.codex.navigator.data.QuestDef;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

public class PlannerTest
{
	private CodexData data;
	private Planner planner;

	@Before
	public void setUp()
	{
		data = CodexData.load(new Gson());
		planner = new Planner(data);
	}

	private AccountSnapshot.Builder fresh()
	{
		AccountSnapshot.Builder b = new AccountSnapshot.Builder().loggedIn(true);
		for (QuestDef q : data.quests())
		{
			b.quest(q.name, QuestState.NOT_STARTED);
		}
		return b;
	}

	@Test
	public void bundledDataLoadsAndEveryPrerequisiteResolves()
	{
		assertTrue(data.quests().size() > 200);
		assertEquals(48, data.diaries().size());
		for (QuestDef q : data.quests())
		{
			for (String p : q.prereqs)
			{
				assertNotNull(q.name + " needs unknown quest " + p, data.quest(p));
			}
			for (String skill : q.skills.keySet())
			{
				assertNotNull(q.name + " names unknown skill " + skill, Planner.skill(skill));
			}
		}
		for (DiaryDef d : data.diaries())
		{
			for (String skill : d.skills.keySet())
			{
				assertNotNull(d.name + " names unknown skill " + skill, Planner.skill(skill));
			}
		}
	}

	@Test
	public void pathListsPrerequisitesBeforeTheQuestThatNeedsThem()
	{
		Plan plan = planner.plan(Goal.quest("Desert Treasure I"), fresh().build());
		List<Step> path = plan.getPath();
		assertEquals("Desert Treasure I", path.get(path.size() - 1).getTitle());
		for (int i = 0; i < path.size(); i++)
		{
			QuestDef quest = data.quest(path.get(i).getTitle());
			if (quest == null)
			{
				continue;
			}
			for (String prereq : quest.prereqs)
			{
				int at = indexOf(path, prereq);
				assertTrue(prereq + " should come before " + quest.name, at >= 0 && at < i);
			}
		}
	}

	@Test
	public void nextStepIsAReadyQuestOnAFreshAccount()
	{
		Plan plan = planner.plan(Goal.quest("Desert Treasure I"), fresh().build());
		Step next = plan.getNext();
		assertNotNull(next);
		assertEquals(Step.Kind.QUEST, next.getKind());
		assertEquals(Step.Status.READY, next.getStatus());
		assertFalse(plan.isComplete());
		assertTrue(plan.getProgress() < 0.5);
	}

	@Test
	public void inProgressQuestsComeFirst()
	{
		AccountSnapshot account = fresh().quest("The Tourist Trap", QuestState.IN_PROGRESS).build();
		Step next = planner.plan(Goal.quest("Desert Treasure I"), account).getNext();
		assertEquals("The Tourist Trap", next.getTitle());
		assertEquals(Step.Status.IN_PROGRESS, next.getStatus());
	}

	@Test
	public void missingLevelsBecomeATrainingStepOnceQuestsAreDone()
	{
		AccountSnapshot.Builder b = fresh();
		Plan first = planner.plan(Goal.quest("Desert Treasure I"), b.build());
		for (Step step : first.getPath())
		{
			if (!step.getTitle().equals("Desert Treasure I"))
			{
				b.quest(step.getTitle(), QuestState.FINISHED);
			}
		}
		Plan plan = planner.plan(Goal.quest("Desert Treasure I"), b.build());
		Step next = plan.getNext();
		assertEquals(Step.Kind.TRAIN, next.getKind());
		assertNotNull(next.getSkill());
		assertTrue(plan.getProgress() > first.getProgress());
	}

	@Test
	public void finishedQuestIsComplete()
	{
		AccountSnapshot account = fresh().quest("Cook's Assistant", QuestState.FINISHED).build();
		Plan plan = planner.plan(Goal.quest("Cook's Assistant"), account);
		assertTrue(plan.isComplete());
		assertEquals(1.0, plan.getProgress(), 0);
		assertNull(plan.getNext());
	}

	@Test
	public void diaryWithEverythingMetAsksForTheTasks()
	{
		AccountSnapshot.Builder b = fresh();
		DiaryDef diary = data.diary("Lumbridge Easy");
		for (Skill skill : Skill.values())
		{
			b.skill(skill, 99, 13_034_431);
		}
		for (QuestDef q : data.quests())
		{
			b.quest(q.name, QuestState.FINISHED);
		}
		Step next = planner.plan(Goal.diary(diary.name), b.build()).getNext();
		assertEquals(Step.Kind.DIARY_TASKS, next.getKind());
	}

	@Test
	public void recommenderOnlySuggestsStartableQuests()
	{
		AccountSnapshot account = fresh().build();
		Recommender rec = new Recommender(planner);
		List<Recommender.Pick> picks = rec.readyNow(account, true, 10);
		assertFalse(picks.isEmpty());
		for (Recommender.Pick pick : picks)
		{
			assertTrue(planner.blockers(pick.getQuest(), account).isEmpty());
		}
	}

	@Test
	public void goalKeysRoundTrip()
	{
		for (Goal goal : Arrays.asList(Goal.quest("Dragon Slayer II"), Goal.diary("Varrock Elite"), Goal.QUEST_CAPE))
		{
			assertEquals(goal, Goal.fromKey(goal.key()));
		}
		assertNull(Goal.fromKey("nonsense"));
	}

	@Test
	public void goalStoreKeepsFocusFirstAndSaves()
	{
		GoalStore store = new GoalStore();
		List<List<String>> saved = new java.util.ArrayList<>();
		store.setSaver(saved::add);
		store.toggle(Goal.quest("A"));
		store.toggle(Goal.quest("B"));
		store.makeFocus(Goal.quest("B"));
		assertEquals(Goal.quest("B"), store.focus());
		store.toggle(Goal.quest("B"));
		assertEquals(Goal.quest("A"), store.focus());
		assertEquals(Collections.singletonList("quest:A"), saved.get(saved.size() - 1));
	}

	private static int indexOf(List<Step> path, String title)
	{
		for (int i = 0; i < path.size(); i++)
		{
			if (path.get(i).getTitle().equals(title))
			{
				return i;
			}
		}
		return -1;
	}
}
