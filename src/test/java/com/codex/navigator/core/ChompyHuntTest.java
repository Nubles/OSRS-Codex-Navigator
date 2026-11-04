package com.codex.navigator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.TestAccounts;
import com.codex.navigator.data.CodexData;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.QuestState;
import org.junit.Test;

public class ChompyHuntTest
{
	private final Gson gson = new Gson();
	private final CodexData data = CodexData.load(gson);
	private final Planner planner = new Planner(data);

	@Test
	public void theHatsAreTheWikisTableWithTheirItemIds()
	{
		assertEquals(18, ChompyHunt.HATS.length);
		int[] kills = {30, 40, 50, 70, 95, 125, 170, 225, 300, 400, 550, 700, 1000, 1300, 1700, 2250, 3000, 4000};
		for (int i = 0; i < kills.length; i++)
		{
			assertEquals(kills[i], ChompyHunt.HATS[i].getKills());
			// ItemID.CBHAT1..CBHAT18 in RuneLite's gameval, 2978 to 2995 on the wiki.
			assertEquals(2978 + i, ChompyHunt.HATS[i].getItemId());
		}
		assertEquals(net.runelite.api.gameval.ItemID.CBHAT1, ChompyHunt.HATS[0].getItemId());
		assertEquals(net.runelite.api.gameval.ItemID.CBHAT18, ChompyHunt.HATS[17].getItemId());
		Set<String> names = new HashSet<>();
		for (ChompyHunt.Hat h : ChompyHunt.HATS)
		{
			assertTrue(names.add(h.getName()));
		}
	}

	@Test
	public void theDiaryTiersAskForAHatsKills()
	{
		assertEquals(Arrays.asList("Ogre bowman", "Marksman", "Ogre forester", "Ogre expert"), Arrays.asList(
			ChompyHunt.bestHat(30).getName(), ChompyHunt.bestHat(125).getName(), ChompyHunt.bestHat(300).getName(),
			ChompyHunt.bestHat(1000).getName()));
		for (int i = 0; i < ChompyHunt.DIARY_TIERS.length; i++)
		{
			assertNotNull(data.diary("Western " + ChompyHunt.DIARY_TIERS[i]));
		}
		assertNotNull(data.quest(ChompyHunt.QUEST));
	}

	@Test
	public void nextAndBestHat()
	{
		assertEquals("Ogre bowman", ChompyHunt.nextHat(0).getName());
		assertEquals("Bowman", ChompyHunt.nextHat(30).getName());
		assertNull(ChompyHunt.bestHat(29));
		assertEquals("Ogre forester", ChompyHunt.bestHat(399).getName());
		assertNull(ChompyHunt.nextHat(4000));
		assertEquals("Ogre forester", ChompyHunt.hat("ogre forester HAT").getName());
		assertNull(ChompyHunt.hat("Forester's ration"));
	}

	@Test
	public void hearsTheKillAndTheBowsCount()
	{
		assertTrue(ChompyHunt.hear("You scratch a notch on your bow for the chompy bird kill.").isKill());
		ChompyHunt.Heard check = ChompyHunt.hear("You've scratched up a total of 1,234 chompy bird kills so far!");
		assertFalse(check.isKill());
		assertEquals(1234, check.getTotal());
		assertEquals(1, ChompyHunt.hear("You've scratched up a total of 1 chompy bird kill so far!").getTotal());
		assertEquals(0, ChompyHunt.hear("You've scratched up no kills yet! You've got to complete the quest first!").getTotal());
		assertNull(ChompyHunt.hear("You scratch your head."));
		assertNull(ChompyHunt.hear("Nubles: You scratch a notch on your bow for the chompy bird kill. lol"));
		assertNull(ChompyHunt.hear("You've scratched up a total of 99999999999 chompy bird kills so far!"));
		assertNull(ChompyHunt.hear(null));
	}

	@Test
	public void killsBeforeTheFirstCheckWaitForIt()
	{
		ChompyHunt.Log log = new ChompyHunt.Log();
		log.kill(0, true);
		log.kill(30_000, true);
		ChompyHunt.View v = log.view();
		assertFalse(v.isKnown());
		assertEquals(2, v.getUnsynced());
		// The bow's total already holds them.
		log.heard(ChompyHunt.hear("You've scratched up a total of 120 chompy bird kills so far!"), 40_000, true);
		assertEquals(120, log.view().getKills());
		assertEquals(0, log.view().getUnsynced());
		log.kill(60_000, true);
		assertEquals(121, log.view().getKills());
		assertTrue(log.takeChanged());
		assertFalse(log.takeChanged());
	}

	@Test
	public void theRateIsLearnedFromKillsCloseTogetherOnly()
	{
		ChompyHunt.Log log = new ChompyHunt.Log();
		log.total(100);
		long t = 0;
		// Twenty kills a minute apart: 60 an hour.
		for (int i = 0; i < 21; i++)
		{
			log.kill(t, true);
			t += 60_000;
		}
		assertEquals(60, log.view().getPerHour(), 0.5);
		// A long break, then more of the same: the break is not counted.
		t += 3 * 3_600_000;
		for (int i = 0; i < 5; i++)
		{
			log.kill(t, true);
			t += 60_000;
		}
		assertEquals(60, log.view().getPerHour(), 0.5);
		// After a pause (log out), the next kill starts afresh.
		log.pause();
		log.kill(t + 1000, true);
		assertEquals(60, log.view().getPerHour(), 0.5);
		assertEquals(127, log.view().getKills());
		assertEquals((300 - 127) / 60.0, log.view().hoursTo(300), 0.05);
		assertEquals(0, log.view().hoursTo(100), 0);
	}

	@Test
	public void noRateUntilEnoughHuntingAndNoneWhenLearningIsOff()
	{
		ChompyHunt.Log log = new ChompyHunt.Log();
		log.total(0);
		for (int i = 0; i < 5; i++)
		{
			log.kill(i * 60_000L, true);
		}
		assertEquals(0, log.view().getPerHour(), 0);
		ChompyHunt.Log off = new ChompyHunt.Log();
		off.total(0);
		for (int i = 0; i < 40; i++)
		{
			off.kill(i * 60_000L, false);
		}
		assertEquals(40, off.view().getKills());
		assertEquals(0, off.view().getPerHour(), 0);
	}

	@Test
	public void savesAndReadsBackAndSurvivesDamage()
	{
		ChompyHunt.Log log = new ChompyHunt.Log();
		log.total(250);
		for (int i = 0; i < 30; i++)
		{
			log.kill(i * 30_000L, true);
		}
		String json = log.toJson(gson);
		ChompyHunt.Log back = new ChompyHunt.Log();
		assertEquals(4, back.load(gson, json));
		assertEquals(log.view(), back.view());
		assertEquals(280, back.view().getKills());

		ChompyHunt.Log broken = new ChompyHunt.Log();
		assertEquals(0, broken.load(gson, "{not json"));
		assertFalse(broken.view().isKnown());
		assertEquals(1, broken.load(gson, "{\"kills\":42,\"unsynced\":\"x\",\"seconds\":-1}"));
		assertEquals(42, broken.view().getKills());
		assertEquals(0, broken.load(gson, "[1,2]"));
		assertEquals(0, broken.load(gson, null));
		assertEquals(0, broken.load(gson, "{\"kills\":1e400}"));
	}

	@Test
	public void goalKeysRoundTrip()
	{
		Goal g = Goal.chompy("ogre forester");
		assertEquals("chompy:Ogre forester", g.key());
		assertEquals(g, Goal.fromKey("chompy:OGRE FORESTER hat"));
		assertNull(Goal.fromKey("chompy:Golden hat"));
		assertNull(Goal.chompy(null));
		assertEquals("chompy", GoalBoard.type(Goal.Type.CHOMPY));
	}

	@Test
	public void aHatPlansTheKillsLeftAtYourOwnRate()
	{
		AccountSnapshot account = TestAccounts.mid(data).chompy(ChompyHunt.View.of(180, 120, 2.5)).build();
		Plan plan = planner.plan(Goal.chompy("Ogre forester"), account);
		assertEquals("Ogre forester hat", plan.getTitle());
		assertFalse(plan.isComplete());
		assertEquals(0.6, plan.getProgress(), 0.001);
		assertEquals(1.0, plan.getHoursLeft(), 0.001);
		assertEquals(Step.Kind.CHOMPY, plan.getNext().getKind());
		assertEquals("Kill 120 more chompy birds for Ogre forester", plan.getNext().getTitle());
		assertTrue(plan.getNext().getDetail(), plan.getNext().getDetail().contains("at your 120 an hour"));
		assertTrue(plan.getPath().isEmpty());
		// The fastest-path solver has nothing to order for a hat.
		assertFalse(PathSolver.solves(Goal.chompy("Ogre forester")));
	}

	@Test
	public void noRateMeansNoTimeAndAnUnknownCountAsksForTheBow()
	{
		AccountSnapshot slow = TestAccounts.mid(data).chompy(ChompyHunt.View.of(10, 0, 0)).build();
		Plan plan = planner.plan(Goal.chompy("Ogre bowman"), slow);
		assertEquals(0, plan.getHoursLeft(), 0);
		assertTrue(plan.getNext().getDetail().contains("learns your kill rate"));

		AccountSnapshot unknown = TestAccounts.mid(data).chompy(ChompyHunt.View.unsynced(3)).build();
		Plan asks = planner.plan(Goal.chompy("Ogre bowman"), unknown);
		assertEquals(0, asks.getProgress(), 0);
		assertEquals(Step.Status.UNKNOWN, asks.getNext().getStatus());
		assertEquals("Check kills on your ogre bow", asks.getNext().getTitle());
		assertTrue(asks.getNext().getDetail().contains("3 seen so far"));
	}

	@Test
	public void aReachedHatIsCompleteAndHandsOnToTheNext()
	{
		AccountSnapshot account = TestAccounts.mid(data).chompy(ChompyHunt.View.of(320, 200, 3)).build();
		Plan plan = planner.plan(Goal.chompy("Ogre forester"), account);
		assertTrue(plan.isComplete());
		assertNull(plan.getNext());
		assertEquals(Goal.chompy("Forester"), Chains.next(planner, Goal.chompy("Ogre forester"), account, true));
		assertNull(Chains.next(planner, Goal.chompy("Expert dragon archer"),
			TestAccounts.mid(data).chompy(ChompyHunt.View.of(4000, 0, 0)).build(), true));
	}

	@Test
	public void theQuestComesFirstWhenItIsNotDone()
	{
		AccountSnapshot account = TestAccounts.mid(data).quest(ChompyHunt.QUEST, QuestState.NOT_STARTED).build();
		Plan plan = planner.plan(Goal.chompy("Ogre bowman"), account);
		assertFalse(plan.isComplete());
		assertEquals(Step.Kind.QUEST, plan.getNext().getKind());
		boolean listed = false;
		for (Step s : plan.getPath())
		{
			listed |= ChompyHunt.QUEST.equals(s.getQuest());
		}
		assertTrue(listed);
		assertTrue(plan.getHoursLeft() > 0);
		assertTrue(plan.getProgress() <= 0.25);
	}

	@Test
	public void theBoardCarriesAHatGoal()
	{
		AccountSnapshot account = TestAccounts.mid(data).chompy(ChompyHunt.View.of(180, 120, 2.5)).build();
		GoalBoard board = GoalBoard.of(planner, Arrays.asList(Goal.chompy("Ogre forester")), account);
		assertEquals("chompy:Ogre forester", board.key(0));
		assertEquals("chompy", board.get(0).type);
		assertEquals(1.0, board.hoursLeft(0), 0);
		assertEquals("Kill 120 more chompy birds for Ogre forester", board.next(0));
		assertNull(board.fastest(0));
	}

	@Test
	public void theChompyCountMovesTheSnapshotSignature()
	{
		AccountSnapshot a = TestAccounts.mid(data).chompy(ChompyHunt.View.of(180, 0, 0)).build();
		AccountSnapshot b = TestAccounts.mid(data).chompy(ChompyHunt.View.of(181, 0, 0)).build();
		assertTrue(a.structureSignature() != b.structureSignature());
		assertEquals(181, b.toBuilder().build().chompy().getKills());
	}

	@Test
	public void aSessionHuntsALeadingHatAtYourRateThenMovesOn()
	{
		AccountSnapshot account = TestAccounts.mid(data).chompy(ChompyHunt.View.of(180, 120, 2.5)).build();
		// The hat leads: its hour of hunting comes first, the hat is reached, then the next goal gets the rest.
		SessionPlan plan = SessionPlan.build(planner, Arrays.asList(Goal.chompy("Ogre forester"), Goal.quest("Regicide")),
			account, 2);
		SessionPlan.Item first = plan.getItems().get(0);
		assertEquals(Step.Kind.CHOMPY, first.getStep().getKind());
		assertEquals("Kill 120 more chompy birds for Ogre forester", first.getStep().getTitle());
		assertEquals(1.0, first.getHours(), 0.001);
		assertFalse(first.isPartial());
		assertTrue(plan.getGains().get(0).getAfter().isComplete());
		assertTrue(plan.getItems().size() > 1);

		// Half an hour: part of the way.
		SessionPlan half = SessionPlan.build(planner, Arrays.asList(Goal.chompy("Ogre forester")), account, 0.5);
		assertEquals("Kill 60 chompy birds toward Ogre forester", half.getItems().get(0).getStep().getTitle());
		assertTrue(half.getItems().get(0).isPartial());
		assertEquals(0.8, half.getGains().get(0).getAfter().getProgress(), 0.001);

		// No rate learned: nothing to time, so nothing is planned for the hat.
		AccountSnapshot untimed = TestAccounts.mid(data).chompy(ChompyHunt.View.of(180, 0, 0)).build();
		assertTrue(SessionPlan.build(planner, Arrays.asList(Goal.chompy("Ogre forester")), untimed, 1).getItems().isEmpty());
	}
}
