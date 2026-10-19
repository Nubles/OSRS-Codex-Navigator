package com.codex.navigator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.QuestDef;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Experience;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

/** Time estimates, goal-aware ranking, the shared route, goal chains and the board other plugins read. */
public class GoalsAndTimeTest
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

	private static AccountSnapshot.Builder level(AccountSnapshot.Builder b, Skill skill, int level)
	{
		return b.skill(skill, level, Experience.getXpForLevel(level));
	}

	@Test
	public void trainingTimeGrowsWithTheGapAndStopsAtTheTarget()
	{
		int xp50 = Experience.getXpForLevel(50);
		assertEquals(0, Effort.trainingHours(Skill.AGILITY, xp50, 50), 0);
		assertEquals(0, Effort.trainingHours(Skill.AGILITY, xp50 + 10, 45), 0);
		double to60 = Effort.trainingHours(Skill.AGILITY, xp50, 60);
		double to70 = Effort.trainingHours(Skill.AGILITY, xp50, 70);
		assertTrue(to60 > 0 && to70 > to60 * 2);
		// Ten Agility levels from 50 is an evening or two, not minutes and not weeks.
		assertTrue("50 to 60 Agility took " + to60, to60 > 1 && to60 < 6);
		// The same experience trains faster in a quick skill.
		assertTrue(Effort.trainingHours(Skill.FLETCHING, xp50, 70) < to70);
		// 1 to 99 in a slow skill is a long haul.
		assertTrue(Effort.trainingHours(Skill.AGILITY, 0, 99) > 150);
	}

	@Test
	public void timesReadAsRoughFigures()
	{
		assertEquals("~20 min", Effort.format(0.33));
		assertEquals("~5 min", Effort.format(0.01));
		assertEquals("~1 h", Effort.format(1.1));
		assertEquals("~2.5 h", Effort.format(2.4));
		assertEquals("~14 h", Effort.format(13.6));
	}

	@Test
	public void planCountsHoursLeftAndNoneOnceComplete()
	{
		AccountSnapshot account = level(fresh(), Skill.AGILITY, 50).build();
		Plan plan = planner.plan(Goal.quest("Regicide"), account);
		assertTrue(plan.getHoursLeft() > Effort.trainingHours(Skill.AGILITY, Experience.getXpForLevel(50), 56));
		AccountSnapshot done = fresh().quest("Regicide", QuestState.FINISHED).build();
		assertEquals(0, planner.plan(Goal.quest("Regicide"), done).getHoursLeft(), 0);
	}

	@Test
	public void nextTrainingStepIsTheQuickestLevelNotTheFewestXp()
	{
		// Fairytale II asks for Thieving 40, Farming 49 and Herblore 57 once its quests are done.
		AccountSnapshot.Builder b = fresh().quest("Lost City", QuestState.FINISHED).quest("Nature Spirit", QuestState.FINISHED)
			.quest("Fairytale I - Growing Pains", QuestState.FINISHED);
		for (String q : planner.plan(Goal.quest("Nature Spirit"), fresh().build()).getPath().stream().map(Step::getQuest)
			.toArray(String[]::new))
		{
			b.quest(q, QuestState.FINISHED);
		}
		level(b, Skill.THIEVING, 39);
		level(b, Skill.FARMING, 48);
		level(b, Skill.HERBLORE, 56);
		Plan plan = planner.plan(Goal.quest("Fairytale II - Cure a Queen"), b.build());
		Step next = plan.getNext();
		assertEquals(Step.Kind.TRAIN, next.getKind());
		SkillGap first = plan.getSkills().get(0);
		for (SkillGap gap : plan.getSkills())
		{
			assertTrue(gap.isMet() || first.hours() <= gap.hours());
		}
		assertEquals(first.getSkill(), next.getSkill());
	}

	@Test
	public void questsOnThePathToAPinnedGoalRankFirst()
	{
		AccountSnapshot account = fresh().build();
		List<Recommender.Pick> plain = recommender.readyNow(account, true, 200);
		int before = indexOf(plain, "Plague City");
		assertTrue(before > 0);
		List<Goal> goals = Collections.singletonList(Goal.quest("Regicide"));
		List<Recommender.Pick> picks = recommender.readyNow(account, goals, true, 5);
		assertEquals("Plague City", picks.get(0).getQuest().name);
		assertEquals("For focus", picks.get(0).getReason());
		assertEquals(goals, picks.get(0).getServes());
	}

	@Test
	public void aFinishedGoalNoLongerPullsItsQuestsUp()
	{
		AccountSnapshot account = fresh().quest("Death Plateau", QuestState.FINISHED).build();
		assertTrue(recommender.goalReach(Collections.singletonList(Goal.quest("Death Plateau")), account).isEmpty());
	}

	@Test
	public void almostThereIsOrderedByTimeAndSkipsLongGrinds()
	{
		AccountSnapshot.Builder b = fresh();
		for (Skill skill : Skill.values())
		{
			level(b, skill, 30);
		}
		List<Recommender.Pick> picks = recommender.almostThere(b.build(), true, 50);
		assertFalse(picks.isEmpty());
		for (int i = 1; i < picks.size(); i++)
		{
			assertTrue(picks.get(i - 1).getHours() <= picks.get(i).getHours() + 1e-9);
		}
		for (Recommender.Pick pick : picks)
		{
			assertTrue(pick.getQuest().name + " takes " + pick.getHours(), pick.getHours() <= 10);
		}
	}

	@Test
	public void routeSharesStepsBetweenGoalsAndStartsWithWhatIsStarted()
	{
		AccountSnapshot account = fresh().quest("Plague City", QuestState.IN_PROGRESS).build();
		List<Goal> goals = Arrays.asList(Goal.quest("Regicide"), Goal.quest("Underground Pass"), Goal.quest("Death Plateau"));
		Route route = Route.build(planner, goals, account);
		assertEquals(3, route.getGoals());
		Route.Leg first = route.getLegs().get(0);
		assertEquals("Plague City", first.getStep().getQuest());
		assertEquals(2, first.getServes().size());
		int plague = 0;
		for (Route.Leg leg : route.getLegs())
		{
			if ("Plague City".equals(leg.getStep().getQuest()))
			{
				plague++;
			}
			assertNotEquals(Step.Status.BLOCKED, leg.getStep().getStatus());
		}
		assertEquals(1, plague);
		// Biohazard, Underground Pass and Regicide wait behind Plague City.
		assertEquals(3, route.getBlocked());
	}

	@Test
	public void routeTakesTheDeepestLevelAnyGoalNeeds()
	{
		AccountSnapshot account = level(fresh(), Skill.AGILITY, 10).build();
		Route route = Route.build(planner, Arrays.asList(Goal.quest("Troll Stronghold"), Goal.quest("Regicide")), account);
		Route.Leg agility = null;
		for (Route.Leg leg : route.getLegs())
		{
			if (leg.getStep().getSkill() == Skill.AGILITY)
			{
				assertNull("Agility listed twice", agility);
				agility = leg;
			}
		}
		assertNotNull(agility);
		assertEquals("Train Agility to 56", agility.getStep().getTitle());
		assertEquals(2, agility.getServes().size());
	}

	@Test
	public void chainsLeadToTheNextTierAndTheNextQuestInTheSeries()
	{
		AccountSnapshot account = fresh().diary("Varrock Easy").quest("Plague City", QuestState.FINISHED).build();
		assertEquals(Goal.diary("Varrock Medium"), Chains.next(planner, Goal.diary("Varrock Easy"), account, true));
		assertEquals(Goal.quest("Biohazard"), Chains.next(planner, Goal.quest("Plague City"), account, true));
		assertNull(Chains.next(planner, Goal.QUEST_CAPE, account, true));
		AccountSnapshot elite = fresh().diary("Varrock Elite").build();
		assertNull(Chains.next(planner, Goal.diary("Varrock Elite"), elite, true));
	}

	@Test
	public void boardCarriesWhatOtherPluginsNeedInAStableShape()
	{
		AccountSnapshot account = level(fresh(), Skill.AGILITY, 50).build();
		List<Goal> goals = Arrays.asList(Goal.quest("Regicide"), Goal.diary("Varrock Easy"));
		Gson gson = new Gson();
		JsonObject board = gson.fromJson(gson.toJson(GoalBoard.of(planner, goals, account)), JsonObject.class);
		assertEquals(1, board.get("version").getAsInt());
		assertEquals("quest:Regicide", board.get("focus").getAsString());
		JsonArray list = board.getAsJsonArray("goals");
		assertEquals(2, list.size());
		JsonObject regicide = list.get(0).getAsJsonObject();
		assertEquals("quest:Regicide", regicide.get("key").getAsString());
		assertEquals("quest", regicide.get("type").getAsString());
		assertFalse(regicide.get("complete").getAsBoolean());
		assertEquals("Start Plague City", regicide.get("next").getAsString());
		assertEquals(56, regicide.getAsJsonObject("skills").get("Agility").getAsInt());
		// Only unmet levels are listed: Agility 56 (at 50) and Ranged 25 for Underground Pass, but nothing met.
		assertEquals(25, regicide.getAsJsonObject("skills").get("Ranged").getAsInt());
		for (String skill : regicide.getAsJsonObject("skills").keySet())
		{
			assertTrue(skill + " is already met", account.level(Planner.skill(skill)) < regicide.getAsJsonObject("skills").get(skill).getAsInt());
		}
		JsonArray quests = regicide.getAsJsonArray("quests");
		assertEquals("Plague City", quests.get(0).getAsString());
		assertEquals("Regicide", quests.get(quests.size() - 1).getAsString());
		double hours = regicide.get("hoursLeft").getAsDouble();
		assertEquals(hours, Math.round(hours * 2) / 2.0, 0);
		assertEquals("diary", list.get(1).getAsJsonObject().get("type").getAsString());
	}

	private static void assertNotEquals(Object a, Object b)
	{
		assertFalse(a + " equals " + b, a.equals(b));
	}

	private static int indexOf(List<Recommender.Pick> picks, String name)
	{
		for (int i = 0; i < picks.size(); i++)
		{
			if (picks.get(i).getQuest().name.equals(name))
			{
				return i;
			}
		}
		return -1;
	}
}
