package com.codex.navigator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.TestAccounts;
import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.QuestDef;
import com.codex.navigator.data.QuestItems;
import com.google.gson.Gson;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** The prep card's judgement: what blocks a quest and for how long, what boosts cover, and where each item is. */
public class QuestPrepTest
{
	private static final Gson GSON = new Gson();
	private final CodexData data = CodexData.load(GSON);
	private final Planner planner = new Planner(data);

	private static Map<Integer, Long> items(long... pairs)
	{
		Map<Integer, Long> out = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put((int) pairs[i], pairs[i + 1]);
		}
		return out;
	}

	private static QuestPrep.Line line(QuestPrep prep, String name)
	{
		for (QuestPrep.Line line : prep.getItems())
		{
			if (line.getItem().name.equals(name))
			{
				return line;
			}
		}
		throw new AssertionError("No " + name + " in " + prep.getQuest());
	}

	@Test
	public void bundledListsLoadAndNameRealQuests()
	{
		QuestItems lists = data.questItems();
		assertTrue("lists for the quests most paths cross", lists.size() >= 55);
		for (String quest : lists.quests())
		{
			assertNotNull("not a quest Navigator knows: " + quest, data.quest(quest));
			for (QuestItems.Item item : lists.of(quest).items)
			{
				assertTrue(quest + ": " + item.name, item.qty >= 1);
				assertTrue(quest + ": " + item.name + " has no ids and is not marked a kind of thing",
					item.checkable() || item.ids == null);
			}
		}
		// The ids come from RuneLite's own item names, checked by the build script; a few pinned here.
		QuestItems.Entry cook = lists.of("Cook's Assistant");
		assertTrue(cook.items.get(0).ids.contains(ItemID.EGG));
		assertTrue(cook.items.get(1).ids.contains(ItemID.BUCKET_MILK));
		assertTrue(cook.items.get(2).ids.contains(ItemID.POT_FLOUR));
		QuestItems.Item coins = lists.of("Dragon Slayer I").items.get(8);
		assertEquals("Coins", coins.name);
		assertEquals(Collections.singletonList(ItemID.COINS), coins.ids);
		assertEquals(2000, coins.qty);
		// A kind of thing is never checked.
		QuestItems.Item light = lists.of("The Lost Tribe").items.get(1);
		assertFalse(light.checkable());
	}

	@Test
	public void eachItemIsMarkedWhereItIs()
	{
		AccountSnapshot account = TestAccounts.mid(data).quest("Dragon Slayer I", QuestState.NOT_STARTED).questPoints(40)
			.build();
		// Carrying the bowl and 1,500 coins; the bank holds 500 more coins, two of the three planks and the nails.
		Holdings have = new Holdings(items(ItemID.BOWL_UNFIRED, 1, ItemID.COINS, 1500),
			items(ItemID.COINS, 500, ItemID.WOODPLANK, 2, ItemID.NAILS, 300), Holdings.BankSource.STEWARD, 1000);
		QuestPrep prep = QuestPrep.of(planner, "Dragon Slayer I", account, have);
		assertEquals(QuestPrep.Have.CARRIED, line(prep, "Unfired bowl").getHave());
		assertEquals(QuestPrep.Have.BANKED, line(prep, "Coins").getHave());
		assertEquals(QuestPrep.Have.SHORT, line(prep, "Plank").getHave());
		assertEquals(2, line(prep, "Plank").getBanked());
		assertEquals(QuestPrep.Have.BANKED, line(prep, "Steel nails").getHave());
		assertEquals(QuestPrep.Have.MISSING, line(prep, "Silk").getHave());
		// The shield is handed out in the quest: not having one is not a gap.
		assertEquals(QuestPrep.Have.IN_QUEST, line(prep, "Anti-dragon shield").getHave());
		// The law and air rune for Telekinetic Grab are missing too.
		assertEquals(QuestPrep.Have.MISSING, line(prep, "Law rune").getHave());
		assertEquals(7, prep.toGet());
	}

	@Test
	public void anUnseenBankIsNeverCalledEmpty()
	{
		AccountSnapshot account = TestAccounts.mid(data).quest("Dragon Slayer I", QuestState.NOT_STARTED).build();
		Holdings carriedOnly = new Holdings(items(ItemID.COINS, 300), null, Holdings.BankSource.NONE, 0);
		QuestPrep prep = QuestPrep.of(planner, "Dragon Slayer I", account, carriedOnly);
		QuestPrep.Line coins = line(prep, "Coins");
		assertEquals(QuestPrep.Have.UNSEEN, coins.getHave());
		assertEquals(300, coins.getCarried());
		assertEquals(0, prep.count(QuestPrep.Have.MISSING, QuestPrep.Have.SHORT));
		assertEquals(QuestPrep.Have.IN_QUEST, line(prep, "Anti-dragon shield").getHave());
	}

	@Test
	public void regicideAt52AgilityIsABoostNotABlock()
	{
		// The mid account has Regicide's quests done and 52 Agility; a summer pie gives +5 toward its 56.
		AccountSnapshot account = TestAccounts.mid(data).build();
		Holdings pies = new Holdings(Collections.emptyMap(), items(ItemID.SUMMER_PIE, 2), Holdings.BankSource.LIVE, 1);
		QuestPrep prep = QuestPrep.of(planner, "Regicide", account, pies);
		assertTrue(prep.isReady());
		assertEquals(1, prep.getBoosts().size());
		SkillGap boost = prep.getBoosts().get(0);
		assertEquals(Skill.AGILITY, boost.getSkill());
		assertEquals(56, boost.getTarget());
		assertEquals(2, prep.boostItems(boost));
		assertTrue(prep.isListed());
		assertEquals(QuestPrep.Have.UNCHECKED, line(prep, "Bow").getHave());
	}

	@Test
	public void anIronmanWhoCannotBakeThePieMustTrainFirst()
	{
		// At 49 Agility, a pie (+5) falls short of 56: train to 51, then eat one. An ironman at 62 Cooking cannot
		// bake a summer pie (95), so for them it is all 56 by training.
		AccountSnapshot.Builder b = TestAccounts.mid(data);
		b.skill(Skill.AGILITY, 49, net.runelite.api.Experience.getXpForLevel(49));
		QuestPrep main = QuestPrep.of(planner, "Regicide", b.build(), Holdings.none());
		assertFalse(main.isReady());
		QuestPrep.Blocker agility = main.getBlockers().get(0);
		assertEquals("Agility 56", agility.getTitle());
		assertEquals("train to 51, then a summer pie", agility.getDetail());
		assertTrue(agility.getHours() > 0);

		QuestPrep iron = QuestPrep.of(planner, "Regicide", b.ironman(true).build(), Holdings.none());
		QuestPrep.Blocker train = iron.getBlockers().get(0);
		assertNull(train.getDetail());
		assertTrue(train.getHours() > agility.getHours());
	}

	@Test
	public void blockersComeQuestsFirstWithTheirTime()
	{
		AccountSnapshot fresh = TestAccounts.fresh(data);
		QuestPrep prep = QuestPrep.of(planner, "Biohazard", fresh, Holdings.none());
		assertEquals(QuestPrep.Blocker.Kind.QUEST, prep.getBlockers().get(0).getKind());
		assertEquals("Plague City", prep.getBlockers().get(0).getQuest());
		double plague = planner.plan(Goal.quest("Plague City"), fresh).getHoursLeft();
		assertEquals(plague, prep.getBlockers().get(0).getHours(), 1e-9);
		assertEquals(prep.getHoursToReady(), plague, 1e-9);
		QuestDef dragon = data.quest("Dragon Slayer I");
		QuestPrep ds = QuestPrep.of(planner, dragon.name, fresh, Holdings.none());
		assertEquals(QuestPrep.Blocker.Kind.QUEST_POINTS, ds.getBlockers().get(ds.getBlockers().size() - 1).getKind());
	}

	@Test
	public void aQuestWithoutAListSaysSoAndAFinishedOneHasNoCard()
	{
		AccountSnapshot account = TestAccounts.mid(data).build();
		QuestPrep dt2 = QuestPrep.of(planner, "Desert Treasure II - The Fallen Empire", account, Holdings.none());
		assertNotNull(dt2);
		assertFalse(dt2.isListed());
		assertTrue(dt2.getItems().isEmpty());
		assertNull(QuestPrep.of(planner, "Cook's Assistant", account, Holdings.none()));
		assertNull(QuestPrep.of(planner, "Not a quest", account, Holdings.none()));
	}

	@Test
	public void theNextQuestIsTheOneThePathReachesNext()
	{
		AccountSnapshot account = TestAccounts.mid(data).build();
		assertEquals("Regicide", QuestPrep.nextQuest(planner.plan(Goal.quest("Regicide"), account)));
		AccountSnapshot fresh = TestAccounts.fresh(data);
		Plan regicide = planner.plan(Goal.quest("Regicide"), fresh);
		String next = QuestPrep.nextQuest(regicide);
		assertNotNull(next);
		assertFalse(fresh.questDone(next));
		assertNull(QuestPrep.nextQuest(planner.plan(Goal.level(Skill.AGILITY, 60), account)));
	}

	@Test
	public void boostFromFindsTheLowestLevelThePieReaches()
	{
		AccountSnapshot account = TestAccounts.mid(data).build();
		assertEquals(51, QuestPrep.boostFrom(Skill.AGILITY, 40, 56, account));
		// Super attack: 5 + 15% of the level. From 61, 61 + 14 = 75.
		assertEquals(61, QuestPrep.boostFrom(Skill.ATTACK, 50, 75, account));
		// No everyday boost for Prayer: the level itself.
		assertEquals(43, QuestPrep.boostFrom(Skill.PRAYER, 30, 43, account));
	}

	@Test
	public void readsBankStewardsSharedBank() throws Exception
	{
		String json;
		try (InputStream in = QuestPrepTest.class.getResourceAsStream("bank-steward-bankV1.json"))
		{
			json = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
		}
		assertEquals("codexbanksteward", Holdings.STEWARD_GROUP);
		assertEquals("bankV1", Holdings.STEWARD_BANK_KEY);
		Holdings.SharedBank bank = Holdings.readShared(GSON, json);
		assertNotNull(bank);
		assertEquals(1760000000000L, bank.getSavedAt());
		assertEquals(Long.valueOf(1234567), bank.getItems().get(ItemID.COINS));
		assertEquals(6, bank.getItems().size());
		// Part of the bank left out: "none banked" could be wrong, so it is no bank at all.
		assertNull(Holdings.readShared(GSON, json.replace("\"truncated\":false", "\"truncated\":true")));
		assertNull(Holdings.readShared(GSON, null));
		assertNull(Holdings.readShared(GSON, "[1,2]"));
		assertNull(Holdings.readShared(GSON, "{\"items\":"));
		// One odd entry is skipped on its own.
		Holdings.SharedBank odd = Holdings.readShared(GSON, "{\"items\":{\"995\":10,\"x\":3,\"385\":\"lots\",\"-4\":2}}");
		assertEquals(Collections.singletonMap(ItemID.COINS, 10L), odd.getItems());
		assertEquals(0, odd.getSavedAt());
	}

	@Test
	public void theBankSeenLastWins()
	{
		HoldingsWatch watch = new HoldingsWatch();
		assertFalse(watch.holdings().bankKnown());
		watch.pack(items(ItemID.COINS, 10));
		watch.worn(items(ItemID.COINS, 5));
		assertEquals(15, watch.holdings().carried(Collections.singletonList(ItemID.COINS)));
		Holdings.SharedBank shared = Holdings.readShared(GSON, "{\"savedAt\":2000,\"items\":{\"995\":100}}");
		watch.steward(shared);
		assertEquals(Holdings.BankSource.STEWARD, watch.holdings().getBankSource());
		watch.bank(items(ItemID.COINS, 7), 1000);
		// The bank opened before Steward's copy was saved is the older one.
		assertEquals(100, watch.holdings().banked(Collections.singletonList(ItemID.COINS)));
		watch.bank(items(ItemID.COINS, 7), 3000);
		assertEquals(Holdings.BankSource.LIVE, watch.holdings().getBankSource());
		assertEquals(7, watch.holdings().banked(Collections.singletonList(ItemID.COINS)));
		watch.clear();
		assertFalse(watch.holdings().bankKnown());
		assertEquals(0, watch.holdings().carried(Collections.singletonList(ItemID.COINS)));
	}
}
