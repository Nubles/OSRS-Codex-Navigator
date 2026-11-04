package com.codex.navigator.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.TestAccounts;
import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.Planner;
import com.codex.navigator.core.SkillNeeds;
import com.codex.navigator.data.CodexData;
import com.codex.navigator.ui.Theme;
import com.google.gson.Gson;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import javax.imageio.ImageIO;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.ui.FontManager;
import org.junit.Test;

/**
 * Paints the bars, exactly as {@link SkillBars#plan} lays them into the game's widgets, over a stand-in for the
 * skills tab, with the hover note beside it. Set {@code -Dcodex.shots=<dir>} to save the pictures.
 */
public class SkillTabShotTest
{
	/** The tab's tiles in their on-screen order: three columns, read across. */
	private static final Skill[] ORDER = {
		Skill.ATTACK, Skill.HITPOINTS, Skill.MINING,
		Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING,
		Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING,
		Skill.RANGED, Skill.THIEVING, Skill.COOKING,
		Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING,
		Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING,
		Skill.RUNECRAFT, Skill.SLAYER, Skill.FARMING,
		Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING,
	};
	private static final int TILE_W = 62;
	private static final int TILE_H = 30;
	private static final int GAP = 1;
	private static final int TAB_X = 268;
	private static final int TAB_Y = 14;
	private static final int TAB_W = 3 * TILE_W + 2 * GAP + 8;
	private static final int TAB_H = 8 * TILE_H + 7 * GAP + 30 + 8;
	private static final int SCALE = 2;

	private final CodexData data = CodexData.load(new Gson());
	private final Planner planner = new Planner(data);

	private AccountSnapshot account()
	{
		return TestAccounts.mid(data).build();
	}

	private SkillNeeds needs(AccountSnapshot account)
	{
		return SkillNeeds.of(planner, Arrays.asList(Goal.level(Skill.AGILITY, 70), Goal.quest("Dragon Slayer II"),
			Goal.diary("Ardougne Hard"), Goal.quest("Regicide")), account);
	}

	@Test
	public void goalBarsOverTheSkillsTab() throws Exception
	{
		AccountSnapshot account = account();
		SkillNeeds needs = needs(account);
		BufferedImage img = draw(account, needs, false, Skill.AGILITY);

		// The Agility tile's bar is gold where it is filled.
		int[] at = tileOrigin(Skill.AGILITY);
		SkillBars.Rect fill = SkillBars.plan(TILE_W, account.xp(Skill.AGILITY), needs.get(Skill.AGILITY), false)[SkillBars.FILL];
		Color px = new Color(img.getRGB(at[0] + fill.x + 1, at[1] + fill.top(TILE_H)));
		assertTrue(px.toString(), px.getRed() > 150 && px.getRed() > px.getBlue() + 40);
		// Strength (72) is no goal's need: its tile carries no bar.
		int[] str = tileOrigin(Skill.STRENGTH);
		assertEquals(tileColor(), new Color(img.getRGB(str[0] + 20, str[1] + TILE_H - SkillBars.LIFT - 1)));
		save(img, "skills-tab-goal-bars.png");
	}

	@Test
	public void quieterBarsToTheNextLevelWhenAsked() throws Exception
	{
		// Experience spread through each level, so the quieter bars show a range of fills.
		AccountSnapshot.Builder b = TestAccounts.mid(data);
		AccountSnapshot base = b.build();
		int i = 0;
		for (Skill skill : ORDER)
		{
			int level = base.level(skill);
			int from = Experience.getXpForLevel(level);
			int to = Experience.getXpForLevel(level + 1);
			b.skill(skill, level, from + (int) ((to - from) * ((i++ * 37 % 90) / 100.0)));
		}
		AccountSnapshot account = b.build();
		BufferedImage img = draw(account, needs(account), true, Skill.MAGIC);
		int[] str = tileOrigin(Skill.STRENGTH);
		Color track = new Color(img.getRGB(str[0] + TILE_W - SkillBars.INSET - 2, str[1] + TILE_H - SkillBars.LIFT - 1));
		assertTrue("a track is drawn on Strength", !track.equals(tileColor()));
		save(img, "skills-tab-next-level.png");
	}

	@Test
	public void theNoteNamesTheGoalsAndTheExperienceLeft()
	{
		AccountSnapshot account = account();
		SkillNeeds needs = needs(account);
		SkillTabTooltip.Tip tip = SkillTabTooltip.tip(Skill.AGILITY, account.xp(Skill.AGILITY), needs.get(Skill.AGILITY));
		assertNotNull(tip);
		assertEquals(70, tip.target);
		assertEquals(52, tip.level);
		// In pin order: the level goal, Dragon Slayer II, the diary (Regicide is on its path) and Regicide itself.
		assertEquals(4, tip.rows.size());
		assertEquals("70 Agility", tip.rows.get(0).title);
		assertEquals(SkillBars.xpLeft(account.xp(Skill.AGILITY), 70), tip.xpLeft);
		assertEquals(SkillBars.xpLeft(account.xp(Skill.AGILITY), 60), tip.rows.get(1).xpLeft);
		assertTrue(tip.rows.get(3).boost);
		assertEquals("Regicide", tip.rows.get(3).title);
		assertEquals(Arrays.asList("70 Agility"), tip.holdsUp);
		assertEquals(Arrays.asList(56, 60), tip.waypoints);
		// Nothing is shown for a skill no goal needs.
		assertEquals(null, SkillTabTooltip.tip(Skill.STRENGTH, account.xp(Skill.STRENGTH), needs.get(Skill.STRENGTH)));

		BufferedImage img = new BufferedImage(SkillTabTooltip.WIDTH, 220, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		Dimension size = SkillTabTooltip.paint(g, tip);
		g.dispose();
		assertEquals(SkillTabTooltip.WIDTH, size.width);
		// The last line ends inside the note: the measured height and the painted lines agree.
		assertTrue(size.height < 220);
		int lastInk = 0;
		for (int y = 0; y < img.getHeight(); y++)
		{
			for (int x = 10; x < 100; x++)
			{
				Color c = new Color(img.getRGB(x, y), true);
				if (c.getAlpha() == 255 && c.getRed() > 90)
				{
					lastInk = y;
				}
			}
		}
		assertTrue("text at " + lastInk + " spills past " + size.height, lastInk < size.height - 3);
	}

	private static Color tileColor()
	{
		return new Color(62, 53, 41);
	}

	private static int[] tileOrigin(Skill skill)
	{
		int i = Arrays.asList(ORDER).indexOf(skill);
		int col = i % 3;
		int row = i / 3;
		return new int[]{TAB_X + 4 + col * (TILE_W + GAP), TAB_Y + 4 + row * (TILE_H + GAP)};
	}

	private BufferedImage draw(AccountSnapshot account, SkillNeeds needs, boolean plain, Skill hover) throws Exception
	{
		int w = TAB_X + TAB_W + 12;
		int h = TAB_Y + TAB_H + 14;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		paintGameView(g, w, h);
		paintTab(g, account);
		for (Skill skill : ORDER)
		{
			int[] o = tileOrigin(skill);
			SkillBars.Rect[] plan = SkillBars.plan(TILE_W, account.xp(skill), needs.get(skill), plain);
			if (plan == null)
			{
				continue;
			}
			for (SkillBars.Rect r : plan)
			{
				if (r == null)
				{
					continue;
				}
				// A widget's opacity runs backwards: 0 is solid.
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1 - r.seeThrough / 255f));
				g.setColor(r.color);
				g.fillRect(o[0] + r.x, o[1] + r.top(TILE_H), r.width, r.height);
			}
			g.setComposite(AlphaComposite.SrcOver);
		}

		// The pointer on the hovered tile, and the note placed as the overlay places it: left of the tab.
		int[] o = tileOrigin(hover);
		SkillTabTooltip.Tip tip = SkillTabTooltip.tip(hover, account.xp(hover), needs.get(hover));
		if (tip != null)
		{
			g.setFont(Theme.small());
			Dimension size = SkillTabTooltip.size(g, tip);
			int x = TAB_X - size.width - 6;
			int y = Math.max(6, Math.min(h - size.height - 6, o[1] + TILE_H / 2 - size.height / 2));
			g.translate(x, y);
			SkillTabTooltip.paint(g, tip);
			g.translate(-x, -y);
		}
		paintCursor(g, o[0] + 22, o[1] + 14);
		g.dispose();

		BufferedImage big = new BufferedImage(w * SCALE, h * SCALE, BufferedImage.TYPE_INT_RGB);
		Graphics2D bg = big.createGraphics();
		bg.drawImage(img, 0, 0, w * SCALE, h * SCALE, null);
		bg.dispose();
		lastBig = big;
		return img;
	}

	private BufferedImage lastBig;

	/** A dim, mottled stand-in for the game world beside the tab. */
	private static void paintGameView(Graphics2D g, int w, int h)
	{
		g.setColor(new Color(46, 54, 38));
		g.fillRect(0, 0, w, h);
		Random r = new Random(7);
		for (int i = 0; i < 900; i++)
		{
			int s = 6 + r.nextInt(18);
			g.setColor(new Color(40 + r.nextInt(22), 48 + r.nextInt(20), 32 + r.nextInt(14)));
			g.fillOval(r.nextInt(w), r.nextInt(h), s, s / 2 + 2);
		}
	}

	/** A plausible stand-in for the skills tab: stone panel, bevelled tiles, icon and the level twice in yellow. */
	private static void paintTab(Graphics2D g, AccountSnapshot account) throws Exception
	{
		g.setColor(new Color(73, 64, 52));
		g.fillRect(TAB_X - 3, TAB_Y - 3, TAB_W + 6, TAB_H + 6);
		g.setColor(new Color(40, 34, 26));
		g.drawRect(TAB_X - 3, TAB_Y - 3, TAB_W + 5, TAB_H + 5);
		g.setColor(new Color(84, 74, 60));
		g.fillRect(TAB_X, TAB_Y, TAB_W, TAB_H);
		g.setFont(FontManager.getRunescapeSmallFont());
		for (Skill skill : ORDER)
		{
			int[] o = tileOrigin(skill);
			bevel(g, o[0], o[1], TILE_W, TILE_H);
			try (InputStream in = SkillTabShotTest.class.getResourceAsStream(
				"/skill_icons/" + skill.getName().toLowerCase(Locale.ROOT) + ".png"))
			{
				if (in != null)
				{
					BufferedImage icon = ImageIO.read(in);
					g.drawImage(icon, o[0] + 4 + (22 - icon.getWidth()) / 2, o[1] + (TILE_H - icon.getHeight()) / 2, null);
				}
			}
			String lvl = String.valueOf(account.level(skill));
			yellow(g, lvl, o[0] + 34, o[1] + 13);
			yellow(g, lvl, o[0] + 46, o[1] + 25);
			g.setColor(new Color(255, 255, 0));
			g.drawLine(o[0] + 41, o[1] + 22, o[0] + 47, o[1] + 9);
		}
		int ty = TAB_Y + 4 + 8 * (TILE_H + GAP) + 2;
		bevel(g, TAB_X + 4, ty, TAB_W - 8, 24);
		yellow(g, "Total level: " + account.totalLevel(), TAB_X + 58, ty + 16);
	}

	private static void bevel(Graphics2D g, int x, int y, int w, int h)
	{
		g.setColor(tileColor());
		g.fillRect(x, y, w, h);
		g.setColor(new Color(92, 81, 64));
		g.drawLine(x, y, x + w - 1, y);
		g.drawLine(x, y, x, y + h - 1);
		g.setColor(new Color(33, 28, 21));
		g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
		g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
	}

	private static void yellow(Graphics2D g, String text, int x, int y)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(new Color(255, 255, 0));
		g.drawString(text, x, y);
	}

	private static void paintCursor(Graphics2D g, int x, int y)
	{
		Polygon p = new Polygon(new int[]{x, x, x + 4, x + 7, x + 9, x + 6, x + 11},
			new int[]{y, y + 15, y + 11, y + 17, y + 16, y + 10, y + 10}, 7);
		g.setColor(Color.WHITE);
		g.fillPolygon(p);
		g.setColor(Color.BLACK);
		g.drawPolygon(p);
	}

	private void save(BufferedImage img, String name) throws Exception
	{
		String dir = System.getProperty("codex.shots");
		if (dir != null)
		{
			ImageIO.write(lastBig, "png", new File(dir, name));
		}
	}
}
