package com.codex.navigator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.QuestDef;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

public class HazardsTest
{
	private CodexData data;
	private Recommender recommender;

	@Before
	public void setUp()
	{
		data = CodexData.load(new Gson());
		recommender = new Recommender(new Planner(data));
	}

	/** Every level and quest point met, no quest started: every quest is ready, so only the ordering decides. */
	private AccountSnapshot.Builder strong()
	{
		AccountSnapshot.Builder b = new AccountSnapshot.Builder().loggedIn(true).members(true).combatLevel(126).questPoints(400);
		for (Skill skill : Skill.values())
		{
			b.skill(skill, 99, 13_034_431);
		}
		for (QuestDef q : data.quests())
		{
			b.quest(q.name, QuestState.NOT_STARTED);
		}
		return b;
	}

	@Test
	public void everyHazardNamesAQuestInTheData()
	{
		for (String name : Hazards.names())
		{
			assertNotNull("Unknown quest " + name, data.quest(name));
		}
		assertNull(Hazards.of("Cook's Assistant"));
		assertEquals("Boss fight", Hazards.of("Dragon Slayer I").getLabel());
		assertEquals("Wilderness", Hazards.of("Mage Arena I").getLabel());
	}

	@Test
	public void onOneLifeDeadlyQuestsWaitBehindSaferOnes()
	{
		List<Recommender.Pick> picks = recommender.readyNow(strong().oneLife(true).build(), Collections.emptyList(), true, 500);
		boolean seenHazard = false;
		for (Recommender.Pick pick : picks)
		{
			boolean hazard = Hazards.of(pick.getQuest().name) != null;
			assertFalse(pick.getQuest().name + " is safe but listed after a deadly quest", seenHazard && !hazard);
			seenHazard |= hazard;
		}
		assertTrue(seenHazard);
	}

	@Test
	public void anOrdinaryAccountKeepsTheUsualOrder()
	{
		List<Recommender.Pick> plain = recommender.readyNow(strong().build(), Collections.emptyList(), true, 500);
		boolean safeAfterHazard = false;
		boolean seenHazard = false;
		for (Recommender.Pick pick : plain)
		{
			boolean hazard = Hazards.of(pick.getQuest().name) != null;
			safeAfterHazard |= seenHazard && !hazard;
			seenHazard |= hazard;
		}
		assertTrue("Without one life the worth order mixes them", safeAfterHazard);
	}

	@Test
	public void aDeadlyQuestYouPinnedStillLeads()
	{
		Goal goal = Goal.quest("Dragon Slayer I");
		List<Recommender.Pick> picks = recommender.readyNow(strong().oneLife(true).build(), Collections.singletonList(goal), true, 3);
		assertEquals("Dragon Slayer I", picks.get(0).getQuest().name);
	}

	@Test
	public void oneLifeIsPartOfWhatThePanelNotices()
	{
		AccountSnapshot a = strong().build();
		AccountSnapshot b = strong().oneLife(true).build();
		assertFalse(a.structureSignature() == b.structureSignature());
		assertTrue(b.toBuilder().build().isOneLife());
	}
}
