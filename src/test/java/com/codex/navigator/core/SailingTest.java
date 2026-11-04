package com.codex.navigator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.QuestDef;
import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;

/** Sailing, the 24th skill: its training rates, the quests that need it and the quest experience it is given. */
public class SailingTest
{
	private final CodexData data = CodexData.load(new Gson());

	/** OSRS Wiki, Sailing training: the rates near each anchor level, so the table stays inside what the wiki reports. */
	@Test
	public void trainingRatesFollowTheWikiGuide()
	{
		// Sea charting at 22: around 10,000 an hour.
		assertEquals(10_000, Effort.xpPerHour(Skill.SAILING, 22), 500);
		// Barracuda shipwreck at 35: 9,500-18,000 with steel or mithril hooks.
		assertInside(Effort.xpPerHour(Skill.SAILING, 35), 9_500, 18_000);
		// The Tempor Tantrum (30-54) 19,342-24,519; the Summer Shore courier route about 20,000.
		assertInside(Effort.xpPerHour(Skill.SAILING, 50), 19_000, 24_519);
		// Large shipwreck at 53: 18,000-30,000.
		assertInside(Effort.xpPerHour(Skill.SAILING, 53), 18_000, 30_000);
		// Mercenary shipwreck at 73: 55,000-70,000 with adamant or rune hooks (a little under is fine: rates err long).
		assertInside(Effort.xpPerHour(Skill.SAILING, 73), 50_000, 70_000);
		// Merchant shipwreck at 87: 70,000-95,000.
		assertInside(Effort.xpPerHour(Skill.SAILING, 87), 70_000, 95_000);
		assertInside(Effort.xpPerHour(Skill.SAILING, 99), 70_000, 95_000);
		// A steady rate, never the Barracuda Trials' best (The Gwenith Glide, 113,538 and up).
		assertTrue(Effort.xpPerHour(Skill.SAILING, 99) < 113_538);
	}

	/** OSRS Wiki, Sailing: Quests requiring Sailing. */
	@Test
	public void questsNeedTheSailingLevelsTheWikiLists()
	{
		Map<String, Integer> wiki = new LinkedHashMap<>();
		wiki.put("Prying Times", 12);
		wiki.put("Current Affairs", 22);
		wiki.put("Crab Quest", 40);
		wiki.put("Troubled Tortugans", 45);
		wiki.put("The Red Reef", 52);
		wiki.put("Fallen From Grace", 62);
		int needing = 0;
		for (QuestDef q : data.quests())
		{
			Integer level = q.skills.get("Sailing");
			if (level != null)
			{
				needing++;
				assertEquals(q.name, wiki.get(q.name), level);
			}
		}
		assertEquals(wiki.size(), needing);
	}

	/** OSRS Wiki, Sailing: Quests rewarding Sailing experience (50,000 in all). */
	@Test
	public void questsGiveTheSailingExperienceTheWikiLists()
	{
		String[] quests = {"Pandemonium", "Prying Times", "Current Affairs", "Crab Quest", "Troubled Tortugans",
			"The Red Reef", "Fallen From Grace"};
		double[] xp = {300, 800, 1_400, 10_000, 10_000, 15_000, 12_500};
		double total = 0;
		for (int i = 0; i < quests.length; i++)
		{
			assertEquals(quests[i], xp[i], data.questXp(quests[i]).get("Sailing"), 0);
			total += data.questXp(quests[i]).get("Sailing");
		}
		assertEquals(50_000, total, 0);
	}

	/**
	 * No diary task names Sailing, but the Lumbridge & Draynor Elite needs every quest (the quest cape emote), and
	 * Fallen From Grace needs 62 Sailing: the wiki lists Sailing 62 for that tier for this reason.
	 */
	@Test
	public void theLumbridgeEliteNeedsSailingThroughTheQuestCape()
	{
		AccountSnapshot.Builder b = new AccountSnapshot.Builder().loggedIn(true);
		for (QuestDef q : data.quests())
		{
			b.quest(q.name, QuestState.NOT_STARTED);
		}
		Plan plan = new Planner(data).plan(Goal.diary("Lumbridge Elite"), b.build());
		int sailing = 0;
		for (SkillGap gap : plan.getSkills())
		{
			if (gap.getSkill() == Skill.SAILING)
			{
				sailing = gap.getTarget();
			}
		}
		assertEquals(62, sailing);
	}

	private static void assertInside(double rate, double low, double high)
	{
		assertTrue(rate + " outside " + low + "-" + high, rate >= low && rate <= high);
	}
}
