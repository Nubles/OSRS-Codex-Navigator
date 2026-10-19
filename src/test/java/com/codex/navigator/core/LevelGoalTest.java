package com.codex.navigator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.QuestDef;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Experience;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

public class LevelGoalTest
{
	private CodexData data;
	private Planner planner;

	@Before
	public void setUp()
	{
		data = CodexData.load(new Gson());
		planner = new Planner(data);
	}

	private AccountSnapshot account(int agility)
	{
		AccountSnapshot.Builder b = new AccountSnapshot.Builder().loggedIn(true).members(true);
		for (QuestDef q : data.quests())
		{
			b.quest(q.name, QuestState.NOT_STARTED);
		}
		for (Skill skill : Skill.values())
		{
			if (skill != Skill.OVERALL)
			{
				b.skill(skill, 50, Experience.getXpForLevel(50));
			}
		}
		b.skill(Skill.AGILITY, agility, Experience.getXpForLevel(agility));
		return b.build();
	}

	@Test
	public void aLevelGoalKeepsOneKeyHoweverItIsSpelled()
	{
		Goal goal = Goal.level(Skill.AGILITY, 60);
		assertEquals("skill:Agility 60", goal.key());
		assertEquals(goal, Goal.fromKey("skill:Agility 60"));
		assertEquals(goal, Goal.fromKey("skill:agility 60"));
		assertEquals(Skill.AGILITY, goal.getSkill());
		assertEquals(60, goal.getLevel());
		assertNull(Goal.fromKey("skill:Agility 100"));
		assertNull(Goal.fromKey("skill:Agility 1"));
		assertNull(Goal.fromKey("skill:Overall 50"));
		assertNull(Goal.fromKey("skill:Agility"));
		assertNull(Goal.fromKey("skill:Fishing sixty"));
		assertNull(Goal.level(Skill.OVERALL, 50));
	}

	@Test
	public void aLevelGoalIsTrainingAndSaysWhatItOpens()
	{
		Plan plan = planner.plan(Goal.level(Skill.AGILITY, 60), account(50));
		assertEquals("60 Agility", plan.getTitle());
		assertFalse(plan.isComplete());
		assertTrue(plan.getPath().isEmpty());
		assertEquals(Step.Kind.TRAIN, plan.getNext().getKind());
		assertEquals("Train Agility to 60", plan.getNext().getTitle());
		assertTrue(plan.getHoursLeft() > 0);
		// Sins of the Father 52, A Kingdom Divided 54, Monkey Madness II 55, Regicide 56, Grim Tales 59, Dragon Slayer II 60.
		List<String> opens = new java.util.ArrayList<>();
		for (QuestDef q : planner.opens(Skill.AGILITY, 60, account(50), true))
		{
			opens.add(q.name);
		}
		assertEquals(Arrays.asList("Sins of the Father", "A Kingdom Divided", "Monkey Madness II", "Regicide", "Grim Tales",
			"Dragon Slayer II"), opens);
		assertEquals("Level goal · opens 6 quests", plan.getSubtitle());

		Plan done = planner.plan(Goal.level(Skill.AGILITY, 60), account(61));
		assertTrue(done.isComplete());
		assertNull(done.getNext());
	}

	@Test
	public void aReachedLevelHandsOnToTheNextRoundTen()
	{
		assertEquals(Goal.level(Skill.AGILITY, 70), Chains.next(planner, Goal.level(Skill.AGILITY, 60), account(61), false));
		// Trained past the goal: the next ten past the level now.
		assertEquals(Goal.level(Skill.AGILITY, 80), Chains.next(planner, Goal.level(Skill.AGILITY, 60), account(73), false));
		assertEquals(Goal.level(Skill.AGILITY, 99), Chains.next(planner, Goal.level(Skill.AGILITY, 90), account(92), false));
		assertNull(Chains.next(planner, Goal.level(Skill.AGILITY, 99), account(99), false));
	}

	@Test
	public void theOfferedLevelsAreTheNextUnlocksARoundTenAnd99()
	{
		List<Integer> levels = planner.suggestedLevels(Skill.AGILITY, account(50), true, 5);
		assertEquals(5, levels.size());
		// The nearest level that opens something comes first (a diary tier asks for 51).
		assertEquals(Integer.valueOf(51), levels.get(0));
		assertTrue(levels.contains(60));
		assertEquals(Integer.valueOf(99), levels.get(4));
		for (int i = 1; i < levels.size(); i++)
		{
			assertTrue(levels.get(i) > levels.get(i - 1));
		}
		assertEquals(Collections.emptyList(), planner.suggestedLevels(Skill.AGILITY, account(99), true, 5));
		// At 95 only 99 is left to offer.
		assertEquals(Collections.singletonList(99), planner.suggestedLevels(Skill.AGILITY, account(95), true, 5));
	}

	@Test
	public void theBoardCarriesALevelGoalAsTypeSkill()
	{
		GoalBoard board = GoalBoard.of(planner, Collections.singletonList(Goal.level(Skill.AGILITY, 60)), account(50));
		JsonObject json = new Gson().toJsonTree(board).getAsJsonObject();
		JsonObject entry = json.getAsJsonArray("goals").get(0).getAsJsonObject();
		assertEquals("skill:Agility 60", entry.get("key").getAsString());
		assertEquals("skill", entry.get("type").getAsString());
		assertEquals("60 Agility", entry.get("title").getAsString());
		assertEquals(60, entry.getAsJsonObject("skills").get("Agility").getAsInt());
		assertEquals(0, entry.getAsJsonArray("quests").size());
		assertEquals("Train Agility to 60", entry.get("next").getAsString());
	}

	@Test
	public void aLevelGoalSharesItsTrainingWithTheQuestsThatNeedIt()
	{
		// Dragon Slayer II needs 60 Agility too; the route trains Agility once, for both goals, to the higher level.
		List<Goal> pinned = Arrays.asList(Goal.level(Skill.AGILITY, 65), Goal.quest("Dragon Slayer II"));
		AccountSnapshot account = account(50);
		Route route = Route.build(planner, pinned, account);
		int agility = 0;
		for (Route.Leg leg : route.getLegs())
		{
			if (leg.getStep().getSkill() == Skill.AGILITY)
			{
				agility++;
				assertEquals(65, leg.getTarget());
				assertTrue(leg.getServes().contains(Goal.level(Skill.AGILITY, 65)));
			}
		}
		assertEquals(1, agility);
		SessionPlan session = SessionPlan.build(planner, Collections.singletonList(Goal.level(Skill.AGILITY, 60)), account, 2);
		assertFalse(session.getItems().isEmpty());
		assertEquals(Skill.AGILITY, session.getItems().get(0).getStep().getSkill());
	}
}
