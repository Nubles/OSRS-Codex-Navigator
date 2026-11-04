package com.codex.navigator;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.CombatTasks;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The game's combat achievement tasks (a cache dump, in test resources) and a mid-game account's progress on them. */
public final class CaFixture
{
	private static List<CombatTasks.Task> tasks;

	private CaFixture()
	{
	}

	/** Every task as the game cache (revision 242) lists them. */
	public static synchronized List<CombatTasks.Task> tasks()
	{
		if (tasks != null)
		{
			return tasks;
		}
		List<CombatTasks.Task> out = new ArrayList<>();
		try (InputStream in = CaFixture.class.getResourceAsStream("/com/codex/navigator/ca-tasks.tsv");
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			for (String line = r.readLine(); line != null; line = r.readLine())
			{
				if (line.startsWith("#") || line.trim().isEmpty())
				{
					continue;
				}
				String[] f = line.split("\t", -1);
				out.add(new CombatTasks.Task(Integer.parseInt(f[0]), f[4], f[5], Integer.parseInt(f[1]), f[2], f[3]));
			}
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
		tasks = out;
		return out;
	}

	public static CombatTasks.Task named(String name)
	{
		for (CombatTasks.Task t : tasks())
		{
			if (t.getName().equals(name))
			{
				return t;
			}
		}
		throw new AssertionError("no task " + name);
	}

	/**
	 * A mid-game account: every Easy and Medium task, and the Hard tasks at Barrows, the Giant Mole, the King Black
	 * Dragon and Sarachnis; kill counts at those bosses and at Vorkath.
	 */
	public static CombatTasks mid()
	{
		BitSet done = new BitSet();
		for (CombatTasks.Task t : tasks())
		{
			boolean hardBoss = t.getTier() == 3 && (t.getMonster().equals("Barrows") || t.getMonster().equals("Giant Mole")
				|| t.getMonster().equals("King Black Dragon") || t.getMonster().equals("Sarachnis"));
			if (t.getTier() <= 2 || hardBoss)
			{
				done.set(t.getId());
			}
		}
		Map<String, Integer> kills = new HashMap<>();
		kills.put("Vorkath", 38);
		kills.put("Barrows", 212);
		kills.put("Giant Mole", 64);
		kills.put("King Black Dragon", 51);
		kills.put("Sarachnis", 90);
		kills.put("Zulrah", 12);
		return new CombatTasks(tasks(), done, new int[]{41, 169, 436, 1100, 1965, 2697}, kills, "Boss Pilot and RuneLite");
	}

	/** The mid account with its combat achievements: points as the game would total them. */
	public static AccountSnapshot.Builder account(AccountSnapshot.Builder b, CombatTasks ca)
	{
		return b.combatTasks(ca).combatAchievementPoints(ca.pointsFromTasks());
	}
}
