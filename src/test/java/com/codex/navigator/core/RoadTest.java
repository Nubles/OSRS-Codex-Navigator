package com.codex.navigator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class RoadTest
{
	private static Step quest(String name, Step.Status status)
	{
		return new Step(Step.Kind.QUEST, status, name, "", name, null);
	}

	private static Plan plan(Goal goal, Step... path)
	{
		List<Step> steps = new ArrayList<>();
		Collections.addAll(steps, path);
		return new Plan(goal, goal.getName(), "", false, 0.5, steps, Collections.emptyList(), null, 0, steps.size());
	}

	@Test
	public void theGoalsOwnQuestIsTheFlag()
	{
		Road road = Road.of(plan(Goal.quest("Regicide"),
			quest("Plague City", Step.Status.DONE), quest("Biohazard", Step.Status.DONE),
			quest("Underground Pass", Step.Status.DONE), quest("Regicide", Step.Status.READY)), false, 6);
		assertEquals(3, road.getTotal());
		assertEquals(3, road.getDone());
		// Two finished stops are drawn behind you; the third is counted.
		assertEquals(2, road.getStops().size());
		assertEquals(1, road.getDoneBefore());
		assertEquals("Biohazard", road.getStops().get(0).getQuest());
		assertEquals(0, road.getMoreAfter());
	}

	@Test
	public void aLongRoadKeepsAWindowAroundTheNextStop()
	{
		List<Step> path = new ArrayList<>();
		for (int i = 0; i < 40; i++)
		{
			path.add(quest("Done " + i, Step.Status.DONE));
		}
		path.add(quest("Holy Grail", Step.Status.IN_PROGRESS));
		path.add(quest("Shilo Village", Step.Status.READY));
		for (int i = 0; i < 150; i++)
		{
			path.add(quest("Later " + i, Step.Status.BLOCKED));
		}
		Road road = Road.of(plan(Goal.QUEST_CAPE, path.toArray(new Step[0])), false, 6);
		assertEquals(6, road.getStops().size());
		assertEquals(38, road.getDoneBefore());
		assertEquals(Road.State.NEXT, road.getStops().get(2).getState());
		assertTrue(road.getStops().get(2).isStarted());
		assertEquals(Road.State.READY, road.getStops().get(3).getState());
		assertEquals(Road.State.BLOCKED, road.getStops().get(4).getState());
		// 152 unfinished, 4 of them drawn.
		assertEquals(148, road.getMoreAfter());
		assertEquals(192, road.getTotal());
	}

	@Test
	public void theNextStopIsTheQuestUnderWayBeforeOneThatCanStart()
	{
		Road road = Road.of(plan(Goal.QUEST_CAPE, quest("A", Step.Status.READY), quest("B", Step.Status.BLOCKED),
			quest("C", Step.Status.IN_PROGRESS)), false, 6);
		// The quest under way is the next stop and leads; the others still follow, none lost.
		assertEquals("C", road.getStops().get(0).getQuest());
		assertEquals(Road.State.NEXT, road.getStops().get(0).getState());
		assertEquals("A", road.getStops().get(1).getQuest());
		assertEquals(Road.State.READY, road.getStops().get(1).getState());
		assertEquals(Road.State.BLOCKED, road.getStops().get(2).getState());
		assertEquals(0, road.getMoreAfter());
	}

	@Test
	public void thePlannersNextStepLeadsTheRoad()
	{
		// The focus card says "Start Recruitment Drive"; the road must not point somewhere else.
		List<Step> path = new ArrayList<>();
		path.add(quest("The Dig Site", Step.Status.READY));
		path.add(quest("Recruitment Drive", Step.Status.READY));
		path.add(quest("Desert Treasure II - The Fallen Empire", Step.Status.BLOCKED));
		Plan p = new Plan(Goal.quest("Desert Treasure II - The Fallen Empire"), "DT2", "", false, 0.3, path,
			Collections.emptyList(), path.get(1), 0, 3);
		Road road = Road.of(p, false, 6);
		assertEquals("Recruitment Drive", road.getStops().get(0).getQuest());
		assertEquals(Road.State.NEXT, road.getStops().get(0).getState());
		assertEquals(Road.State.READY, road.getStops().get(1).getState());
	}

	@Test
	public void hazardsAreMarkedOnlyOnOneLife()
	{
		Plan p = plan(Goal.quest("Legends' Quest"), quest("Dragon Slayer I", Step.Status.READY), quest("Shilo Village", Step.Status.READY),
			quest("Legends' Quest", Step.Status.BLOCKED));
		assertEquals("Boss", Road.of(p, true, 6).getStops().get(0).getHazard());
		assertNull(Road.of(p, false, 6).getStops().get(0).getHazard());
	}

	@Test
	public void noRoadWithoutQuestsOnTheWay()
	{
		assertNull(Road.of(plan(Goal.quest("Cook's Assistant"), quest("Cook's Assistant", Step.Status.READY)), false, 6));
		assertNull(Road.of(plan(Goal.level(net.runelite.api.Skill.AGILITY, 70)), false, 6));
		assertNull(Road.of(null, false, 6));
		Plan done = new Plan(Goal.QUEST_CAPE, "Cape", "", true, 1, Collections.singletonList(quest("A", Step.Status.DONE)),
			Collections.emptyList(), null, 1, 1);
		assertNull(Road.of(done, false, 6));
		// A quest goal with one quest before it is still a road: one stop, then the flag.
		Road one = Road.of(plan(Goal.quest("Biohazard"), quest("Plague City", Step.Status.READY), quest("Biohazard", Step.Status.BLOCKED)), false, 6);
		assertEquals(1, one.getStops().size());
		assertFalse(one.getStops().get(0).isStarted());
	}
}
