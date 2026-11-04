package com.codex.navigator.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.TestAccounts;
import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.ChompyHunt;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.GoalStore;
import com.codex.navigator.core.Planner;
import com.codex.navigator.core.Recommender;
import com.codex.navigator.data.CodexData;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import net.runelite.api.QuestState;
import net.runelite.client.game.SkillIconManager;
import org.junit.Test;

/**
 * Chompy hats as the panel draws them, at the side panel's width: the Atlas list, a hat goal's page, the focus on the
 * Compass, a Western diary tier's page, before the bow is checked and before the quest. Set {@code -Dcodex.shots=<dir>}
 * to save the pictures.
 */
public class ChompyShotTest
{
	private static final int WIDTH = 217;

	private final CodexData data = CodexData.load(new Gson());
	private final Planner planner = new Planner(data);

	private NavigatorPanel panel(AccountSnapshot account, Goal... pins)
	{
		GoalStore goals = new GoalStore();
		for (Goal g : pins)
		{
			goals.toggle(g);
		}
		NavigatorPanel panel = new NavigatorPanel(planner, new Recommender(planner), goals, new SkillIconManager(), () -> true);
		panel.update(account);
		return panel;
	}

	/** 212 kills, hunting at 140 an hour learned over 2.4 hours, Western Easy done. */
	private AccountSnapshot hunter()
	{
		return TestAccounts.mid(data).diary("Western Easy").chompy(ChompyHunt.View.of(212, 140, 2.4)).build();
	}

	@Test
	public void theAtlasShowsTheNextHatTheDiaryAndEveryHat() throws Exception
	{
		NavigatorPanel panel = panel(hunter(), Goal.chompy("Ogre forester"));
		JComponent view = ChompyView.atlas(panel);
		shoot(view, "1-atlas-chompy.png", 0);
		String text = text(view);
		assertTrue(text, text.contains("NEXT HAT"));
		assertTrue(text, text.contains("Woodsman"));
		assertTrue(text, text.contains("13 kills to go"));
		assertTrue(text, text.contains("About 5 min at your pace"));
		assertTrue(text, text.contains("140 kills an hour, learned from 2.4 hours of hunting"));
		assertTrue(text, text.contains("Western Medium"));
		assertTrue(text, text.contains("125 kills reached"));
		assertTrue(text, text.contains("88 kills to go"));
		assertTrue(text, text.contains("Expert dragon archer"));
		assertTrue(text, text.contains("pinned"));
	}

	@Test
	public void aHatGoalsPageCarriesTheKillsAndTheTime() throws Exception
	{
		NavigatorPanel panel = panel(hunter(), Goal.chompy("Ogre forester"));
		JComponent view = DetailView.build(panel, Goal.chompy("Ogre forester"), false, false, () -> { }, () -> { }, () -> { });
		shoot(view, "2-detail-ogre-forester.png", 0);
		String text = text(view);
		assertTrue(text, text.contains("Ogre forester hat"));
		assertTrue(text, text.contains("88 kills to go"));
		assertTrue(text, text.contains("of play left"));
		assertFalse(text, text.contains("Every quest on this path"));
	}

	@Test
	public void theCompassAndGoalsCarryAHatFocus() throws Exception
	{
		NavigatorPanel panel = panel(hunter(), Goal.chompy("Ogre forester"), Goal.quest("Regicide"));
		JComponent compass = CompassView.build(panel);
		shoot(compass, "3-compass-hat-focus.png", 640);
		assertTrue(text(compass).contains("Ogre forester hat"));
		JComponent goals = GoalsView.build(panel);
		shoot(goals, "4-goals-hat.png", 520);
		assertTrue(text(goals).contains("Ogre forester hat"));
	}

	@Test
	public void aWesternDiaryTierShowsItsChompyTask() throws Exception
	{
		NavigatorPanel panel = panel(hunter(), Goal.diary("Western Hard"));
		JComponent view = DetailView.build(panel, Goal.diary("Western Hard"), false, false, () -> { }, () -> { }, () -> { });
		shoot(view, "5-detail-western-hard.png", 760);
		String text = text(view);
		assertTrue(text, text.contains("Claim a Chompy bird hat from Rantz after registering at least 300 kills."));
		assertTrue(text, text.contains("212 of 300 kills"));
		JComponent ardougne = DetailView.build(panel, Goal.diary("Ardougne Medium"), false, false, () -> { }, () -> { }, () -> { });
		assertFalse(text(ardougne).contains("Chompy task"));
	}

	@Test
	public void beforeTheBowIsCheckedAndBeforeTheQuest() throws Exception
	{
		NavigatorPanel unsynced = panel(TestAccounts.mid(data).chompy(ChompyHunt.View.unsynced(4)).build());
		JComponent view = ChompyView.atlas(unsynced);
		shoot(view, "6-atlas-not-checked.png", 560);
		String text = text(view);
		assertTrue(text, text.contains("Check kills on your ogre bow to read your count"));
		assertTrue(text, text.contains("4 kills seen so far"));

		AccountSnapshot fresh = TestAccounts.mid(data).quest(ChompyHunt.QUEST, QuestState.NOT_STARTED).build();
		NavigatorPanel first = panel(fresh, Goal.chompy("Ogre bowman"));
		JComponent atlas = ChompyView.atlas(first);
		shoot(atlas, "7-atlas-quest-first.png", 420);
		assertTrue(text(atlas).contains("Big Chompy Bird Hunting"));
		JComponent page = DetailView.build(first, Goal.chompy("Ogre bowman"), false, false, () -> { }, () -> { }, () -> { });
		shoot(page, "8-detail-quest-first.png", 0);
		assertTrue(text(page).contains("Big Chompy Bird Hunting"));

		NavigatorPanel out = panel(AccountSnapshot.empty());
		JComponent loggedOut = ChompyView.atlas(out);
		shoot(loggedOut, "9-atlas-logged-out.png", 360);
		assertTrue(text(loggedOut).contains("Log in to see your chompy kills"));
	}

	@Test
	public void theAtlasHasAChompyList() throws Exception
	{
		NavigatorPanel panel = panel(hunter());
		AtlasView atlas = new AtlasView(panel);
		atlas.showList(3);
		atlas.rebuild();
		atlas.setPreferredSize(new Dimension(WIDTH, 520));
		shoot(atlas, "0-atlas-tabs.png", 520);
		assertTrue(text(atlas).contains("NEXT HAT"));
	}

	@Test
	public void everyHatReached() throws Exception
	{
		NavigatorPanel panel = panel(TestAccounts.mid(data).chompy(ChompyHunt.View.of(4120, 260, 6)).build());
		JComponent view = ChompyView.atlas(panel);
		shoot(view, "10-atlas-every-hat.png", 330);
		assertTrue(text(view).contains("EVERY HAT REACHED"));
	}

	private static String text(Component c)
	{
		StringBuilder sb = new StringBuilder();
		if (c instanceof JLabel)
		{
			sb.append(((JLabel) c).getText()).append('\n');
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				sb.append(text(child));
			}
		}
		return sb.toString();
	}

	private static BufferedImage shoot(JComponent c, String name, int maxHeight) throws Exception
	{
		c.setSize(WIDTH, 10);
		layout(c);
		Dimension pref = c.getPreferredSize();
		int h = maxHeight > 0 ? Math.min(maxHeight, pref.height) : pref.height;
		c.setSize(WIDTH, pref.height);
		layout(c);
		layout(c);
		int pad = 8;
		int scale = 2;
		BufferedImage img = new BufferedImage((WIDTH + pad * 2) * scale, (h + pad * 2) * scale, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.scale(scale, scale);
		g.setColor(Theme.INK);
		g.fillRect(0, 0, WIDTH + pad * 2, h + pad * 2);
		g.translate(pad, pad);
		g.setClip(0, 0, WIDTH, h);
		c.printAll(g);
		g.dispose();
		String dir = System.getProperty("codex.shots");
		if (dir != null)
		{
			File out = new File(dir);
			out.mkdirs();
			ImageIO.write(img, "png", new File(out, name));
		}
		return img;
	}

	private static void layout(Component c)
	{
		c.doLayout();
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				layout(child);
			}
		}
	}
}
