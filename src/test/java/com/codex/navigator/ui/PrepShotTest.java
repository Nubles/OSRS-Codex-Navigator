package com.codex.navigator.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.codex.navigator.TestAccounts;
import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.GoalStore;
import com.codex.navigator.core.Holdings;
import com.codex.navigator.core.Planner;
import com.codex.navigator.core.QuestPrep;
import com.codex.navigator.core.Recommender;
import com.codex.navigator.data.CodexData;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.SkillIconManager;
import org.junit.Test;

/**
 * The prep card as the panel draws it, at the side panel's width: on the Compass under the focus, on a quest's own
 * page, with Bank Steward's bank, with no bank seen, and for a quest whose list is not bundled. Set
 * {@code -Dcodex.shots=<dir>} to save the pictures.
 */
public class PrepShotTest
{
	/** The side panel's width less its scroll bar. */
	private static final int WIDTH = 217;
	/** Now, as the Compass reads the clock itself. */
	private static final long NOW = System.currentTimeMillis();

	private final CodexData data = CodexData.load(new Gson());
	private final Planner planner = new Planner(data);

	private static Map<Integer, Long> items(long... pairs)
	{
		Map<Integer, Long> out = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			out.put((int) pairs[i], pairs[i + 1]);
		}
		return out;
	}

	private NavigatorPanel panel(AccountSnapshot account, Holdings holdings, Goal... pins)
	{
		GoalStore goals = new GoalStore();
		for (Goal g : pins)
		{
			goals.toggle(g);
		}
		NavigatorPanel panel = new NavigatorPanel(planner, new Recommender(planner), goals, new SkillIconManager(), () -> true);
		panel.update(account);
		panel.setHoldings(holdings);
		return panel;
	}

	/** The Regicide trip from the mid account: pies and coal banked, a bow and rope carried. */
	private static Holdings regicideKit()
	{
		return new Holdings(items(ItemID.MAGIC_SHORTBOW, 1, ItemID.ROPE, 2, ItemID.TINDERBOX, 1),
			items(ItemID.SUMMER_PIE, 3, ItemID.COAL, 4, ItemID.SPADE, 1, ItemID.PESTLE_AND_MORTAR, 1, ItemID.POT_EMPTY, 1,
				ItemID._4DOSESTAMINA, 2, ItemID.LEATHER_GLOVES, 1),
			Holdings.BankSource.STEWARD, NOW - 3 * 3_600_000L);
	}

	@Test
	public void compassCarriesThePrepForTheFocusQuest() throws Exception
	{
		AccountSnapshot account = TestAccounts.mid(data).build();
		NavigatorPanel panel = panel(account, regicideKit(), Goal.quest("Regicide"));
		JComponent view = CompassView.build(panel);
		BufferedImage img = shoot(view, "1-compass-regicide.png", 1300);
		assertTrue(img.getHeight() > 300);
		assertTrue(text(view).contains("NEXT QUEST"));
		assertTrue(text(view).contains("Summer pie (+5)"));
		assertTrue(text(view).contains("3 h ago"));
	}

	@Test
	public void aQuestPageWithNoBankSeen() throws Exception
	{
		AccountSnapshot account = TestAccounts.mid(data).quest("Dragon Slayer I", QuestState.NOT_STARTED).build();
		Holdings carried = new Holdings(items(ItemID.HAMMER, 1, ItemID.COINS, 1200), null, Holdings.BankSource.NONE, 0);
		QuestPrep prep = QuestPrep.of(planner, "Dragon Slayer I", account, carried);
		Card card = PrepCard.build(prep, false, null, NOW);
		shoot(card, "2-quest-page-bank-unseen.png", 0);
		assertTrue(text(card).contains("Bank not seen yet"));
	}

	@Test
	public void aBlockedQuestWithItsWaits() throws Exception
	{
		AccountSnapshot account = TestAccounts.mid(data).build();
		Holdings live = new Holdings(items(ItemID.COINS, 20_000),
			items(ItemID.BLACK_FULL_HELM, 1, ItemID.BLACK_PLATEBODY, 1, ItemID.FISHING_ROD, 1, ItemID.RUNE_PICKAXE, 1),
			Holdings.BankSource.LIVE, NOW - 12 * 60_000L);
		QuestPrep prep = QuestPrep.of(planner, "Heroes' Quest", account, live);
		Card card = PrepCard.build(prep, true, q -> { }, NOW);
		shoot(card, "3-blocked-heroes-quest.png", 0);
		assertTrue(text(card).contains("STILL IN THE WAY"));
		assertTrue(text(card).contains("12 min ago"));
	}

	@Test
	public void aQuestWhoseListIsNotBundledSaysSo() throws Exception
	{
		AccountSnapshot account = TestAccounts.mid(data).build();
		QuestPrep prep = QuestPrep.of(planner, "Monkey Madness I", account, regicideKit());
		Card card = PrepCard.build(prep, true, q -> { }, NOW);
		shoot(card, "4-not-bundled.png", 0);
		assertTrue(text(card).contains("does not carry this quest's item list"));
	}

	@Test
	public void theQuestDetailPageHasThePrep() throws Exception
	{
		AccountSnapshot account = TestAccounts.mid(data).build();
		NavigatorPanel panel = panel(account, regicideKit(), Goal.quest("Regicide"));
		JComponent view = DetailView.build(panel, Goal.quest("Regicide"), false, false, () -> { }, () -> { }, () -> { });
		shoot(view, "5-detail-regicide.png", 1400);
		assertTrue(text(view).contains("BEFORE YOU SET OFF"));
	}

	@Test
	public void wordsForEachState()
	{
		assertEquals(", 3 h ago", PrepCard.ago(NOW - 3 * 3_600_000L, NOW));
		assertEquals(", 2 days ago", PrepCard.ago(NOW - 50 * 3_600_000L, NOW));
		assertEquals("", PrepCard.ago(0, NOW));
		AccountSnapshot account = TestAccounts.mid(data).build();
		QuestPrep prep = QuestPrep.of(planner, "Regicide", account, regicideKit());
		assertEquals("Boost and go", pillText(PrepCard.statusPill(prep)));
		String summary = PrepCard.summary(prep);
		assertTrue(summary, summary.endsWith("to get"));
		Holdings none = Holdings.none();
		assertTrue(PrepCard.bankLine(none, NOW).startsWith("Bank not seen yet"));
		assertEquals(Collections.emptyList(), QuestPrep.of(planner, "Recruitment Drive", account, none).getItems());
	}

	private static String pillText(Pill pill)
	{
		try
		{
			java.lang.reflect.Field f = Pill.class.getDeclaredField("text");
			f.setAccessible(true);
			return (String) f.get(pill);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	/** Every label's text in the tree, so a test can read what the card says. */
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

	/** Paints the component at the panel's width over the panel's ink, cut to {@code maxHeight} when above 0. */
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
}
