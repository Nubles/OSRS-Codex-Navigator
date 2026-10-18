package com.codex.navigator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.DiaryDef;
import com.google.gson.Gson;
import org.junit.Test;

public class DiaryVarbitTest
{
	@Test
	public void everyDiaryTierThePluginReadsExistsInTheData()
	{
		CodexData data = CodexData.load(new Gson());
		int count = 0;
		for (String area : CodexNavigatorPlugin.DIARY_AREAS)
		{
			for (String tier : CodexNavigatorPlugin.DIARY_TIERS)
			{
				DiaryDef d = data.diary(area + " " + tier);
				assertNotNull(area + " " + tier, d);
				count++;
			}
		}
		assertEquals(data.diaries().size(), count);
	}

	@Test
	public void questStepTextNamesTheVerb()
	{
		com.codex.navigator.core.Step start = new com.codex.navigator.core.Step(com.codex.navigator.core.Step.Kind.QUEST,
			com.codex.navigator.core.Step.Status.READY, "Monkey Madness I", "", "Monkey Madness I", null);
		assertEquals("Start Monkey Madness I", CodexNavigatorPlugin.stepText(start));
	}
}
