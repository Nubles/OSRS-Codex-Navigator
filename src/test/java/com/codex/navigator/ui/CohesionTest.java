package com.codex.navigator.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.Plan;
import com.codex.navigator.core.Planner;
import com.codex.navigator.core.Road;
import com.codex.navigator.core.Step;
import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.QuestDef;
import com.google.gson.Gson;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JLabel;
import net.runelite.api.QuestState;
import org.junit.Test;

/** The same figure everywhere, never twice in one place, and long names that fit. */
public class CohesionTest
{
	private static FontMetrics metrics()
	{
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		FontMetrics fm = g.getFontMetrics(Theme.title());
		g.dispose();
		return fm;
	}

	@Test
	public void aLongGoalWrapsAtItsDashRatherThanBeingCut()
	{
		FontMetrics fm = metrics();
		String dt2 = "Desert Treasure II - The Fallen Empire";
		int width = 209 - 8 - 54;
		String[] lines = RoadCard.titleLines(dt2, fm, width);
		assertArrayEquals(new String[]{"Desert Treasure II -", "The Fallen Empire"}, lines);
		assertTrue(fm.stringWidth(lines[0]) <= width && fm.stringWidth(lines[1]) <= width);
		assertArrayEquals(new String[]{"Regicide"}, RoadCard.titleLines("Regicide", fm, width));
		// Without a dash it breaks at the last word that fits; every word kept.
		String[] rfd = RoadCard.titleLines("Recipe for Disaster - Freeing the Lumbridge Guide", fm, width);
		assertEquals(2, rfd.length);
		assertTrue(fm.stringWidth(rfd[0]) <= width);
		assertEquals("Recipe for Disaster - Freeing the Lumbridge Guide", rfd[0] + " " + rfd[1]);
	}

	@Test
	public void theCrestCountsQuestsAsTheQuestCapeDoes()
	{
		CodexData data = CodexData.load(new Gson());
		Planner planner = new Planner(data);
		AccountSnapshot.Builder b = new AccountSnapshot.Builder().loggedIn(true);
		int i = 0;
		for (QuestDef q : data.quests())
		{
			// Every third quest done, miniquests included, so a miscount either way would show.
			b.quest(q.name, i++ % 3 == 0 ? QuestState.FINISHED : QuestState.NOT_STARTED);
		}
		AccountSnapshot account = b.build();
		Plan cape = planner.plan(Goal.QUEST_CAPE, account);
		int[] crest = CrestHeader.capeCount(account, data, planner.capeQuests());
		assertEquals(cape.getQuestsDone(), crest[0]);
		assertEquals(cape.getQuestsTotal(), crest[1]);
		// The Atlas lists miniquests too, so its count is the larger one; the crest is not.
		assertTrue(crest[1] < data.quests().size());
	}

	private static Plan plan(boolean complete, double hours)
	{
		List<Step> path = new ArrayList<>();
		path.add(new Step(Step.Kind.QUEST, Step.Status.DONE, "Plague City", "", "Plague City", null));
		path.add(new Step(Step.Kind.QUEST, Step.Status.READY, "Biohazard", "", "Biohazard", null));
		path.add(new Step(Step.Kind.QUEST, Step.Status.BLOCKED, "Regicide", "", "Regicide", null));
		return new Plan(Goal.quest("Regicide"), "Regicide", "Experienced · 3 QP", complete, 0.996, path,
			Collections.emptyList(), path.get(1), 1, 3, 0, 0, hours);
	}

	@Test
	public void theRoadCardShowsEachFigureOnce()
	{
		Plan plan = plan(false, 4.5);
		Road road = Road.of(plan, false, 6);
		RoadShare.Spec spec = RoadShare.Spec.of(plan, road, "  Nubles ", "~3 Nov", 0, ZoneOffset.UTC);
		assertNotNull(spec);
		assertEquals("Nubles", spec.heading());
		List<String> values = new ArrayList<>();
		for (String[] tile : spec.tiles())
		{
			values.add(tile[0]);
		}
		assertEquals(java.util.Arrays.asList("1 of 3", "~4.5 h", "~3 Nov"), values);
		// Nearly there is still not there.
		assertEquals("99%", RoadShare.percent(plan.getProgress()));
		assertEquals("codex-road-1970-01-01.png", RoadShare.fileName(0, ZoneOffset.UTC));
		BufferedImage card = RoadShare.render(spec);
		assertEquals(RoadShare.WIDTH * RoadShare.SCALE, card.getWidth());
	}

	@Test
	public void withNamesOffOrNoDayTheCardLeavesThemOut()
	{
		Plan plan = plan(false, 4.5);
		RoadShare.Spec spec = RoadShare.Spec.of(plan, Road.of(plan, false, 6), null, null, 0, ZoneOffset.UTC);
		assertEquals("The road", spec.heading());
		assertEquals(2, spec.tiles().size());
		assertEquals("The road", RoadShare.Spec.of(plan, Road.of(plan, false, 6), " ", null, 0, ZoneOffset.UTC).heading());
		assertNull(RoadShare.Spec.of(plan(true, 0), Road.of(plan, false, 6), "Nubles", null, 0, ZoneOffset.UTC));
		assertNull(RoadShare.Spec.of(plan, null, "Nubles", null, 0, ZoneOffset.UTC));
	}

	@Test
	public void aRowsTagMovesUpOnlyWhenTheDetailWouldBeCut()
	{
		JLabel name = Ui.label("Regicide", Theme.body(), Theme.TEXT);
		JLabel longDetail = Ui.label("Summer pie for Agility 56 +1", Theme.small(), Theme.TEXT_MUTED);
		Pill pill = new Pill("Goal", Theme.GOLD);
		Row.Lines lines = new Row.Lines(name, longDetail, pill);
		int narrow = longDetail.getPreferredSize().width;
		assertTrue(lines.tagOnTop(narrow));
		assertFalse(lines.tagOnTop(narrow + pill.getPreferredSize().width + 20));
		// A long name keeps the tag on the detail line: cutting the name is worse.
		JLabel longName = Ui.label("Recipe for Disaster - Another Cook's Quest", Theme.body(), Theme.TEXT);
		assertFalse(new Row.Lines(longName, longDetail, new Pill("Goal", Theme.GOLD)).tagOnTop(narrow));
	}

	private static void texts(java.awt.Component c, List<String> out)
	{
		if (c instanceof JLabel && ((JLabel) c).getText() != null)
		{
			out.add(((JLabel) c).getText());
		}
		if (c instanceof java.awt.Container)
		{
			for (java.awt.Component k : ((java.awt.Container) c).getComponents())
			{
				texts(k, out);
			}
		}
	}

	@Test
	public void theCrestSaysItCountsAsTheCapeDoes()
	{
		// The Atlas's "All quests" lists every quest (and miniquests when asked), so a bare "Quests 45/199" above a
		// list headed 212 read as a miscount. The crest names what it counts.
		CodexData data = CodexData.load(new Gson());
		CrestHeader crest = new CrestHeader();
		crest.update(new AccountSnapshot.Builder().loggedIn(true).build(), data, new Planner(data).capeQuests());
		List<String> labels = new ArrayList<>();
		texts(crest, labels);
		assertTrue(labels.toString(), labels.contains("Quest cape"));
		assertFalse(labels.toString(), labels.contains("Quests"));
	}

	@Test
	public void aGoalsPathCountsWhatIsLeftSinceItsRingShowsTheDoneCount()
	{
		// The ring above already reads "45/199"; the Path header said the same figure again.
		Plan plan = plan(false, 4.5);
		assertEquals("2 to go", DetailView.toGo(plan));
		assertEquals("all done", DetailView.toGo(new Plan(Goal.quest("Regicide"), "Regicide", "", true, 1, new ArrayList<>(),
			Collections.emptyList(), null, 3, 3, 0, 0, 0)));
	}

	@Test
	public void paceRatesFollowTheSuitesNumberRule()
	{
		// Whole thousands below a million, then millions to one decimal: never "~1000k xp/h".
		assertEquals("57k", PaceCard.perHour(57_400));
		assertEquals("999k", PaceCard.perHour(999_700));
		assertEquals("1.2M", PaceCard.perHour(1_290_000));
	}

	@Test
	public void boostAndGoNeverRepeatsTheFocusCardsBoost()
	{
		// The focus card's next step already reads "Boost Agility 52 to 56 with a summer pie"; listing Regicide again
		// under Boost and go said the same thing twice.
		CodexData data = CodexData.load(new Gson());
		Planner planner = new Planner(data);
		com.codex.navigator.core.Recommender rec = new com.codex.navigator.core.Recommender(planner);
		AccountSnapshot account = com.codex.navigator.TestAccounts.mid(data).build();
		List<Goal> pinned = java.util.Arrays.asList(Goal.quest("Regicide"), Goal.QUEST_CAPE);
		Plan focus = planner.plan(pinned.get(0), account);
		assertTrue(focus.getNext().getDetail(), focus.getNext().getDetail().startsWith("Boost Agility"));
		List<String> all = new ArrayList<>();
		for (com.codex.navigator.core.Recommender.Pick p : rec.boostNow(account, pinned, true, 3))
		{
			all.add(p.getQuest().name);
		}
		assertTrue(all.toString(), all.contains("Regicide"));
		List<String> listed = new ArrayList<>();
		for (com.codex.navigator.core.Recommender.Pick p : CompassView.boostPicks(rec, account, pinned, focus, true, 3))
		{
			listed.add(p.getQuest().name);
		}
		assertFalse(listed.toString(), listed.contains("Regicide"));
		// The others still show, up to the same count.
		assertTrue(listed.size() <= 3);
		// With no focus nothing is held back.
		assertEquals(all, names(CompassView.boostPicks(rec, account, pinned, null, true, 3)));
	}

	private static List<String> names(List<com.codex.navigator.core.Recommender.Pick> picks)
	{
		List<String> out = new ArrayList<>();
		for (com.codex.navigator.core.Recommender.Pick p : picks)
		{
			out.add(p.getQuest().name);
		}
		return out;
	}
}
