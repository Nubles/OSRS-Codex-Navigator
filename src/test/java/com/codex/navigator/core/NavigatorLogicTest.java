package com.codex.navigator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.DiaryDef;
import com.codex.navigator.data.QuestDef;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

public class NavigatorLogicTest
{
	private CodexData data;
	private Planner planner;
	private Recommender recommender;

	@Before
	public void setUp()
	{
		data = CodexData.load(new Gson());
		planner = new Planner(data);
		recommender = new Recommender(planner);
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

	private AccountSnapshot.Builder maxed()
	{
		AccountSnapshot.Builder b = new AccountSnapshot.Builder().loggedIn(true).combatLevel(126).questPoints(500);
		for (Skill skill : Skill.values())
		{
			b.skill(skill, 99, 13_034_431);
		}
		for (QuestDef q : data.quests())
		{
			b.quest(q.name, QuestState.FINISHED);
		}
		return b;
	}

	@Test
	public void everyFreeQuestNameIsInTheData()
	{
		for (String name : CodexData.freeQuestNames())
		{
			assertNotNull("Unknown free quest " + name, data.quest(name));
		}
	}

	@Test
	public void freeWorldOnlySuggestsFreeQuests()
	{
		AccountSnapshot account = fresh().members(false).build();
		List<Recommender.Pick> picks = recommender.readyNow(account, true, 50);
		assertFalse(picks.isEmpty());
		for (Recommender.Pick pick : picks)
		{
			assertTrue(pick.getQuest().name + " is a members quest", data.isFree(pick.getQuest()));
		}
		for (Recommender.Pick pick : recommender.almostThere(account, true, 50))
		{
			assertTrue(data.isFree(pick.getQuest()));
		}
		assertTrue(recommender.closestDiaries(account, 3).isEmpty());
	}

	@Test
	public void membersWorldSuggestsMembersQuests()
	{
		List<Recommender.Pick> picks = recommender.readyNow(fresh().build(), true, 50);
		boolean anyMembers = false;
		for (Recommender.Pick pick : picks)
		{
			anyMembers |= !data.isFree(pick.getQuest());
		}
		assertTrue(anyMembers);
	}

	@Test
	public void diariesWithinReachLeaveOutDistantTiers()
	{
		for (Plan plan : recommender.closestDiaries(fresh().build(), 48))
		{
			assertTrue(plan.getTitle(), plan.getProgress() >= Recommender.WITHIN_REACH);
		}
	}

	@Test
	public void diaryWithEveryRequirementMetAwaitsTasks()
	{
		Plan plan = planner.plan(Goal.diary("Varrock Elite"), maxed().build());
		assertTrue(plan.isAwaitingTasks());
		assertFalse(plan.isComplete());

		Plan done = planner.plan(Goal.diary("Varrock Elite"), maxed().diary("Varrock Elite").build());
		assertFalse(done.isAwaitingTasks());
		assertTrue(done.isComplete());

		Plan quest = planner.plan(Goal.quest("Dragon Slayer II"), fresh().build());
		assertFalse(quest.isAwaitingTasks());
	}

	@Test
	public void planCarriesQuestPointAndCombatTargets()
	{
		Plan plan = planner.plan(Goal.quest("Dragon Slayer II"), fresh().build());
		assertTrue(plan.getQuestPointTarget() >= 200);
		Plan finished = planner.plan(Goal.quest("Dragon Slayer II"), maxed().build());
		assertEquals(0, finished.getQuestPointTarget());
	}

	@Test
	public void directDependentsAreIndexed()
	{
		assertTrue(data.questsNeeding("Priest in Peril").contains("Nature Spirit"));
		assertTrue(data.diariesNeeding("Priest in Peril").size() + data.questsNeeding("Priest in Peril").size() > 3);
		assertTrue(data.questsNeeding("No such quest").isEmpty());
		for (QuestDef q : data.quests())
		{
			for (String needing : data.questsNeeding(q.name))
			{
				assertTrue(data.quest(needing).prereqs.contains(q.name));
			}
		}
	}

	@Test
	public void experienceAloneDoesNotChangeTheStructure()
	{
		AccountSnapshot a = fresh().skill(Skill.MINING, 50, 101_333).build();
		AccountSnapshot b = fresh().skill(Skill.MINING, 50, 101_400).build();
		AccountSnapshot c = fresh().skill(Skill.MINING, 51, 111_945).build();
		assertNotEquals(a.signature(), b.signature());
		assertEquals(a.structureSignature(), b.structureSignature());
		assertNotEquals(a.structureSignature(), c.structureSignature());
		assertNotEquals(a.structureSignature(), fresh().skill(Skill.MINING, 50, 101_333).members(false).build()
			.structureSignature());
	}

	@Test
	public void offlinePinsMergeIntoTheProfileAndSaveOnce()
	{
		GoalStore store = new GoalStore();
		List<List<String>> saved = new ArrayList<>();
		store.setSaver(saved::add);

		store.load(Arrays.asList("quest:Dragon Slayer II", "diary:Varrock Elite"), null);
		assertTrue(saved.isEmpty());

		store.load(Collections.singletonList("quest:Dragon Slayer II"),
			Arrays.asList("quest:Dragon Slayer II", "cape:Quest point cape"));
		assertEquals(Arrays.asList("quest:Dragon Slayer II", "cape:Quest point cape"), store.keys());
		assertEquals(1, saved.size());
		assertEquals(Goal.quest("Dragon Slayer II"), store.focus());

		store.load(Collections.singletonList("quest:A"), Collections.singletonList("quest:A"));
		assertEquals(1, saved.size());
	}

	@Test
	public void malformedKeysAreSkipped()
	{
		assertNull(Goal.fromKey("quest:"));
		assertNull(Goal.fromKey("quest:   "));
		assertEquals(Goal.QUEST_CAPE, Goal.fromKey("cape:"));
		GoalStore store = new GoalStore();
		store.load(Arrays.asList(null, "", "quest:", "bogus", "quest:Cook's Assistant"));
		assertEquals(Collections.singletonList("quest:Cook's Assistant"), store.keys());
	}

	@Test
	public void diaryNamesMatchTheAreasThePluginReads()
	{
		for (DiaryDef d : data.diaries())
		{
			assertEquals(d.area + " " + d.tier, d.name);
		}
	}

	@Test
	public void goalPinnedMidSessionIsAnnouncedWhenFinished()
	{
		CompletionWatch watch = new CompletionWatch();
		Goal cook = Goal.quest("Cook's Assistant");
		List<Goal> none = Collections.emptyList();
		List<Goal> pinned = Collections.singletonList(cook);
		// Published before the goal was pinned; nothing publishes between the pin and the finish.
		assertTrue(watch.finished(planner, none, fresh().quest(cook.getName(), QuestState.IN_PROGRESS).build()).isEmpty());
		List<Plan> done = watch.finished(planner, pinned, fresh().quest(cook.getName(), QuestState.FINISHED).build());
		assertEquals(1, done.size());
		assertEquals(cook, done.get(0).getGoal());
	}

	@Test
	public void goalFinishedWhileUnpinnedIsNotAnnouncedOnRepin()
	{
		CompletionWatch watch = new CompletionWatch();
		Goal cook = Goal.quest("Cook's Assistant");
		List<Goal> pinned = Collections.singletonList(cook);
		watch.finished(planner, pinned, fresh().build());
		watch.finished(planner, Collections.emptyList(), fresh().quest(cook.getName(), QuestState.FINISHED).build());
		assertTrue(watch.finished(planner, pinned, fresh().quest(cook.getName(), QuestState.FINISHED).questPoints(1).build())
			.isEmpty());
		watch.reset();
		assertTrue(watch.finished(planner, pinned, fresh().quest(cook.getName(), QuestState.FINISHED).build()).isEmpty());
	}
}
