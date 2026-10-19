package com.codex.navigator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.GoalBoard;
import com.codex.navigator.core.GoalStore;
import com.codex.navigator.core.Pace;
import com.codex.navigator.core.Planner;
import com.codex.navigator.core.PlayTime;
import com.codex.navigator.core.Recommender;
import com.codex.navigator.core.Saved;
import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.DiaryDef;
import com.codex.navigator.data.QuestDef;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.Test;

/**
 * Every saved entry this plugin reads (its own goals and pace, Session Chronicle's journal and Guardian's
 * one-life mark) is read back from empty, cut-off, wrong-type, huge, negative, future-version and garbage values
 * without throwing, and keeps whatever good part there is. Big accounts and long histories stay quick.
 */
public class DamagedDataTest
{
	private static final Gson GSON = new Gson();

	/** Values a damaged or hand-edited profile could hold under any key. */
	static List<String> garbage()
	{
		StringBuilder deep = new StringBuilder();
		for (int i = 0; i < 200; i++)
		{
			deep.append('[');
		}
		StringBuilder longText = new StringBuilder("\"");
		for (int i = 0; i < 100_000; i++)
		{
			longText.append('x');
		}
		longText.append('"');
		return Arrays.asList(null, "", " ", "null", "{", "[", "[1,2", "[\"quest:Regi", "\"", "garbage", "{}", "[]", "0",
			"-1", "true", "false", "NaN", "1e999999", "-9223372036854775809", "[null]", "[[]]", "[{}]", "{\"a\":null}",
			"{\"version\":99,\"goals\":[{\"key\":\"quest:Regicide\"}]}", "[1e100000000]", "{\"AGILITY\":1e100000000}",
			"\u0000\u0001", "<html>", deep.toString(), longText.toString());
	}

	@Test
	public void goalsSurviveAnySavedValue()
	{
		for (String value : garbage())
		{
			List<String> keys = Saved.goalKeys(GSON, value);
			GoalStore store = new GoalStore();
			store.load(keys);
			for (Goal goal : store.goals())
			{
				assertTrue(value, goal.key().length() < 300);
			}
		}
	}

	@Test
	public void oneBadGoalNeverLosesTheGoodOnes()
	{
		// One odd entry used to fail the whole list, so the next pin saved over every goal.
		List<String> keys = Saved.goalKeys(GSON,
			"[\"quest:Regicide\", 5, {\"x\":1}, null, [\"y\"], \"diary:Ardougne Hard\", true, \"skill:Agility 70\"]");
		GoalStore store = new GoalStore();
		store.load(keys);
		assertEquals(Arrays.asList("quest:Regicide", "diary:Ardougne Hard", "skill:Agility 70"), store.keys());
		// A bare key rather than a list is still that one goal.
		assertEquals(Collections.singletonList("quest:Regicide"), Saved.goalKeys(GSON, "\"quest:Regicide\""));
	}

	@Test
	public void oddGoalKeysAreSkippedNotPinned()
	{
		for (String key : Arrays.asList("", ":", "quest:", "quest: ", "skill:Agility", "skill:Agility 0", "skill:Agility 100",
			"skill:Agility -5", "skill:Agility 99999999999", "skill:Overall 50", "skill:Nonsense 50", "diary:", "nothing",
			"cape", "future:Something"))
		{
			assertNull(key, Goal.fromKey(key));
		}
		assertEquals("skill:Agility 70", Goal.fromKey("skill:agility 70").key());
	}

	@Test
	public void aHugeSavedListLoadsQuicklyAndKeepsOrder()
	{
		CodexData data = CodexData.load(GSON);
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < 20_000; i++)
		{
			json.append(i > 0 ? "," : "").append("\"quest:Made Up Quest ").append(i % 5_000).append('"');
		}
		json.append(']');
		long start = System.nanoTime();
		GoalStore store = new GoalStore();
		store.load(Saved.goalKeys(GSON, json.toString()));
		long ms = (System.nanoTime() - start) / 1_000_000;
		assertTrue(ms + " ms", ms < 100);
		assertEquals("quest:Made Up Quest 0", store.focus().key());
		assertTrue(store.goals().size() <= GoalStore.MAX_GOALS);
		assertTrue(data.quests().size() + data.diaries().size() < GoalStore.MAX_GOALS);
	}

	@Test
	public void paceSurvivesAnySavedValue()
	{
		for (String value : garbage())
		{
			Pace pace = new Pace();
			pace.load(Saved.pace(GSON, value));
			for (Skill skill : Skill.values())
			{
				double f = pace.factor(skill);
				assertTrue(value + " " + f, f >= 0.25 && f <= 4);
			}
		}
	}

	@Test
	public void oneBadPaceRowNeverLosesTheGoodOnes()
	{
		// One skill's row of the wrong shape used to throw the whole learned pace away.
		Map<String, double[]> saved = Saved.pace(GSON,
			"{\"AGILITY\":[90000,60000,3600],\"ATTACK\":\"fast\",\"MINING\":[1,2],\"SLAYER\":{\"x\":1},\"FISHING\":[1,2,\"x\"],"
				+ "\"version\":2,\"COOKING\":[1e308,1e-300,1e308],\"HERBLORE\":[-5,100,100],\"THIEVING\":[NaN,1,1]}");
		Pace pace = new Pace();
		pace.load(saved);
		assertEquals(1.5, pace.factor(Skill.AGILITY), 0.001);
		assertEquals(4, pace.factor(Skill.COOKING), 0.001);
		assertEquals(1, pace.factor(Skill.HERBLORE), 0.001);
		assertEquals(1, pace.factor(Skill.THIEVING), 0.001);
		assertEquals(1, pace.factor(Skill.ATTACK), 0.001);
	}

	@Test
	public void chroniclesJournalSurvivesAnySavedValue()
	{
		long now = 1_760_000_000_000L;
		for (String value : garbage())
		{
			assertNull(value, PlayTime.read(GSON, value, now));
		}
		// Sessions of the wrong shape, from the future, with no play or absurd figures are passed over; the good ones count.
		String journal = "[{\"startedAt\":" + (now - 86_400_000L) + ",\"playedMs\":3600000},"
			+ "{\"startedAt\":" + (now - 2 * 86_400_000L) + ",\"playedMs\":3600000},"
			+ "{\"startedAt\":\"yesterday\",\"playedMs\":3600000}, 7, null, \"x\", {\"startedAt\":" + (now + 86_400_000L)
			+ ",\"playedMs\":3600000}, {\"startedAt\":1e100000000,\"playedMs\":1e100000000},"
			+ "{\"startedAt\":" + (now - 3 * 86_400_000L) + ",\"playedMs\":-5}, {\"startedAt\":" + (now - 3 * 86_400_000L)
			+ ",\"playedMs\":9e18}]";
		long start = System.nanoTime();
		PlayTime time = PlayTime.read(GSON, journal, now);
		assertTrue((System.nanoTime() - start) / 1_000_000 < 50);
		assertEquals(3, time.getSessions());
		// The 9e18 ms session counts as a single day at most: (1 + 1 + 24) hours over the 7-day floor.
		assertEquals(26 / 7.0, time.getHoursPerDay(), 0.01);
	}

	@Test
	public void aHugeNumberInTheJournalIsReadQuickly()
	{
		// Read through a JSON tree, an exponent like 1e100000000 was expanded digit by digit on older Java, freezing
		// the client while the journal loaded.
		long now = 1_760_000_000_000L;
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < 50; i++)
		{
			json.append(i > 0 ? "," : "").append("{\"startedAt\":1e").append(100_000_000 + i).append(",\"playedMs\":1e")
				.append(100_000_000 + i).append('}');
		}
		json.append(']');
		long start = System.nanoTime();
		assertNull(PlayTime.read(GSON, json.toString(), now));
		long ms = (System.nanoTime() - start) / 1_000_000;
		assertTrue(ms + " ms", ms < 50);
		assertEquals(-1, Saved.number(GSON.fromJson("1e100000000", com.google.gson.JsonElement.class)));
		assertEquals(-1, Saved.number(GSON.fromJson("-1e100000000", com.google.gson.JsonElement.class)));
		assertEquals(1_760_000_000_000L, Saved.number(GSON.fromJson("1760000000000", com.google.gson.JsonElement.class)));
		assertEquals(1_760_000_000_000L, Saved.number(GSON.fromJson("1.76e12", com.google.gson.JsonElement.class)));
		assertEquals(-1, Saved.number(GSON.fromJson("\"12\"", com.google.gson.JsonElement.class)));
	}

	@Test
	public void aTenThousandSessionJournalReadsInAFewMilliseconds()
	{
		long now = 1_760_000_000_000L;
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < 12_000; i++)
		{
			json.append(i > 0 ? "," : "").append("{\"startedAt\":").append(now - i * 3_600_000L)
				.append(",\"playedMs\":1800000,\"kills\":[\"Zulrah x3\"],\"loot\":12345}");
		}
		json.append(']');
		String journal = json.toString();
		PlayTime.read(GSON, journal, now);
		long start = System.nanoTime();
		PlayTime time = PlayTime.read(GSON, journal, now);
		long ms = (System.nanoTime() - start) / 1_000_000;
		System.out.println("12,000 session journal read in " + ms + " ms");
		assertTrue(ms + " ms", ms < 100);
		// Fourteen days back to the hour, both ends counted.
		assertEquals(14 * 24 + 1, time.getSessions());
	}

	@Test
	public void guardiansOneLifeMarkIsOnlyTrueWhenSaidPlainly()
	{
		assertTrue(CodexNavigatorPlugin.markedOneLife("true"));
		for (String value : garbage())
		{
			assertFalse(value, !"true".equals(value) && CodexNavigatorPlugin.markedOneLife(value));
		}
		for (String value : Arrays.asList("TRUE", " true", "1", "yes", "{\"oneLife\":true}"))
		{
			assertFalse(value, CodexNavigatorPlugin.markedOneLife(value));
		}
	}

	@Test
	public void maxedAndFreshAccountsPlanQuickly()
	{
		CodexData data = CodexData.load(GSON);
		Planner planner = new Planner(data);
		Recommender rec = new Recommender(planner);
		List<Goal> everything = new ArrayList<>();
		everything.add(Goal.QUEST_CAPE);
		for (QuestDef q : data.quests())
		{
			everything.add(Goal.quest(q.name));
		}
		for (DiaryDef d : data.diaries())
		{
			everything.add(Goal.diary(d.name));
		}
		everything.add(Goal.level(Skill.AGILITY, 99));
		for (AccountSnapshot account : Arrays.asList(TestAccounts.maxed(data), TestAccounts.fresh(data), TestAccounts.mid(data).build()))
		{
			// What a refresh does on the client thread with every goal in the game pinned: the board for other
			// plugins, the finished-goal check and the overlay's focus plan.
			for (int warm = 0; warm < 3; warm++)
			{
				GoalBoard.of(planner, everything, account);
			}
			long start = System.nanoTime();
			String board = GSON.toJson(GoalBoard.of(planner, everything, account));
			planner.plan(everything.get(0), account);
			long clientMs = (System.nanoTime() - start) / 1_000_000;
			// What the Compass builds on the Swing thread.
			start = System.nanoTime();
			rec.readyNow(account, everything, true, 6);
			rec.boostNow(account, everything, true, 4);
			rec.almostThere(account, everything, true, 4);
			rec.closestDiaries(account, 3);
			long panelMs = (System.nanoTime() - start) / 1_000_000;
			System.out.println(account.getPlayerName() + ": board of " + everything.size() + " goals " + clientMs
				+ " ms (" + board.length() + " chars), compass lists " + panelMs + " ms");
			assertTrue(clientMs + " ms", clientMs < 100);
			assertTrue(panelMs + " ms", panelMs < 400);
		}
		// A keen player's pins: twenty goals, the most anyone is likely to keep, plan in a few milliseconds.
		List<Goal> twenty = everything.subList(0, 20);
		AccountSnapshot maxed = TestAccounts.maxed(data);
		AccountSnapshot mid = TestAccounts.mid(data).build();
		for (int warm = 0; warm < 3; warm++)
		{
			GoalBoard.of(planner, twenty, mid);
		}
		long start = System.nanoTime();
		GSON.toJson(GoalBoard.of(planner, twenty, mid));
		GSON.toJson(GoalBoard.of(planner, twenty, maxed));
		double ms = (System.nanoTime() - start) / 2e6;
		System.out.println("board of 20 goals: " + ms + " ms");
		assertTrue(ms + " ms", ms < 10);
	}
}
