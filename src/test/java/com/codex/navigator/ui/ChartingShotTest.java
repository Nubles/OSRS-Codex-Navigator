package com.codex.navigator.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.TestAccounts;
import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.GoalStore;
import com.codex.navigator.core.Planner;
import com.codex.navigator.core.Recommender;
import com.codex.navigator.core.SeaCharting;
import com.codex.navigator.data.CodexData;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.BitSet;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import org.junit.Test;

/**
 * Sea charting as the panel draws it, at the side panel's width: the Atlas list, an ocean opened, the goal's page, the
 * focus on the Compass, a low level and logged out. Set {@code -Dcodex.shots=<dir>} to save the pictures.
 */
public class ChartingShotTest
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

	/**
	 * 54 Sailing, sailing in Catherby Bay: every chart of the Ardent Ocean up to 22 Sailing done and most of the
	 * Western Ocean's low ones, charted over three hours at about 18 an hour, with the places seen.
	 */
	private AccountSnapshot sailor()
	{
		SeaCharting.Log log = new SeaCharting.Log();
		BitSet done = new BitSet();
		long t = 0;
		int n = 0;
		for (SeaCharting.Chart c : SeaCharting.charts())
		{
			String ocean = c.getSea().getOcean();
			boolean take = "Ardent Ocean".equals(ocean) && c.getLevel() <= 22
				|| "Western Ocean".equals(ocean) && c.getLevel() <= 12 && n % 3 != 0
				|| "Unquiet Ocean".equals(ocean) && c.getKind() == SeaCharting.Kind.GENERIC;
			n++;
			if (!take)
			{
				continue;
			}
			done.set(c.getIndex());
			if (done.cardinality() <= 56)
			{
				log.charted(c, t, new int[]{c.getSea().getX() + 6, c.getSea().getY() - 4}, true);
				t += 200_000;
			}
		}
		SeaCharting.Sea catherby = sea("Catherby Bay");
		SeaCharting.View view = log.view(true, done, true, catherby.getX() + 20, catherby.getY() - 10);
		return TestAccounts.mid(data).skill(Skill.SAILING, 54, Experience.getXpForLevel(54) + 12_000).charting(view).build();
	}

	private static SeaCharting.Sea sea(String name)
	{
		for (SeaCharting.Sea s : SeaCharting.seas())
		{
			if (s.getName().equals(name))
			{
				return s;
			}
		}
		throw new AssertionError(name);
	}

	@Test
	public void theAtlasShowsTheChartsTheRoseAndTheNearest() throws Exception
	{
		ChartingView.open("Ardent Ocean", false);
		NavigatorPanel panel = panel(sailor());
		JComponent view = ChartingView.atlas(panel);
		shoot(view, "1-atlas-sea-charting.png", 0);
		String text = text(view);
		assertTrue(text, text.contains("EVERY SEA CHART"));
		assertTrue(text, text.contains("charts to go"));
		assertTrue(text, text.contains("open at your 54 Sailing"));
		assertTrue(text, text.contains("of charting at your pace"));
		assertTrue(text, text.contains("charts an hour, learned from"));
		assertTrue(text, text.contains("100,000 Sailing experience"));
		assertTrue(text, text.contains("Chartin' Charles McAtless"));
		assertTrue(text, text.contains("Catherby Bay"));
		assertTrue(text, text.contains("At 57 Sailing"));
		assertTrue(text, text.contains("Northern Ocean"));
		assertTrue(text, text.contains("About 14 h"));
	}

	@Test
	public void anOceanOpensToItsSeas() throws Exception
	{
		ChartingView.open("Ardent Ocean", true);
		try
		{
			NavigatorPanel panel = panel(sailor());
			StackPanel stack = new StackPanel(6, 8);
			ChartingView.oceans(stack, panel, panel.account().charting(), () -> { });
			shoot(stack, "2-oceans-ardent-open.png", 0);
			String text = text(stack);
			assertTrue(text, text.contains("Bay of Sarim"));
			assertTrue(text, text.contains("The Storm Tempor"));
		}
		finally
		{
			ChartingView.open("Ardent Ocean", false);
		}
	}

	@Test
	public void theGoalsPageCarriesTheChartsAndTheSailingLevel() throws Exception
	{
		NavigatorPanel panel = panel(sailor(), Goal.charting());
		JComponent view = DetailView.build(panel, Goal.charting(), false, false, () -> { }, () -> { }, () -> { });
		shoot(view, "3-detail-sea-charting.png", 0);
		String text = text(view);
		assertTrue(text, text.contains("Sea charting"));
		assertTrue(text, text.contains("of 358 charted"));
		assertTrue(text, text.contains("Sailing"));
		assertFalse(text, text.contains("Pin sea charting"));
	}

	@Test
	public void theCompassCarriesAChartingFocus() throws Exception
	{
		NavigatorPanel panel = panel(sailor(), Goal.charting(), Goal.quest("Regicide"));
		JComponent compass = CompassView.build(panel);
		shoot(compass, "4-compass-charting-focus.png", 560);
		assertTrue(text(compass).contains("Sea charting"));
		JComponent goals = GoalsView.build(panel);
		shoot(goals, "5-goals-charting.png", 520);
		assertTrue(text(goals).contains("Sea charting"));
	}

	@Test
	public void aNewSailorAndLoggedOut() throws Exception
	{
		AccountSnapshot fresh = TestAccounts.mid(data).skill(Skill.SAILING, 1, 0)
			.charting(SeaCharting.View.of(new BitSet(), 0, 0, 3050, 3200)).build();
		NavigatorPanel first = panel(fresh);
		JComponent view = ChartingView.atlas(first);
		shoot(view, "6-atlas-new-sailor.png", 760);
		String text = text(view);
		assertTrue(text, text.contains("358 charts to go"));
		assertTrue(text, text.contains("Your pace shows after a few charts"));
		assertTrue(text, text.contains("At 12 Sailing"));

		NavigatorPanel out = panel(AccountSnapshot.empty());
		JComponent loggedOut = ChartingView.atlas(out);
		shoot(loggedOut, "7-atlas-logged-out.png", 420);
		assertTrue(text(loggedOut).contains("Log in to read your sea charts"));
	}

	@Test
	public void theAtlasHasAChartsList() throws Exception
	{
		NavigatorPanel panel = panel(sailor());
		AtlasView atlas = new AtlasView(panel);
		atlas.showList(4);
		atlas.rebuild();
		atlas.setPreferredSize(new Dimension(WIDTH, 520));
		shoot(atlas, "0-atlas-tabs.png", 520);
		assertTrue(text(atlas).contains("EVERY SEA CHART"));
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
