package com.codex.navigator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.codex.navigator.core.Goal;
import com.codex.navigator.core.GoalStore;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * The {@code goals} entry is read by Codex Bank Steward, so its shape must not move: a JSON list of goal keys,
 * focus first. The goal board added beside it lives under its own key and leaves this one alone.
 */
public class GoalsKeyContractTest
{
	/** Codex Bank Steward's reader ({@code Plans.readGoals} and its {@code parse}), copied as it stands. */
	private static List<String> stewardReadGoals(Gson gson, String json)
	{
		List<String> out = new ArrayList<>();
		JsonElement root;
		if (json == null || json.trim().isEmpty())
		{
			return out;
		}
		try
		{
			root = gson.fromJson(json, JsonElement.class);
		}
		catch (JsonParseException | IllegalStateException | NumberFormatException e)
		{
			return out;
		}
		if (root == null || !root.isJsonArray())
		{
			return out;
		}
		for (JsonElement e : root.getAsJsonArray())
		{
			if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString())
			{
				String key = e.getAsString().trim();
				if (key.indexOf(':') > 0 && !out.contains(key))
				{
					out.add(key);
				}
			}
		}
		return out;
	}

	@Test
	public void bankStewardReadsThePinnedGoalsAsBefore()
	{
		Gson gson = new Gson();
		List<String> saved = new ArrayList<>();
		GoalStore store = new GoalStore();
		// What the plugin's saver writes under codexnavigator.goals.
		store.setSaver(keys -> saved.add(gson.toJson(keys)));
		store.toggle(Goal.quest("Dragon Slayer II"));
		store.toggle(Goal.diary("Ardougne Medium"));
		store.toggle(Goal.QUEST_CAPE);
		store.makeFocus(Goal.diary("Ardougne Medium"));

		String json = saved.get(saved.size() - 1);
		assertEquals("[\"diary:Ardougne Medium\",\"quest:Dragon Slayer II\",\"cape:Quest point cape\"]", json);
		assertEquals(Arrays.asList("diary:Ardougne Medium", "quest:Dragon Slayer II", "cape:Quest point cape"),
			stewardReadGoals(gson, json));
		// The board other plugins may read has a key of its own.
		assertNotEquals("goals", CodexNavigatorPlugin.BOARD_KEY);
	}
}
