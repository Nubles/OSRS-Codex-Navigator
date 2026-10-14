package com.codex.navigator.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** One achievement diary tier as it appears in codex-data.json. */
public class DiaryDef
{
	public String name;
	public String area;
	public String tier;
	public Map<String, Integer> skills = Collections.emptyMap();
	public int questPoints;
	public int combatLevel;
	public boolean allQuests;
	public List<String> quests = Collections.emptyList();

	public int tierRank()
	{
		switch (tier)
		{
			case "Easy":
				return 1;
			case "Medium":
				return 2;
			case "Hard":
				return 3;
			case "Elite":
				return 4;
			default:
				return 0;
		}
	}
}
