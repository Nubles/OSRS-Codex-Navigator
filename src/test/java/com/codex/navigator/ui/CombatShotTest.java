package com.codex.navigator.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.CaFixture;
import com.codex.navigator.TestAccounts;
import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.CombatTasks;
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
import java.util.BitSet;
import java.util.Collections;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import net.runelite.client.game.SkillIconManager;
import org.junit.Test;

/**
 * Combat achievements as the panel draws them, at the side panel's width: the Atlas list, a tier goal's page, the
 * focus on the Compass and Goals, logged out, and an account with no kill counts saved. Set
 * {@code -Dcodex.shots=<dir>} to save the pictures.
 */
public class CombatShotTest
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

	private AccountSnapshot mid()
	{
		return CaFixture.account(TestAccounts.mid(data), CaFixture.mid()).build();
	}

	@Test
	public void theAtlasListsTheLedgerTheNearestTasksAndYourBosses() throws Exception
	{
		NavigatorPanel panel = panel(mid());
		JComponent view = CombatView.atlas(panel);
		shoot(view, "1-atlas-combat.png", 0);
		String text = text(view);
		assertTrue(text, text.contains("NEXT REWARD"));
		assertTrue(text, text.contains("points to go"));
		assertTrue(text, text.contains("Ghommal's hilt 3"));
		assertTrue(text, text.contains("38 of 50 kills"));
		assertTrue(text, text.contains("KC 212"));
		assertTrue(text, text.contains("Kill counts from Boss Pilot and RuneLite."));
	}

	@Test
	public void aTierGoalsPageCarriesItsRewardAndTheNearestTasks() throws Exception
	{
		NavigatorPanel panel = panel(mid(), Goal.combat("Hard"));
		JComponent view = DetailView.build(panel, Goal.combat("Hard"), false, false, () -> { }, () -> { }, () -> { });
		shoot(view, "2-detail-hard.png", 0);
		String text = text(view);
		assertTrue(text, text.contains("Hard combat achievements"));
		assertTrue(text, text.contains("more points for Hard"));
		assertTrue(text, text.contains("AT HARD"));
		assertFalse(text, text.contains("Every quest on this path"));
	}

	@Test
	public void aReachedTierSaysSo() throws Exception
	{
		NavigatorPanel panel = panel(mid(), Goal.combat("Medium"));
		JComponent view = DetailView.build(panel, Goal.combat("Medium"), false, false, () -> { }, () -> { }, () -> { });
		shoot(view, "3-detail-medium-reached.png", 0);
		assertTrue(text(view).contains(" of 169 points"));
	}

	@Test
	public void theCompassAndGoalsCarryATierFocus() throws Exception
	{
		NavigatorPanel panel = panel(mid(), Goal.combat("Hard"), Goal.quest("Regicide"));
		JComponent compass = CompassView.build(panel);
		shoot(compass, "4-compass-hard-focus.png", 700);
		assertTrue(text(compass).contains("Hard combat achievements"));
		JComponent goals = GoalsView.build(panel);
		shoot(goals, "5-goals-hard.png", 700);
		assertTrue(text(goals).contains("Hard combat achievements"));
	}

	@Test
	public void loggedOutAndWithNoKillCounts() throws Exception
	{
		NavigatorPanel out = panel(AccountSnapshot.empty());
		JComponent view = CombatView.atlas(out);
		shoot(view, "6-atlas-logged-out.png", 0);
		assertTrue(text(view).contains("Log in to read your combat achievements"));

		CombatTasks fresh = new CombatTasks(CaFixture.tasks(), new BitSet(), null, Collections.emptyMap(), null);
		NavigatorPanel first = panel(CaFixture.account(TestAccounts.mid(data), fresh).build());
		JComponent none = CombatView.atlas(first);
		shoot(none, "7-atlas-no-kills.png", 0);
		assertTrue(text(none).contains("No kill counts saved yet"));
		assertTrue(text(none).contains("  ·  Easy"));
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
