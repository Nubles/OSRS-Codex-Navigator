package com.codex.navigator.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.Planner;
import com.codex.navigator.core.SkillNeeds;
import com.codex.navigator.data.CodexData;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;

/** The bar maths, the drawing plan for one tile, and the skills tab tiles the bars are laid into. */
public class SkillBarsTest
{
	private static final int TILE = 62;
	private static final int TRACK = TILE - SkillBars.INSET * 2;

	@Test
	public void towardALevelIsMeasuredInExperienceFromZero()
	{
		assertEquals(0, SkillBars.towardLevel(0, 50), 0);
		assertEquals(1, SkillBars.towardLevel(Experience.getXpForLevel(70), 70), 0);
		assertEquals(1, SkillBars.towardLevel(Experience.getXpForLevel(80), 70), 0);
		// 98 is about 90% of the way to 99 in experience, as the panel's own bars say.
		double at98 = SkillBars.towardLevel(Experience.getXpForLevel(98), 99);
		assertEquals(Experience.getXpForLevel(98) / (double) Experience.getXpForLevel(99), at98, 1e-9);
		assertTrue(at98 > 0.9 && at98 < 0.91);
		// Half the experience of level 60 is half a bar, whatever level that is.
		assertEquals(0.5, SkillBars.towardLevel(Experience.getXpForLevel(60) / 2, 60), 1e-4);
	}

	@Test
	public void towardTheNextLevelRunsInsideTheCurrentLevel()
	{
		int from = Experience.getXpForLevel(52);
		int to = Experience.getXpForLevel(53);
		assertEquals(0, SkillBars.towardNextLevel(from), 0);
		assertEquals(0.5, SkillBars.towardNextLevel(from + (to - from) / 2), 0.001);
		assertEquals(0, SkillBars.towardNextLevel(to), 0);
		assertEquals(-1, SkillBars.towardNextLevel(Experience.getXpForLevel(99)), 0);
		assertEquals(-1, SkillBars.towardNextLevel(Experience.MAX_SKILL_XP), 0);
		assertEquals(0, SkillBars.towardNextLevel(0), 0);
	}

	@Test
	public void experienceLeftComesFromTheGamesTable()
	{
		assertEquals(Experience.getXpForLevel(70) - 300_000, SkillBars.xpLeft(300_000, 70));
		assertEquals(0, SkillBars.xpLeft(Experience.getXpForLevel(70), 70));
		assertEquals(0, SkillBars.xpLeft(Experience.MAX_SKILL_XP, 99));
		assertEquals(99, SkillBars.level(Experience.MAX_SKILL_XP));
		assertEquals(52, SkillBars.level(Experience.getXpForLevel(52) + 10));
	}

	@Test
	public void aStartedBarShowsAtLeastAPixel()
	{
		assertEquals(0, SkillBars.fillWidth(TRACK, 0));
		assertEquals(1, SkillBars.fillWidth(TRACK, 0.001));
		assertEquals(TRACK / 2, SkillBars.fillWidth(TRACK, 0.5));
		assertEquals(TRACK, SkillBars.fillWidth(TRACK, 1));
	}

	private static SkillNeeds needs(AccountSnapshot account, Goal... goals)
	{
		return SkillNeeds.of(new Planner(CodexData.load(new Gson())), Arrays.asList(goals), account);
	}

	private static AccountSnapshot agility(int level)
	{
		return new AccountSnapshot.Builder().loggedIn(true).skill(Skill.AGILITY, level, Experience.getXpForLevel(level)).build();
	}

	@Test
	public void aGoalBarRunsToTheHighestLevelNotchedAtTheLowerOnes()
	{
		AccountSnapshot account = agility(50);
		SkillNeeds.Need need = needs(account, Goal.level(Skill.AGILITY, 70), Goal.level(Skill.AGILITY, 60)).get(Skill.AGILITY);
		int xp = account.xp(Skill.AGILITY);
		SkillBars.Rect[] plan = SkillBars.plan(TILE, xp, need, false);
		assertNotNull(plan);
		SkillBars.Rect track = plan[SkillBars.TRACK];
		assertEquals(SkillBars.INSET, track.x);
		assertEquals(TRACK, track.width);
		assertEquals(SkillBars.GOAL_HEIGHT, track.height);
		int fill = SkillBars.fillWidth(TRACK, SkillBars.towardLevel(xp, 70));
		assertEquals(fill, plan[SkillBars.FILL].width);
		assertEquals(fill, plan[SkillBars.SHINE].width);
		// The fill and its shine together are exactly as tall as the track.
		assertEquals(SkillBars.GOAL_HEIGHT, plan[SkillBars.FILL].height + plan[SkillBars.SHINE].height);

		// One notch, at 60's share of 70's experience, ahead of the fill so drawn as a dim tick.
		SkillBars.Rect notch = plan[SkillBars.NOTCH];
		int at = (int) Math.round(TRACK * Experience.getXpForLevel(60) / (double) Experience.getXpForLevel(70));
		assertEquals(SkillBars.INSET + at, notch.x);
		assertTrue(at > fill);
		assertNull(plan[SkillBars.NOTCH + 1]);
		// Level goals' next step is training, so the skill holds them up and wears the mark at the top right.
		SkillBars.Rect mark = plan[SkillBars.MARK + 1];
		assertTrue(mark.fromTop);
		assertEquals(TILE - SkillBars.MARK_X - 5, mark.x);
		assertEquals(5, mark.width);
	}

	@Test
	public void aReachedLevelDrawsNothingUnlessNextLevelBarsAreOn()
	{
		AccountSnapshot account = agility(72);
		assertTrue(needs(account, Goal.level(Skill.AGILITY, 70)).isEmpty());
		int xp = account.xp(Skill.AGILITY);
		assertNull(SkillBars.plan(TILE, xp, null, false));
		SkillBars.Rect[] plain = SkillBars.plan(TILE, xp + 5000, null, true);
		assertNotNull(plain);
		assertEquals(SkillBars.PLAIN_HEIGHT, plain[SkillBars.TRACK].height);
		assertNull("no mark without a goal", plain[SkillBars.MARK]);
		// At 99 there is no next level to show.
		assertNull(SkillBars.plan(TILE, Experience.getXpForLevel(99), null, true));
	}

	@Test
	public void everySkillHasItsOwnTileInTheSkillsTab()
	{
		// Interface 320's children 1 to 24, in the tab's column order (Attack down to Construction, then Hitpoints...).
		int group = InterfaceID.Stats.UNIVERSE >>> 16;
		Set<Integer> seen = new HashSet<>();
		for (Skill skill : Skill.values())
		{
			int id = SkillBars.tileId(skill);
			if (skill == Skill.OVERALL)
			{
				assertEquals(-1, id);
				continue;
			}
			assertEquals(skill.getName(), group, id >>> 16);
			int child = id & 0xFFFF;
			assertTrue(skill.getName(), child >= 1 && child <= 24);
			assertTrue(seen.add(id));
		}
		assertEquals(0x0140, group);
		assertEquals(1, SkillBars.tileId(Skill.ATTACK) & 0xFFFF);
		assertEquals(8, SkillBars.tileId(Skill.CONSTRUCTION) & 0xFFFF);
		assertEquals(9, SkillBars.tileId(Skill.HITPOINTS) & 0xFFFF);
		assertEquals(24, SkillBars.tileId(Skill.SAILING) & 0xFFFF);
	}
}
