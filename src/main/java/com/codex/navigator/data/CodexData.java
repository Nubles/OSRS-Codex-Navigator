package com.codex.navigator.data;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The bundled requirement data: quests, diaries and combat achievement tier sizes,
 * plus a reverse index of how much each quest unlocks.
 */
public final class CodexData
{
	public static final String[] COMBAT_ACHIEVEMENT_TIERS = {"Easy", "Medium", "Hard", "Elite", "Master", "Grandmaster"};

	private static final class Raw
	{
		List<QuestDef> quests;
		List<DiaryDef> diaries;
		Map<String, Integer> combatAchievementTasks;
	}

	private final Map<String, QuestDef> quests = new LinkedHashMap<>();
	private final Map<String, DiaryDef> diaries = new LinkedHashMap<>();
	private final Map<String, Integer> combatAchievementTasks;
	private final Map<String, Integer> unlocks = new HashMap<>();

	public CodexData(List<QuestDef> questList, List<DiaryDef> diaryList, Map<String, Integer> caTasks)
	{
		for (QuestDef q : questList)
		{
			quests.put(q.name, q);
		}
		for (DiaryDef d : diaryList)
		{
			diaries.put(d.name, d);
		}
		combatAchievementTasks = caTasks == null ? Collections.emptyMap() : caTasks;
		indexUnlocks();
	}

	public static CodexData load(Gson gson)
	{
		try (InputStream in = CodexData.class.getResourceAsStream("/com/codex/navigator/codex-data.json"))
		{
			if (in == null)
			{
				throw new IllegalStateException("codex-data.json is missing from the plugin jar");
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				Raw raw = gson.fromJson(reader, Raw.class);
				return new CodexData(raw.quests, raw.diaries, raw.combatAchievementTasks);
			}
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Could not read codex-data.json", e);
		}
	}

	/** Counts, for every quest, how many quests and diaries need it somewhere down their chain. */
	private void indexUnlocks()
	{
		Map<String, Set<String>> dependents = new HashMap<>();
		for (QuestDef q : quests.values())
		{
			for (String p : q.prereqs)
			{
				dependents.computeIfAbsent(p, k -> new HashSet<>()).add("q:" + q.name);
			}
		}
		for (DiaryDef d : diaries.values())
		{
			for (String p : d.quests)
			{
				dependents.computeIfAbsent(p, k -> new HashSet<>()).add("d:" + d.name);
			}
		}
		for (String name : quests.keySet())
		{
			Set<String> seen = new HashSet<>();
			Deque<String> queue = new ArrayDeque<>();
			queue.add(name);
			while (!queue.isEmpty())
			{
				String current = queue.poll();
				for (String dep : dependents.getOrDefault(current, Collections.emptySet()))
				{
					if (seen.add(dep) && dep.startsWith("q:"))
					{
						queue.add(dep.substring(2));
					}
				}
			}
			unlocks.put(name, seen.size());
		}
	}

	public Collection<QuestDef> quests()
	{
		return quests.values();
	}

	public Collection<DiaryDef> diaries()
	{
		return diaries.values();
	}

	public QuestDef quest(String name)
	{
		return quests.get(name);
	}

	public DiaryDef diary(String name)
	{
		return diaries.get(name);
	}

	public int unlockCount(String quest)
	{
		return unlocks.getOrDefault(quest, 0);
	}

	public int combatAchievementTasks(String tier)
	{
		return combatAchievementTasks.getOrDefault(tier, 0);
	}

	public List<QuestDef> questCapeQuests()
	{
		List<QuestDef> list = new ArrayList<>();
		for (QuestDef q : quests.values())
		{
			if (!q.miniquest)
			{
				list.add(q);
			}
		}
		return list;
	}

	public int totalQuestPoints()
	{
		int total = 0;
		for (QuestDef q : quests.values())
		{
			total += q.points;
		}
		return total;
	}
}
