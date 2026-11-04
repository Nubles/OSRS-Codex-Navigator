package com.codex.navigator.core;

import com.codex.navigator.CaFixture;
import com.codex.navigator.TestAccounts;
import com.codex.navigator.data.CodexData;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CombatTasksTest
{
	private final CodexData data = CodexData.load(new Gson());

	@Test
	public void theGamesListAgreesWithTheWikiAndTheBundledCounts()
	{
		List<CombatTasks.Task> tasks = CaFixture.tasks();
		assertEquals(655, tasks.size());
		CombatTasks ca = new CombatTasks(tasks, new BitSet(), null, Collections.emptyMap(), null);
		int points = 0;
		for (int tier = 1; tier <= 6; tier++)
		{
			// Bundled tier sizes and the wiki's tier table (41, 64, 89, 166, 173, 122) both match the game.
			assertEquals(data.combatAchievementTasks(CombatTasks.tierName(tier)), ca.tasksIn(tier));
			points += ca.tasksIn(tier) * CombatTasks.POINTS[tier - 1];
		}
		// Every task done is the Grandmaster threshold: "requires completion of all currently available combat tasks".
		assertEquals(2697, points);
		assertEquals(CombatTasks.WIKI_THRESHOLDS[5], points);
		BitSet all = new BitSet();
		tasks.forEach(t -> all.set(t.getId()));
		assertEquals(2697, new CombatTasks(tasks, all, null, Collections.emptyMap(), null).pointsFromTasks());
	}

	@Test
	public void thresholdsComeFromTheGameAndFallBackToTheWiki()
	{
		CombatTasks unknown = new CombatTasks(CaFixture.tasks(), new BitSet(), new int[6], Collections.emptyMap(), null);
		assertEquals(41, unknown.threshold(1));
		assertEquals(2697, unknown.threshold(6));
		CombatTasks moved = new CombatTasks(CaFixture.tasks(), new BitSet(), new int[]{41, 169, 450, 1100, 1965, 2750},
			Collections.emptyMap(), null);
		assertEquals(450, moved.threshold(3));
		assertEquals(3, moved.nextTier(440));
		assertEquals(1, moved.nextTier(0));
		assertEquals(0, moved.nextTier(2750));
		assertEquals(0, moved.threshold(7));
	}

	@Test
	public void tierNamesRoundTrip()
	{
		assertEquals(3, CombatTasks.tierOf("hard"));
		assertEquals(6, CombatTasks.tierOf(" Grandmaster "));
		assertEquals(0, CombatTasks.tierOf("Legendary"));
		assertEquals(0, CombatTasks.tierOf(null));
		assertEquals("Elite", CombatTasks.tierName(4));
		assertEquals("", CombatTasks.tierName(0));
		assertEquals(3, CombatTasks.rewards(3).size());
		assertEquals("Ghommal's hilt 3", CombatTasks.rewards(3).get(0));
		assertTrue(CombatTasks.rewards(9).isEmpty());
	}

	@Test
	public void killCountTasksReadTheirNumberFromTheGamesDescription()
	{
		assertEquals(50, CaFixture.named("Vorkath Veteran").killsNeeded());
		assertEquals(10, CaFixture.named("Barrows Novice").killsNeeded());
		// A kill count task without a number, and a task of another type.
		assertEquals(0, CaFixture.named("Noxious Foe").killsNeeded());
		assertEquals(0, CaFixture.named("Stick 'em With the Pointy End").killsNeeded());
		assertEquals(1, CaFixture.named("Noxious Foe").getPoints());
		assertEquals(4, CaFixture.named("Vorkath Veteran").getPoints());
	}

	@Test
	public void theNearestTasksAreAtTheBossesYouAlreadyFight()
	{
		CombatTasks ca = CaFixture.mid();
		List<CombatTasks.Near> near = ca.nearest(0, 8);
		assertEquals(8, near.size());
		for (CombatTasks.Near n : near)
		{
			assertFalse(ca.isDone(n.getTask()));
			// Every one of them is at a boss with a kill count here: nothing untouched outranks them.
			assertTrue(n.getTask() + " " + n.getWhy(), n.getKills() > 0);
		}
		CombatTasks.Near vorkath = null;
		for (CombatTasks.Near n : ca.nearest(0, 400))
		{
			if (n.getTask().getName().equals("Vorkath Veteran"))
			{
				vorkath = n;
			}
		}
		assertEquals("38 of 50 kills", vorkath.getWhy());
		assertEquals(0.76, vorkath.getProgress(), 1e-9);
		// Sorted nearest first.
		for (int i = 1; i < near.size(); i++)
		{
			assertTrue(near.get(i - 1).getScore() >= near.get(i).getScore());
		}
	}

	@Test
	public void withoutKillCountsTasksDoneAtAMonsterAndTheEasierTierLead()
	{
		BitSet done = new BitSet();
		done.set(CaFixture.named("Barrows Novice").getId());
		CombatTasks ca = new CombatTasks(CaFixture.tasks(), done, null, Collections.emptyMap(), null);
		CombatTasks.Near first = ca.nearest(0, 1).get(0);
		assertEquals("Barrows", first.getTask().getMonster());
		assertTrue(first.getWhy(), first.getWhy().endsWith("Barrows tasks done"));
		// Only one tier when asked.
		for (CombatTasks.Near n : ca.nearest(4, 20))
		{
			assertEquals(4, n.getTask().getTier());
		}
		// With nothing done anywhere, an Easy task leads and says where it is.
		CombatTasks fresh = new CombatTasks(CaFixture.tasks(), new BitSet(), null, Collections.emptyMap(), null);
		CombatTasks.Near lead = fresh.nearest(0, 1).get(0);
		assertEquals(1, lead.getTask().getTier());
		assertEquals(lead.getTask().hasMonster() ? lead.getTask().getMonster() : "Any monster", lead.getWhy());
		assertEquals(-1, lead.getKills());
	}

	@Test
	public void tasksToEarnCountsTheNearestUntilThePointsAreMet()
	{
		CombatTasks ca = CaFixture.mid();
		assertEquals(0, ca.tasksToEarn(0));
		int need = 436 - ca.pointsFromTasks();
		int n = ca.tasksToEarn(need);
		assertTrue(n > 0);
		int sum = 0;
		for (CombatTasks.Near near : ca.nearest(0, n))
		{
			sum += near.getTask().getPoints();
		}
		assertTrue(sum >= need);
		assertEquals(-1, ca.tasksToEarn(100_000));
	}

	@Test
	public void byMonsterCountsDoneAndAll()
	{
		Map<String, int[]> m = CaFixture.mid().byMonster();
		int[] barrows = m.get("Barrows");
		assertTrue(barrows[0] > 0 && barrows[0] <= barrows[1]);
		int total = 0;
		for (int[] c : m.values())
		{
			total += c[1];
		}
		assertEquals(655, total);
	}

	@Test
	public void killCountsReadBossPilotsLogbookAndRuneLitesCounts()
	{
		Gson gson = new Gson();
		// Boss Pilot's logbookV1 as its Logbook class saves it.
		String logbook = "{\"version\":1,\"bosses\":{\"vorkath\":{\"kc\":38,\"best\":72.0,\"trips\":[]},"
			+ "\"dks\":{\"kc\":400,\"trips\":[]},\"zulrah\":{\"kc\":-1,\"trips\":[]},\"mole\":{\"kc\":10}}}";
		Map<String, Integer> runelite = new HashMap<>();
		runelite.put("giant mole", 64);
		runelite.put("barrows chests", 212);
		runelite.put("theatre of blood hard mode", 3);
		KillCounts.Read read = KillCounts.read(Arrays.asList("Vorkath", "Giant Mole", "Barrows", "Dagannoth Rex",
			"Zulrah", "Theatre of Blood: Hard Mode", "Other"), gson, logbook, runelite::get);
		assertEquals(Integer.valueOf(38), read.getKills().get("Vorkath"));
		// The higher of the two.
		assertEquals(Integer.valueOf(64), read.getKills().get("Giant Mole"));
		assertEquals(Integer.valueOf(212), read.getKills().get("Barrows"));
		assertEquals(Integer.valueOf(3), read.getKills().get("Theatre of Blood: Hard Mode"));
		// Boss Pilot counts the kings together: no single king takes that count.
		assertNull(read.getKills().get("Dagannoth Rex"));
		assertNull(read.getKills().get("Zulrah"));
		assertEquals("Boss Pilot and RuneLite", read.getSource());

		assertTrue(KillCounts.read(Arrays.asList("Vorkath"), gson, "{damaged", null).getKills().isEmpty());
		assertTrue(KillCounts.read(Arrays.asList("Vorkath"), gson, "{\"version\":2,\"bosses\":{\"vorkath\":{\"kc\":5}}}", null)
			.getKills().isEmpty());
		assertNull(KillCounts.read(Arrays.asList("Vorkath"), gson, null, k -> null).getSource());
		assertEquals("RuneLite", KillCounts.read(Arrays.asList("Vorkath"), gson, null, k -> 7).getSource());
	}

	@Test
	public void aTierGoalPlansFromPointsAndThreshold()
	{
		Planner planner = new Planner(data);
		CombatTasks ca = CaFixture.mid();
		int points = ca.pointsFromTasks();
		assertTrue(points > 169 && points < 436);
		AccountSnapshot account = CaFixture.account(TestAccounts.mid(data), ca).build();
		Plan hard = planner.plan(Goal.combat("Hard"), account);
		assertEquals("Hard combat achievements", hard.getTitle());
		assertFalse(hard.isComplete());
		assertEquals(points / 436.0, hard.getProgress(), 1e-9);
		assertEquals(Step.Kind.COMBAT_TASKS, hard.getNext().getKind());
		assertEquals("Earn " + (436 - points) + " more points for Hard", hard.getNext().getTitle());
		assertTrue(hard.getNext().getDetail(), hard.getNext().getDetail().startsWith("Nearest: "));
		assertEquals(0, hard.getHoursLeft(), 0);
		assertTrue(hard.getPath().isEmpty());

		Plan medium = planner.plan(Goal.combat("Medium"), account);
		assertTrue(medium.isComplete());
		assertNull(medium.getNext());
		// After Medium comes the next tier the points do not reach.
		assertEquals(Goal.combat("Hard"), Chains.next(planner, Goal.combat("Medium"), account, true));

		Plan out = planner.plan(Goal.combat("Hard"), AccountSnapshot.empty());
		assertFalse(out.isComplete());
		assertEquals(Step.Status.UNKNOWN, out.getNext().getStatus());

		assertFalse(PathSolver.solves(Goal.combat("Hard")));
		GoalBoard board = GoalBoard.of(planner, Arrays.asList(Goal.combat("Hard")), account);
		assertEquals("combat", board.get(0).type);
		assertEquals("ca:Hard", board.key(0));
		assertEquals(hard.getNext().getTitle(), board.next(0));
		assertTrue(board.skills(0).isEmpty());
	}

	@Test
	public void tierGoalKeys()
	{
		assertEquals("ca:Hard", Goal.combat("hard").key());
		assertEquals(Goal.combat("Hard"), Goal.fromKey("ca:hard"));
		assertNull(Goal.fromKey("ca:Legendary"));
		assertNull(Goal.combat("Legendary"));
		GoalStore store = new GoalStore();
		store.load(Arrays.asList("quest:Regicide", "ca:Elite", "ca:", "ca:nope"));
		assertEquals(Arrays.asList("quest:Regicide", "ca:Elite"), store.keys());
	}

	@Test
	public void theSnapshotChangesWhenATaskIsDone()
	{
		CombatTasks before = CaFixture.mid();
		BitSet more = new BitSet();
		for (CombatTasks.Task t : CaFixture.tasks())
		{
			if (before.isDone(t))
			{
				more.set(t.getId());
			}
		}
		more.set(CaFixture.named("Vorkath Veteran").getId());
		CombatTasks after = new CombatTasks(CaFixture.tasks(), more, null, before.allKills(), before.getKillSource());
		AccountSnapshot a = new AccountSnapshot.Builder().loggedIn(true).combatTasks(before).build();
		AccountSnapshot b = a.toBuilder().combatTasks(after).build();
		assertTrue(a.structureSignature() != b.structureSignature());
		assertEquals(a.structureSignature(), a.toBuilder().build().structureSignature());
	}
}
