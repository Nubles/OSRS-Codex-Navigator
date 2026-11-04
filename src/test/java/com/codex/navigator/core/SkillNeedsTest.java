package com.codex.navigator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.TestAccounts;
import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.DiaryDef;
import com.codex.navigator.data.QuestDef;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

/** Which skills the pinned goals still need, to what level, and which of them hold a goal up today. */
public class SkillNeedsTest
{
	private CodexData data;
	private Planner planner;

	@Before
	public void setUp()
	{
		data = CodexData.load(new Gson());
		planner = new Planner(data);
	}

	@Test
	public void levelGoalsInOneSkillShareABarToTheHighest()
	{
		AccountSnapshot account = TestAccounts.mid(data).build();
		Goal seventy = Goal.level(Skill.AGILITY, 70);
		Goal sixty = Goal.level(Skill.AGILITY, 60);
		SkillNeeds needs = SkillNeeds.of(planner, Arrays.asList(seventy, sixty, Goal.level(Skill.ATTACK, 60)), account);

		SkillNeeds.Need agility = needs.get(Skill.AGILITY);
		assertEquals(70, agility.getTarget());
		assertEquals(Collections.singletonList(60), agility.getWaypoints());
		assertEquals(2, agility.getUses().size());
		assertEquals("pin order, focus first", seventy, agility.getUses().get(0).getGoal());
		assertEquals("70 Agility", agility.getUses().get(0).getTitle());
		// A level goal's next step is training it: the skill holds both up.
		assertEquals(Arrays.asList("70 Agility", "60 Agility"), agility.getHoldsUp());
		// Attack is 70 already: a reached level is no need.
		assertNull(needs.get(Skill.ATTACK));
	}

	@Test
	public void aQuestNeedsEveryUnmetLevelOnItsPath()
	{
		AccountSnapshot account = TestAccounts.mid(data).build();
		Goal goal = Goal.quest("Dragon Slayer II");
		SkillNeeds needs = SkillNeeds.of(planner, Collections.singletonList(goal), account);
		Plan plan = planner.plan(goal, account);
		int unmet = 0;
		for (SkillGap gap : plan.getSkills())
		{
			if (gap.isMet())
			{
				assertNull(gap.getSkill().getName(), needs.get(gap.getSkill()));
				continue;
			}
			unmet++;
			SkillNeeds.Need need = needs.get(gap.getSkill());
			assertEquals(gap.getSkill().getName(), gap.getTarget(), need.getTarget());
			assertEquals("Dragon Slayer II", need.getUses().get(0).getTitle());
		}
		assertTrue(unmet > 0);
		assertEquals(unmet, needs.all().size());
		// Dragon Slayer II's own Magic 75 is on the bar, with the account at 64.
		QuestDef ds2 = data.quest("Dragon Slayer II");
		assertEquals(Planner.need(ds2.skills, Skill.MAGIC).intValue(), needs.get(Skill.MAGIC).getTarget());
		// Quests on its path can still be started, so no level holds it up yet.
		assertEquals(Step.Kind.QUEST, plan.getNext().getKind());
		for (SkillNeeds.Need need : needs.all().values())
		{
			assertFalse(need.getSkill().getName(), need.isBlocking());
		}
	}

	@Test
	public void aDiaryWithEveryQuestDoneIsHeldUpByItsQuickestLevel()
	{
		AccountSnapshot.Builder b = TestAccounts.mid(data);
		DiaryDef diary = data.diary("Ardougne Hard");
		for (String q : planner.plan(Goal.diary("Ardougne Hard"), b.build()).getPath().stream()
			.map(Step::getQuest).filter(n -> n != null).toArray(String[]::new))
		{
			b.quest(q, QuestState.FINISHED);
		}
		b.questPoints(300);
		AccountSnapshot account = b.build();
		Plan plan = planner.plan(Goal.diary("Ardougne Hard"), account);
		assertEquals(Step.Kind.TRAIN, plan.getNext().getKind());
		SkillNeeds needs = SkillNeeds.of(planner, Collections.singletonList(Goal.diary("Ardougne Hard")), account);
		int blocking = 0;
		for (Map.Entry<Skill, SkillNeeds.Need> e : needs.all().entrySet())
		{
			if (e.getValue().isBlocking())
			{
				blocking++;
				assertEquals(plan.getNext().getSkill(), e.getKey());
				assertEquals(Collections.singletonList("Ardougne Hard Diary"), e.getValue().getHoldsUp());
			}
			assertEquals(Planner.need(diary.skills, e.getKey()) == null ? 0 : (int) Planner.need(diary.skills, e.getKey()),
				e.getValue().getTarget());
		}
		assertEquals("one skill is the next step", 1, blocking);
	}

	@Test
	public void aBoostCoveredLevelIsShownAsSuch()
	{
		// Agility 52 against Regicide's 56: a summer pie covers it, so it is needed but holds nothing up.
		AccountSnapshot account = TestAccounts.mid(data).build();
		SkillNeeds.Need agility = SkillNeeds.of(planner, Collections.singletonList(Goal.quest("Regicide")), account)
			.get(Skill.AGILITY);
		assertEquals(56, agility.getTarget());
		assertTrue(agility.getUses().get(0).isBoostable());
		assertFalse(agility.isBlocking());
	}

	@Test
	public void finishedGoalsAndLoggedOutAccountsNeedNothing()
	{
		assertTrue(SkillNeeds.of(planner, Collections.singletonList(Goal.QUEST_CAPE), AccountSnapshot.empty()).isEmpty());
		assertTrue(SkillNeeds.of(planner, Collections.singletonList(Goal.QUEST_CAPE), TestAccounts.maxed(data)).isEmpty());
		assertTrue(SkillNeeds.of(planner, Collections.emptyList(), TestAccounts.mid(data).build()).isEmpty());
	}

	@Test
	public void theSignatureMovesWithTheBars()
	{
		AccountSnapshot account = TestAccounts.mid(data).build();
		String a = SkillNeeds.of(planner, Collections.singletonList(Goal.level(Skill.AGILITY, 70)), account).signature();
		String b = SkillNeeds.of(planner, Collections.singletonList(Goal.level(Skill.AGILITY, 71)), account).signature();
		assertFalse(a.equals(b));
		assertEquals(a, SkillNeeds.of(planner, Collections.singletonList(Goal.level(Skill.AGILITY, 70)), account).signature());
	}
}
