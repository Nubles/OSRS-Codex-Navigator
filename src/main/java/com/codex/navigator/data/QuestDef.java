package com.codex.navigator.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** One quest or miniquest as it appears in codex-data.json. */
public class QuestDef
{
	public String name;
	public boolean miniquest;
	public int points;
	public String series;
	public String difficulty;
	public Map<String, Integer> skills = Collections.emptyMap();
	public int questPoints;
	public int combatLevel;
	public List<String> prereqs = Collections.emptyList();

	public int difficultyRank()
	{
		if (difficulty == null)
		{
			return 0;
		}
		switch (difficulty)
		{
			case "Novice":
				return 1;
			case "Intermediate":
				return 2;
			case "Experienced":
				return 3;
			case "Master":
				return 4;
			case "Grandmaster":
				return 5;
			default:
				return 0;
		}
	}
}
