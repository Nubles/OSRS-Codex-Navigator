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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Experience;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;

public class PathSolverTest
{
	// ---- A small world where order matters ----

	private static QuestDef quest(String name, Map<String, Integer> skills, String... prereqs)
	{
		QuestDef q = new QuestDef();
		q.name = name;
		q.points = 1;
		q.difficulty = "Intermediate";
		q.skills = skills;
		q.prereqs = Arrays.asList(prereqs);
		return q;
	}

	private static Map<String, Integer> needs(Object... pairs)
	{
		Map<String, Integer> out = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return out;
	}

	/**
	 * The goal needs Hill (50 Agility, no reward) and Grove (20 Thieving, 20,000 Agility experience). Training
	 * Thieving and doing Grove first banks the Agility reward, so less Agility is trained by hand.
	 */
	private static CodexData world()
	{
		List<QuestDef> quests = new ArrayList<>();
		quests.add(quest("Hill", needs("Agility", 50)));
		quests.add(quest("Grove", needs("Thieving", 20)));
		quests.add(quest("Summit", needs(), "Hill", "Grove"));
		QuestDef side = quest("Side Errand", needs());
		side.difficulty = "Novice";
		side.points = 2;
		quests.add(side);
		QuestDef gate = quest("Gate", needs());
		gate.questPoints = 5;
		quests.add(gate);
		QuestDef brawl = quest("Brawl", needs());
		brawl.combatLevel = 10;
		quests.add(brawl);
		DiaryDef diary = new DiaryDef();
		diary.name = "Valley Easy";
		diary.area = "Valley";
		diary.tier = "Easy";
		diary.skills = needs("Agility", 55);
		diary.quests = Collections.singletonList("Grove");
		CodexData data = new CodexData(quests, Collections.singletonList(diary), Collections.emptyMap());
		Map<String, Map<String, Double>> xp = new HashMap<>();
		xp.put("Grove", Collections.singletonMap("Agility", 20_000.0));
		data.setQuestXp(xp);
		return data;
	}

	private static AccountSnapshot account(CodexData data, int qp)
	{
		AccountSnapshot.Builder b = new AccountSnapshot.Builder().loggedIn(true).questPoints(qp);
		for (QuestDef q : data.quests())
		{
			b.quest(q.name, QuestState.NOT_STARTED);
		}
		return b.build();
	}

	private static List<String> titles(PathSolver.Solution s)
	{
		List<String> out = new ArrayList<>();
		for (PathSolver.Leg leg : s.getLegs())
		{
			out.add(leg.getTitle());
		}
		return out;
	}

	@Test
	public void rewardsAreBankedBeforeTrainingByHand()
	{
		CodexData data = world();
		Planner planner = new Planner(data);
		PathSolver.Solution s = new PathSolver(planner).solve(Goal.quest("Summit"), account(data, 0));
		assertNotNull(s);
		assertEquals(Arrays.asList("Train Thieving to 20", "Grove", "Train Agility to 50", "Hill", "Summit"), titles(s));
		PathSolver.Leg agility = s.getLegs().get(2);
		// Grove's 20,000 experience is level 35 or so: training starts there, not at 1.
		assertEquals(Experience.getLevelForXp(20_000), agility.getFrom());
		double byHand = Effort.trainingHours(Skill.AGILITY, 0, 50) + Effort.trainingHours(Skill.THIEVING, 0, 20);
		assertEquals(Effort.trainingHours(Skill.AGILITY, 20_000, 50) + Effort.trainingHours(Skill.THIEVING, 0, 20),
			s.getTrainHours(), 1e-9);
		assertTrue(s.getTrainHours() < byHand);
		// The planner trains every level from where it stands; the order saves the difference.
		assertEquals(byHand - s.getTrainHours(), s.getSaved(), 1e-6);
		assertEquals(20_000, s.getXpBanked(), 1e-9);
		assertEquals(Collections.singletonMap(Skill.AGILITY, 20_000.0), s.getLegs().get(1).getXp());
	}

	@Test
	public void theCriticalPathEndsAtTheGoalAndNamesTheGatingSkill()
	{
		CodexData data = world();
		PathSolver.Solution s = new PathSolver(new Planner(data)).solve(Goal.quest("Summit"), account(data, 0));
		List<PathSolver.Leg> critical = s.critical();
		assertEquals("Summit", critical.get(critical.size() - 1).getTitle());
		// The long wait is Thieving, then Grove's reward, then Agility to 50 and Hill: one chain.
		assertEquals(Arrays.asList("Train Thieving to 20", "Grove", "Train Agility to 50", "Hill", "Summit"),
			titlesOf(critical));
		assertEquals(Skill.AGILITY, s.getGates().get(0).getSkill());
		assertEquals(Collections.singletonList("Hill"), s.getGates().get(0).getOpens());
		assertEquals(s.getHours(), s.criticalHours(), 1e-9);
	}

	private static List<String> titlesOf(List<PathSolver.Leg> legs)
	{
		List<String> out = new ArrayList<>();
		for (PathSolver.Leg leg : legs)
		{
			out.add(leg.getTitle());
		}
		return out;
	}

	@Test
	public void aDiarysOwnLevelsComeLast()
	{
		CodexData data = world();
		PathSolver.Solution s = new PathSolver(new Planner(data)).solve(Goal.diary("Valley Easy"), account(data, 0));
		assertEquals(Arrays.asList("Train Thieving to 20", "Grove", "Train Agility to 55"), titles(s));
		assertNull(s.getLegs().get(2).getOpens());
	}

	@Test
	public void aBoostOpensAQuestWithoutTraining()
	{
		CodexData data = world();
		Map<String, Map<String, Integer>> boosts = new HashMap<>();
		boosts.put("Hill", Collections.singletonMap("Agility", 50));
		data.setBoostable(boosts);
		// A summer pie gives Agility +5, so 46 starts Hill at 50.
		AccountSnapshot.Builder b = new AccountSnapshot.Builder().loggedIn(true).skill(Skill.AGILITY, 46,
			Experience.getXpForLevel(46));
		for (QuestDef q : data.quests())
		{
			b.quest(q.name, QuestState.NOT_STARTED);
		}
		PathSolver.Solution s = new PathSolver(new Planner(data)).solve(Goal.quest("Summit"), b.build());
		assertEquals("Hill", s.getLegs().get(0).getTitle());
		assertTrue(s.getLegs().get(0).isBoosted());
		for (PathSolver.Leg leg : s.getLegs())
		{
			assertTrue(leg.getSkill() != Skill.AGILITY);
		}
	}

	@Test
	public void questPointsComeFromTheQuickestQuestsOutsideTheGoal()
	{
		CodexData data = world();
		PathSolver.Solution s = new PathSolver(new Planner(data)).solve(Goal.quest("Gate"), account(data, 3));
		// Side Errand is 2 points in a novice's time: the best points per hour, so it goes first.
		assertEquals(PathSolver.Leg.Kind.POINTS, s.getLegs().get(0).getKind());
		assertEquals("Side Errand", s.getLegs().get(0).getTitle());
		PathSolver.Leg last = s.getLegs().get(s.getLegs().size() - 1);
		assertEquals("Gate", last.getTitle());
		int points = 0;
		for (PathSolver.Leg leg : s.getLegs())
		{
			if (leg.getKind() == PathSolver.Leg.Kind.POINTS)
			{
				points += data.quest(leg.getTitle()).points;
			}
		}
		assertEquals(2, points);
	}

	@Test
	public void aCombatLevelIsTrainedInMelee()
	{
		CodexData data = world();
		PathSolver.Solution s = new PathSolver(new Planner(data)).solve(Goal.quest("Brawl"), account(data, 0));
		assertEquals("Brawl", s.getLegs().get(s.getLegs().size() - 1).getTitle());
		for (int i = 0; i < s.getLegs().size() - 1; i++)
		{
			PathSolver.Leg leg = s.getLegs().get(i);
			assertEquals(PathSolver.Leg.Kind.TRAIN, leg.getKind());
			assertTrue(Arrays.asList(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS).contains(leg.getSkill()));
		}
	}

	@Test
	public void levelGoalsAndFinishedGoalsAreNotSolved()
	{
		CodexData data = world();
		PathSolver solver = new PathSolver(new Planner(data));
		assertNull(solver.solve(Goal.level(Skill.AGILITY, 50), account(data, 0)));
		AccountSnapshot.Builder b = new AccountSnapshot.Builder().loggedIn(true);
		for (QuestDef q : data.quests())
		{
			b.quest(q.name, QuestState.FINISHED);
		}
		assertNull(solver.solve(Goal.quest("Summit"), b.build()));
	}

	// ---- The bundled data ----

	@Test
	public void theQuestCapeSolvesQuicklyAndRespectsEveryRequirement()
	{
		CodexData data = CodexData.load(new Gson());
		Planner planner = new Planner(data);
		AccountSnapshot account = account(data, 0);
		long start = System.nanoTime();
		PathSolver.Solution s = new PathSolver(planner).solve(Goal.QUEST_CAPE, account);
		long ms = (System.nanoTime() - start) / 1_000_000;
		assertNotNull(s);
		assertTrue(ms + " ms", ms < 2000);
		Set<String> done = new HashSet<>();
		Map<Skill, Integer> trained = new HashMap<>();
		int checks = 0;
		for (PathSolver.Leg leg : s.getLegs())
		{
			if (leg.getKind() == PathSolver.Leg.Kind.CHECK)
			{
				checks++;
				continue;
			}
			if (leg.getKind() == PathSolver.Leg.Kind.TRAIN)
			{
				trained.merge(leg.getSkill(), leg.getTo(), Math::max);
				continue;
			}
			QuestDef q = data.quest(leg.getTitle());
			for (String prereq : q.prereqs)
			{
				assertTrue(q.name + " before " + prereq, done.contains(prereq));
			}
			done.add(q.name);
		}
		assertEquals(0, checks);
		for (QuestDef q : data.questCapeQuests())
		{
			assertTrue(q.name, done.contains(q.name));
		}
		assertTrue(s.getHours() > 100 && Double.isFinite(s.getHours()));
		assertTrue("quest rewards should bank experience", s.getXpBanked() > 100_000);
		assertFalse(s.critical().isEmpty());
		assertTrue(s.getGates().size() > 5);
	}

	@Test
	public void questRewardsNameRealQuestsAndSkills()
	{
		CodexData data = CodexData.load(new Gson());
		int quests = 0;
		for (QuestDef q : data.quests())
		{
			for (Map.Entry<String, Double> e : data.questXp(q.name).entrySet())
			{
				assertNotNull(q.name + ": " + e.getKey(), Planner.skill(e.getKey()));
				assertTrue(e.getValue() > 0);
			}
			quests += data.questXp(q.name).isEmpty() ? 0 : 1;
		}
		assertTrue(quests > 100);
		// OSRS Wiki, Regicide: "13,750 Agility experience"; Quest experience rewards: Holy Grail 15,300 Defence.
		assertEquals(13_750, data.questXp("Regicide").get("Agility"), 0);
		assertEquals(15_300, data.questXp("Holy Grail").get("Defence"), 0);
		assertEquals(10_000, data.questXp("Recipe for Disaster - King Awowogei").get("Agility"), 0);
		assertEquals(4_000, data.questXp("Recipe for Disaster - Culinaromancer").get("Hitpoints"), 0);
	}
}
