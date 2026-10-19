package com.codex.navigator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.QuestDef;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

/** Boostable requirements, the learned training pace, and the plan for a fixed amount of play. */
public class BoostPaceSessionTest
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

	/** Everything Regicide asks for except its 56 Agility. */
	private AccountSnapshot.Builder readyForRegicideBut(int agility)
	{
		AccountSnapshot.Builder b = fresh();
		for (String q : planner.plan(Goal.quest("Regicide"), b.build()).getPath().stream()
			.map(Step::getQuest).filter(q -> q != null && !q.equals("Regicide")).toArray(String[]::new))
		{
			b.quest(q, QuestState.FINISHED);
		}
		for (Skill skill : Skill.values())
		{
			level(b, skill, 60);
		}
		return level(b, Skill.AGILITY, agility);
	}

	@Test
	public void boostDataMatchesTheRequirementsItMarks()
	{
		// Every mark names a real quest's real requirement, so a data refresh cannot leave a stale one behind.
		int marks = 0;
		for (QuestDef q : data.quests())
		{
			for (Map.Entry<String, Integer> e : q.skills.entrySet())
			{
				if (data.boostable(q.name, e.getKey(), e.getValue()))
				{
					marks++;
					assertNotNull(e.getKey(), Planner.skill(e.getKey()));
				}
			}
		}
		assertTrue("only " + marks + " boostable requirements", marks > 60);
		assertTrue(data.boostable("Regicide", "Agility", 56));
		// Quest Helper does not mark Regicide's Crafting, so the Codex never calls it boostable.
		assertFalse(data.boostable("Regicide", "Crafting", 10));
		// A mark is for one level: the same skill at another level is not covered.
		assertFalse(data.boostable("Regicide", "Agility", 60));
	}

	@Test
	public void boostsAddTheirFlatAmountAndShareOfTheLevel()
	{
		assertEquals(5, Boosts.of(Skill.AGILITY).amount(52));
		// Super potions add 5 and 15% of the level, rounded down.
		assertEquals(5 + 9, Boosts.of(Skill.ATTACK).amount(60));
		assertEquals(4 + 6, Boosts.of(Skill.RANGED).amount(65));
		assertTrue(Boosts.reaches(Skill.AGILITY, 51, 56));
		assertFalse(Boosts.reaches(Skill.AGILITY, 50, 56));
		// No everyday boost for Prayer, so nothing is ever called boost-ready there.
		assertNull(Boosts.of(Skill.PRAYER));
		assertFalse(Boosts.reaches(Skill.PRAYER, 42, 43));
		assertEquals("Summer pie for Agility 56", Boosts.brief(Skill.AGILITY, 56));
		assertEquals("Boost Agility 52 to 56 with a summer pie (+5)", Boosts.describe(Skill.AGILITY, 52, 56));
	}

	@Test
	public void aQuestHeldBackOnlyByABoostableLevelIsReadyWithThePie()
	{
		AccountSnapshot account = readyForRegicideBut(52).build();
		QuestDef regicide = data.quest("Regicide");
		Step step = planner.questStep(regicide, account);
		assertEquals(Step.Status.READY, step.getStatus());
		assertEquals("Boost Agility 52 to 56 with a summer pie (+5)", step.getDetail());

		Plan plan = planner.plan(Goal.quest("Regicide"), account);
		assertEquals(Step.Kind.QUEST, plan.getNext().getKind());
		assertEquals("Regicide", plan.getNext().getQuest());
		// No training hours for a level a pie covers: only the quest itself is left.
		assertEquals(Effort.questHours(regicide), plan.getHoursLeft(), 1e-9);
		SkillGap agility = plan.getSkills().stream().filter(g -> g.getSkill() == Skill.AGILITY).findFirst().get();
		assertTrue(agility.isBoostable());

		// It is listed under boosts, not as something to train for.
		assertTrue(names(recommender.boostNow(account, Collections.emptyList(), true, 50)).contains("Regicide"));
		assertFalse(names(recommender.almostThere(account, true, 50)).contains("Regicide"));
		// And the route has no Agility training in it.
		Route route = Route.build(planner, Arrays.asList(Goal.quest("Regicide")), account);
		for (Route.Leg leg : route.getLegs())
		{
			assertFalse(leg.getStep().getSkill() == Skill.AGILITY);
		}
	}

	@Test
	public void anIronmanIsOnlyToldToUseABoostTheyCanMake()
	{
		// An ironman cannot buy a summer pie; at 60 Cooking they cannot bake one (95), so Regicide needs Agility.
		AccountSnapshot iron = readyForRegicideBut(52).ironman(true).build();
		assertEquals(Step.Status.BLOCKED, planner.questStep(data.quest("Regicide"), iron).getStatus());
		assertEquals("Train Agility to 56", planner.plan(Goal.quest("Regicide"), iron).getNext().getTitle());
		assertTrue(recommender.boostNow(iron, Collections.emptyList(), true, 50).stream()
			.noneMatch(p -> p.getQuest().name.equals("Regicide")));

		// With 95 Cooking they bake their own, and the quest is ready again.
		AccountSnapshot baker = level(readyForRegicideBut(52).ironman(true), Skill.COOKING, 95).build();
		assertEquals(Step.Status.READY, planner.questStep(data.quest("Regicide"), baker).getStatus());

		// A dwarven stout is sold in shops, so an ironman can always use it.
		assertTrue(Boosts.reaches(Skill.MINING, 49, 50, iron));
		assertFalse(Boosts.reaches(Skill.AGILITY, 52, 56, iron));
		assertTrue(Boosts.reaches(Skill.AGILITY, 52, 56, readyForRegicideBut(52).build()));
	}

	@Test
	public void aBoostThatFallsShortStillMeansTraining()
	{
		AccountSnapshot account = readyForRegicideBut(50).build();
		Step step = planner.questStep(data.quest("Regicide"), account);
		assertEquals(Step.Status.BLOCKED, step.getStatus());
		Plan plan = planner.plan(Goal.quest("Regicide"), account);
		assertEquals("Train Agility to 56", plan.getNext().getTitle());
		assertTrue(plan.getHoursLeft() > Effort.questHours(data.quest("Regicide")));
		assertTrue(recommender.boostNow(account, Collections.emptyList(), true, 50).stream()
			.noneMatch(p -> p.getQuest().name.equals("Regicide")));
	}

	@Test
	public void aLevelAnotherQuestNeedsUnboostedIsNotCoveredByTheBoost()
	{
		// Two goals share Agility: if one of them may not be boosted, the level must be trained.
		Map<String, Map<String, Integer>> marks = new HashMap<>();
		marks.put("Regicide", Collections.singletonMap("Agility", 56));
		data.setBoostable(marks);
		AccountSnapshot account = readyForRegicideBut(52).build();
		assertTrue(planner.plan(Goal.quest("Regicide"), account).getSkills().stream()
			.anyMatch(SkillGap::isBoostable));
		data.setBoostable(Collections.emptyMap());
		assertTrue(planner.plan(Goal.quest("Regicide"), account).getSkills().stream()
			.noneMatch(SkillGap::isBoostable));
	}

	private static List<String> names(List<Recommender.Pick> picks)
	{
		return Arrays.asList(picks.stream().map(p -> p.getQuest().name).toArray(String[]::new));
	}

	// ---- Pace ----

	/** Feeds steady training: one drop every {@code everySeconds} for {@code minutes}, at {@code factor} times the table. */
	private static long train(Pace pace, Skill skill, int startXp, double factor, int minutes, int everySeconds, long start)
	{
		long now = start;
		double xp = startXp;
		pace.observe(skill, (int) xp, now);
		for (int s = everySeconds; s <= minutes * 60; s += everySeconds)
		{
			int level = Experience.getLevelForXp((int) xp);
			xp += Effort.xpPerHour(skill, level) * factor * everySeconds / 3600;
			now = start + s * 1000L;
			pace.observe(skill, (int) xp, now);
		}
		return now;
	}

	@Test
	public void paceLearnsHowFastThePlayerReallyTrains()
	{
		Pace pace = new Pace();
		// Ten minutes is too little to say anything.
		train(pace, Skill.AGILITY, Experience.getXpForLevel(50), 1.5, 10, 30, 0);
		assertEquals(1, pace.factor(Skill.AGILITY), 0);
		// Two hours at one and a half times the table reads as one and a half.
		pace = new Pace();
		train(pace, Skill.AGILITY, Experience.getXpForLevel(50), 1.5, 120, 30, 0);
		assertEquals(1.5, pace.factor(Skill.AGILITY), 0.03);
		assertEquals(2, pace.hours(Skill.AGILITY), 0.02);
		// Half an hour counts halfway: the estimate moves toward the player gradually.
		pace = new Pace();
		train(pace, Skill.AGILITY, Experience.getXpForLevel(50), 2, 30, 30, 0);
		assertEquals(1.5, pace.factor(Skill.AGILITY), 0.05);
	}

	@Test
	public void pausesLumpsAndBoostsDoNotCountAsTraining()
	{
		Pace pace = new Pace();
		int xp = Experience.getXpForLevel(50);
		long now = train(pace, Skill.AGILITY, xp, 1, 60, 30, 0);
		double before = pace.factor(Skill.AGILITY);
		int at = Experience.getXpForLevel(50) + (int) (Effort.xpPerHour(Skill.AGILITY, 50) * 60 / 60);
		// A quest reward of 13,750 Agility seconds after the last lap is no lap.
		pace.observe(Skill.AGILITY, at + 13_750, now + 5_000);
		assertEquals(before, pace.factor(Skill.AGILITY), 1e-9);
		// Ten minutes away and back: the gap is a new bout, not ten slow minutes.
		pace.observe(Skill.AGILITY, at + 13_750 + 500, now + 605_000);
		assertEquals(before, pace.factor(Skill.AGILITY), 1e-9);
		double hours = pace.hours(Skill.AGILITY);
		// A level drained or boosted with no experience must not restart the clock between two laps.
		Pace hp = new Pace();
		hp.observe(Skill.HITPOINTS, 100_000, 0);
		hp.observe(Skill.HITPOINTS, 100_000, 100_000);
		hp.observe(Skill.HITPOINTS, 100_100, 120_000);
		assertEquals(120, hp.hours(Skill.HITPOINTS) * 3600, 1e-6);
		assertEquals(hours, pace.hours(Skill.AGILITY), 1e-9);
	}

	@Test
	public void newTrainingOutweighsOldOnceMemoryIsFull()
	{
		Pace pace = new Pace();
		long now = train(pace, Skill.FISHING, Experience.getXpForLevel(60), 0.5, 600, 30, 0);
		assertEquals(0.5, pace.factor(Skill.FISHING), 0.03);
		pace.pause();
		train(pace, Skill.FISHING, Experience.getXpForLevel(70), 2, 600, 30, now + 3_600_000);
		assertTrue("old pace still dominates: " + pace.factor(Skill.FISHING), pace.factor(Skill.FISHING) > 1.7);
	}

	@Test
	public void paceSurvivesASaveAndIgnoresJunk()
	{
		Pace pace = new Pace();
		train(pace, Skill.SLAYER, Experience.getXpForLevel(70), 1.3, 90, 40, 0);
		assertTrue(pace.takeChanged());
		assertFalse(pace.takeChanged());
		Map<String, double[]> saved = pace.toSaved();
		saved.put("NOT_A_SKILL", new double[]{1, 1, 1});
		saved.put("AGILITY", new double[]{1, 0, 5});
		saved.put("FISHING", new double[]{1});
		Pace back = new Pace();
		back.load(saved);
		assertEquals(pace.factor(Skill.SLAYER), back.factor(Skill.SLAYER), 0.01);
		assertEquals(1, back.factor(Skill.AGILITY), 0);
		assertEquals(Collections.singleton(Skill.SLAYER), back.factors().keySet());
		back.load(null);
		assertTrue(back.factors().isEmpty());
	}

	@Test
	public void plansUseThePlayersPace()
	{
		AccountSnapshot.Builder b = level(fresh(), Skill.AGILITY, 40);
		Plan steady = planner.plan(Goal.quest("Regicide"), b.build());
		Map<Skill, Double> quick = new EnumMap<>(Skill.class);
		quick.put(Skill.AGILITY, 2.0);
		Plan fast = planner.plan(Goal.quest("Regicide"), b.pace(quick).build());
		double agilitySteady = Effort.trainingHours(Skill.AGILITY, Experience.getXpForLevel(40), 56);
		assertEquals(agilitySteady / 2, steady.getHoursLeft() - fast.getHoursLeft(), 0.01);
	}

	@Test
	public void experienceAfterSomeHoursIsTheInverseOfHoursToALevel()
	{
		int from = Experience.getXpForLevel(45);
		int to = Experience.getXpForLevel(60);
		double hours = Effort.trainingHours(Skill.MINING, from, 60, 1.25);
		assertEquals(to, Effort.xpAfter(Skill.MINING, from, hours, 1.25, Integer.MAX_VALUE), 2);
		// Never past the cap, however long.
		assertEquals(to, Effort.xpAfter(Skill.MINING, from, 1_000, 1, to));
		assertEquals(from, Effort.xpAfter(Skill.MINING, from, 0, 1, to));
	}

	// ---- Session plan ----

	@Test
	public void aSessionFillsItsTimeWithWholeStepsThenTrains()
	{
		AccountSnapshot account = level(fresh(), Skill.AGILITY, 30).build();
		List<Goal> goals = Arrays.asList(Goal.quest("Regicide"));
		SessionPlan session = SessionPlan.build(planner, goals, account, 6);
		assertFalse(session.getItems().isEmpty());
		assertTrue(session.getUsed() <= 6 + 1e-9);
		double sum = 0;
		for (SessionPlan.Item item : session.getItems())
		{
			sum += item.getHours();
		}
		assertEquals(session.getUsed(), sum, 1e-9);
		// Regicide is a long road from a fresh account: the quests on the way fit, then 56 Agility does not, so
		// the session ends on Agility training cut short by the clock.
		SessionPlan.Item last = session.getItems().get(session.getItems().size() - 1);
		assertTrue(last.isPartial());
		assertEquals(Skill.AGILITY, last.getStep().getSkill());
		assertEquals(6, session.getUsed(), 1e-9);
		for (int i = 0; i < session.getItems().size() - 1; i++)
		{
			assertFalse(session.getItems().get(i).isPartial());
		}
		// The goal moves forward and says so.
		assertEquals(1, session.getGains().size());
		SessionPlan.Gain gain = session.getGains().get(0);
		assertTrue(gain.getAfter().getProgress() > gain.getBefore().getProgress());
		assertTrue(gain.getAfter().getHoursLeft() < gain.getBefore().getHoursLeft());
	}

	@Test
	public void aQuestUnlockedByAnEarlierStepCanFollowIt()
	{
		// Plague City opens Biohazard: an hour is enough for both, in that order.
		AccountSnapshot account = fresh().build();
		SessionPlan session = SessionPlan.build(planner, Arrays.asList(Goal.quest("Biohazard")), account, 3);
		List<String> quests = Arrays.asList(session.getItems().stream().map(i -> i.getStep().getQuest())
			.filter(q -> q != null).toArray(String[]::new));
		assertTrue(quests.toString(), quests.indexOf("Plague City") >= 0);
		assertTrue(quests.toString(), quests.indexOf("Biohazard") > quests.indexOf("Plague City"));
		assertTrue(session.getGains().get(0).getAfter().isComplete());
		// With the goal done early, the rest of the time is left spare rather than invented.
		assertTrue(session.getUsed() < 3);
	}

	@Test
	public void aSessionWithNothingOpenIsEmpty()
	{
		AccountSnapshot account = fresh().quest("Cook's Assistant", QuestState.FINISHED).build();
		SessionPlan session = SessionPlan.build(planner, Arrays.asList(Goal.quest("Cook's Assistant")), account, 2);
		assertTrue(session.getItems().isEmpty());
		assertTrue(session.getGains().isEmpty());
		assertEquals(0, session.getUsed(), 0);
	}
}
