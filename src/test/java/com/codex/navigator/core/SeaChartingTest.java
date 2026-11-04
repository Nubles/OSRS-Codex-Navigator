package com.codex.navigator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.TestAccounts;
import com.codex.navigator.data.CodexData;
import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

/** The bundled chart list against its sources, what is done and nearest, and what is learned from charting. */
public class SeaChartingTest
{
	private final Gson gson = new Gson();

	private static SeaCharting.Chart find(String taskStart)
	{
		for (SeaCharting.Chart c : SeaCharting.charts())
		{
			if (c.getTask().startsWith(taskStart))
			{
				return c;
			}
		}
		throw new AssertionError("no chart " + taskStart);
	}

	@Test
	public void everyChartIsBundled()
	{
		// OSRS Wiki, Sea charting: "full completion of all 358 charting tasks".
		assertEquals(358, SeaCharting.charts().size());
		assertEquals(SeaCharting.TOTAL, SeaCharting.charts().size());
		// One varbit a task in RuneLite's gameval: 94 GENERIC, 57 SPYGLASS, 65 DRINK_CRATE, 54 CURRENT_DUCK,
		// 57 MERMAID_GUIDE and 31 WEATHER_TROLL, 358 in all.
		assertEquals(94, SeaCharting.total(SeaCharting.Kind.GENERIC));
		assertEquals(57, SeaCharting.total(SeaCharting.Kind.SPYGLASS));
		assertEquals(65, SeaCharting.total(SeaCharting.Kind.CRATE));
		assertEquals(54, SeaCharting.total(SeaCharting.Kind.CURRENT));
		assertEquals(57, SeaCharting.total(SeaCharting.Kind.DIVING));
		assertEquals(31, SeaCharting.total(SeaCharting.Kind.WEATHER));
		assertEquals(70, SeaCharting.seas().size());
		assertEquals(6, SeaCharting.oceans().size());
	}

	@Test
	public void eachVarbitIsTheGamesOwnForItsKind() throws Exception
	{
		Map<Integer, String> names = new HashMap<>();
		for (Field f : VarbitID.class.getFields())
		{
			if (Modifier.isStatic(f.getModifiers()) && f.getType() == int.class)
			{
				names.putIfAbsent(f.getInt(null), f.getName());
			}
		}
		Map<SeaCharting.Kind, String> prefix = new HashMap<>();
		prefix.put(SeaCharting.Kind.GENERIC, "SAILING_CHARTING_GENERIC_");
		prefix.put(SeaCharting.Kind.SPYGLASS, "SAILING_CHARTING_SPYGLASS_");
		prefix.put(SeaCharting.Kind.CRATE, "SAILING_CHARTING_DRINK_CRATE_");
		prefix.put(SeaCharting.Kind.CURRENT, "SAILING_CHARTING_CURRENT_DUCK_");
		prefix.put(SeaCharting.Kind.DIVING, "SAILING_CHARTING_MERMAID_GUIDE_");
		prefix.put(SeaCharting.Kind.WEATHER, "SAILING_CHARTING_WEATHER_TROLL_");
		Set<Integer> seen = new HashSet<>();
		for (SeaCharting.Chart c : SeaCharting.charts())
		{
			assertTrue("twice: " + c.getVarbit(), seen.add(c.getVarbit()));
			String name = names.get(c.getVarbit());
			assertNotNull("no gameval varbit " + c.getVarbit(), name);
			assertTrue(name + " for " + c.getKind(), name.startsWith(prefix.get(c.getKind())));
			assertTrue(name, name.endsWith("_COMPLETE"));
			assertEquals(c, SeaCharting.byVarbit(c.getVarbit()));
		}
		assertEquals(VarbitID.SAILING_CHARTING_GENERIC_BOARD_PORT_SARIM_COMPLETE, SeaCharting.charts().get(0).getVarbit());
		assertEquals("Look at the port task board in Port Sarim.", SeaCharting.charts().get(0).getTask());
	}

	@Test
	public void levelsAndTasksFollowTheWiki()
	{
		for (SeaCharting.Chart c : SeaCharting.charts())
		{
			// No task asks less than its tool, or more than "every charting task requires 78 Sailing".
			assertTrue(c.getTask(), c.getLevel() >= c.getKind().getLevel());
			assertTrue(c.getTask(), c.getLevel() <= SeaCharting.FULL_LEVEL);
		}
		// Sea pages, quoted: tools set most levels, a sea sets some.
		assertEquals(24, find("Use your spyglass to get a good view of a cave in the Kharazi Jungle").getLevel());
		assertEquals(47, find("Find the nest of a fearless bird on the Onyx Crest.").getLevel());
		assertEquals(51, find("Find an abandoned camp on Anglers' Retreat.").getLevel());
		assertEquals(61, find("Use your spyglass to get a good view of the surrounding sea from the Tear of the Soul.").getLevel());
		// "To be able to do this task at 38 Sailing, one must board another player's boat" (Quest Helper says 40).
		assertEquals(38, find("With help from a mermaid guide, document the water depth south west of Corsair Cove.").getLevel());
		assertEquals(78, find("With help from a mermaid guide, document the water depth north west of Miscellania.").getLevel());
		assertEquals(22, find("Test the currents south of Lacerta Falls.").getLevel());
		assertTrue(find("Test the currents south of Lacerta Falls.").isLevelConfirmed());
		assertFalse(find("Find an interesting sign south east of Land's End.").isLevelConfirmed());
		assertEquals("Great Sound", find("Find an interesting sign south east of Land's End.").getSea().getName());
		// Tools (OSRS Wiki, Sea charting, Tools table).
		assertEquals(1, SeaCharting.Kind.GENERIC.getLevel());
		assertEquals(1, SeaCharting.Kind.SPYGLASS.getLevel());
		assertEquals(12, SeaCharting.Kind.CRATE.getLevel());
		assertEquals(22, SeaCharting.Kind.CURRENT.getLevel());
		assertEquals(38, SeaCharting.Kind.DIVING.getLevel());
		assertEquals(57, SeaCharting.Kind.WEATHER.getLevel());
		int unconfirmed = 0;
		for (SeaCharting.Chart c : SeaCharting.charts())
		{
			unconfirmed += c.isLevelConfirmed() ? 0 : 1;
		}
		assertEquals(8, unconfirmed);
	}

	@Test
	public void onlyAOneIsDone()
	{
		int first = SeaCharting.charts().get(0).getVarbit();
		int second = SeaCharting.charts().get(1).getVarbit();
		BitSet done = SeaCharting.read(v -> v == first ? 1 : v == second ? 2 : 0);
		assertEquals(1, done.cardinality());
		assertTrue(done.get(0));
	}

	@Test
	public void nearestIsByDistanceAndLevel()
	{
		SeaCharting.Sea sarim = SeaCharting.charts().get(0).getSea();
		assertEquals("Bay of Sarim", sarim.getName());
		// Standing in the middle of the Bay of Sarim at level 1: its open charts come first.
		SeaCharting.View view = SeaCharting.View.of(new BitSet(), 0, 0, sarim.getX(), sarim.getY());
		List<SeaCharting.Near> near = view.nearest(1, 5);
		assertEquals(5, near.size());
		for (int i = 0; i < 5; i++)
		{
			SeaCharting.Chart c = near.get(i).getChart();
			assertTrue(c.getTask(), c.getLevel() <= 1);
			if (i > 0)
			{
				assertTrue(near.get(i - 1).getDistance() <= near.get(i).getDistance());
			}
		}
		assertEquals("Bay of Sarim", near.get(0).getChart().getSea().getName());
		assertEquals(0, near.get(0).getDistance());
		assertFalse(near.get(0).isLearned());
		// Nothing above the level is offered; the next level is named instead.
		int open1 = view.openAt(1);
		assertEquals(12, view.nextLevel(1));
		assertTrue(view.openAt(12) > open1);
		assertEquals(358, view.openAt(78));
		// Done charts drop out.
		BitSet done = new BitSet();
		done.set(near.get(0).getChart().getIndex());
		SeaCharting.View after = SeaCharting.View.of(done, 0, 0, sarim.getX(), sarim.getY());
		assertNotEquals(near.get(0).getChart(), after.nearest(1, 1).get(0).getChart());
		assertEquals(1, after.doneCount());
		assertEquals(357, after.openAt(78));
	}

	private static void assertNotEquals(Object a, Object b)
	{
		assertFalse(a + " = " + b, a.equals(b));
	}

	@Test
	public void withNoPlaceKnownTheGamesOrderStands()
	{
		SeaCharting.View view = SeaCharting.View.of(new BitSet(), 0, 0, 0, 0);
		List<SeaCharting.Near> near = view.nearest(78, 3);
		assertEquals(-1, near.get(0).getDistance());
		assertEquals("Kharidian Sea", near.get(0).getChart().getSea().getName());
	}

	@Test
	public void chartingTeachesPlacesAndPace()
	{
		SeaCharting.Log log = new SeaCharting.Log();
		List<SeaCharting.Chart> charts = SeaCharting.charts();
		long t = 1_000_000;
		BitSet done = new BitSet();
		// Six charts three minutes apart: five gaps, fifteen minutes.
		for (int i = 0; i < 6; i++)
		{
			log.charted(charts.get(i), t + i * 180_000L, new int[]{3000 + i, 3200}, true);
			done.set(i);
		}
		SeaCharting.View view = log.view(true, done, true, 3000, 3200);
		assertEquals(20.0, view.getPerHour(), 0.01);
		assertEquals(6, view.placesLearned());
		assertEquals(3000, view.placeOf(charts.get(0))[0]);
		assertEquals((358 - 6) / 20.0, view.hoursLeft(), 0.01);
		// A long pause is not charting time.
		log.charted(charts.get(6), t + 6 * 180_000L + 3_600_000L, null, true);
		assertEquals(20.0, log.view(true, done, false, 0, 0).getPerHour(), 0.01);
		// Nor is the time away after a pause.
		log.pause();
		log.charted(charts.get(7), t + 9_000_000L, null, true);
		assertEquals(20.0, log.view(true, done, false, 0, 0).getPerHour(), 0.01);
		// Too little timed: no rate.
		SeaCharting.Log fresh = new SeaCharting.Log();
		fresh.charted(charts.get(0), t, null, true);
		fresh.charted(charts.get(1), t + 60_000, null, true);
		assertEquals(0, fresh.view(true, new BitSet(), false, 0, 0).getPerHour(), 0);
		// With learning off, places are still kept but nothing is timed.
		SeaCharting.Log off = new SeaCharting.Log();
		for (int i = 0; i < 8; i++)
		{
			off.charted(charts.get(i), t + i * 180_000L, new int[]{3100, 3100}, false);
		}
		assertEquals(0, off.view(true, new BitSet(), false, 0, 0).getPerHour(), 0);
		assertEquals(8, off.view(true, new BitSet(), false, 0, 0).placesLearned());
	}

	@Test
	public void aSeaIsWhereThePlayerChartedInIt()
	{
		SeaCharting.Log log = new SeaCharting.Log();
		SeaCharting.Chart first = SeaCharting.charts().get(0);
		log.charted(first, 0, new int[]{2000, 2000}, false);
		BitSet done = new BitSet();
		done.set(first.getIndex());
		SeaCharting.View view = log.view(true, done, true, 2000, 2000);
		List<SeaCharting.Near> near = view.nearest(1, 1);
		assertEquals(first.getSea(), near.get(0).getChart().getSea());
		assertTrue(near.get(0).isLearned());
		assertEquals(0, near.get(0).getDistance());
	}

	@Test
	public void theLogSavesAndKeepsWhatItCan()
	{
		SeaCharting.Log log = new SeaCharting.Log();
		List<SeaCharting.Chart> charts = SeaCharting.charts();
		for (int i = 0; i < 7; i++)
		{
			log.charted(charts.get(i), i * 200_000L, new int[]{3000, 3000 + i}, true);
		}
		assertTrue(log.takeChanged());
		assertFalse(log.takeChanged());
		String json = log.toJson(gson);
		SeaCharting.Log back = new SeaCharting.Log();
		assertEquals(2, back.load(gson, json));
		assertEquals(log.view(true, new BitSet(), false, 0, 0), back.view(true, new BitSet(), false, 0, 0));
		assertEquals(3006, back.view(true, new BitSet(), false, 0, 0).placeOf(charts.get(6))[1]);

		// One odd place is passed over and the rest kept; the value is reported as not read in full.
		String damaged = "{\"places\":{\"" + charts.get(0).getVarbit() + "\":[3000,3000],\"99\":[1,1],\""
			+ charts.get(1).getVarbit() + "\":[\"x\",2]},\"timed\":5,\"seconds\":900}";
		SeaCharting.Log partly = new SeaCharting.Log();
		assertEquals(1, partly.load(gson, damaged));
		assertEquals(1, partly.view(true, new BitSet(), false, 0, 0).placesLearned());
		assertEquals(20.0, partly.view(true, new BitSet(), false, 0, 0).getPerHour(), 0.01);
		assertEquals(0, new SeaCharting.Log().load(gson, "not json"));
		assertEquals(0, new SeaCharting.Log().load(gson, null));
		assertEquals(0, new SeaCharting.Log().load(gson, "{\"timed\":-1,\"seconds\":1e400}"));
	}

	@Test
	public void theGoalPlansToTheNearestChart()
	{
		CodexData data = CodexData.load(gson);
		Planner planner = new Planner(data);
		assertEquals(Goal.charting(), Goal.fromKey("charting:Sea charting"));
		assertEquals(Goal.charting(), Goal.fromKey("charting:sea charting"));
		assertNull(Goal.fromKey("charting:Something else"));
		assertFalse(PathSolver.solves(Goal.charting()));

		Plan out = planner.plan(Goal.charting(), AccountSnapshot.empty());
		assertFalse(out.isComplete());
		assertEquals(Step.Status.UNKNOWN, out.getNext().getStatus());

		SeaCharting.Sea sarim = SeaCharting.charts().get(0).getSea();
		BitSet done = new BitSet();
		done.set(0, 100);
		AccountSnapshot account = TestAccounts.mid(data)
			.skill(Skill.SAILING, 40, Experience.getXpForLevel(40))
			.charting(SeaCharting.View.of(done, 20, 2, sarim.getX(), sarim.getY())).build();
		Plan plan = planner.plan(Goal.charting(), account);
		assertFalse(plan.isComplete());
		assertEquals(100 / 358.0, plan.getProgress(), 0.001);
		assertEquals(Step.Kind.CHARTING, plan.getNext().getKind());
		assertEquals(Step.Status.READY, plan.getNext().getStatus());
		assertEquals(1, plan.getSkills().size());
		assertEquals(Skill.SAILING, plan.getSkills().get(0).getSkill());
		assertEquals(78, plan.getSkills().get(0).getTarget());
		assertTrue(plan.getHoursLeft() > 258 / 20.0);

		BitSet all = new BitSet();
		all.set(0, 358);
		AccountSnapshot finished = TestAccounts.mid(data).skill(Skill.SAILING, 80, Experience.getXpForLevel(80))
			.charting(SeaCharting.View.of(all, 0, 0, 0, 0)).build();
		assertTrue(planner.plan(Goal.charting(), finished).isComplete());

		// Everything open is charted: the next step is the level that opens more.
		BitSet low = new BitSet();
		for (SeaCharting.Chart c : SeaCharting.charts())
		{
			if (c.getLevel() <= 1)
			{
				low.set(c.getIndex());
			}
		}
		AccountSnapshot stuck = TestAccounts.mid(data).skill(Skill.SAILING, 5, Experience.getXpForLevel(5))
			.charting(SeaCharting.View.of(low, 0, 0, 0, 0)).build();
		Step next = planner.plan(Goal.charting(), stuck).getNext();
		assertEquals("Train Sailing to 12", next.getTitle());
	}
}
