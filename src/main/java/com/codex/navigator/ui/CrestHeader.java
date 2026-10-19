package com.codex.navigator.ui;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.data.CodexData;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.geom.Path2D;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Set;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The account crest at the top of the panel: one ring for the whole account and
 * three ledgers beneath the name for quests, diaries and combat tasks.
 */
public class CrestHeader extends JPanel
{
	private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.ENGLISH);

	private final ProgressRing ring = new ProgressRing(60, 6f);
	private final JLabel name = Ui.label("Codex", Theme.title(), Theme.GOLD_BRIGHT);
	private final JLabel summary = Ui.label("Your account compass", Theme.small(), Theme.TEXT_MUTED);
	private final Ledger quests = new Ledger("Quests");
	private final Ledger diaries = new Ledger("Diaries");
	private final Ledger combat = new Ledger("Combat tasks");

	public CrestHeader()
	{
		super(new BorderLayout(10, 0));
		setOpaque(false);
		setBorder(new EmptyBorder(12, 10, 12, 10));

		JPanel ringHolder = new JPanel(new BorderLayout());
		ringHolder.setOpaque(false);
		ringHolder.add(ring, BorderLayout.NORTH);
		add(ringHolder, BorderLayout.WEST);

		JPanel right = new JPanel();
		right.setOpaque(false);
		right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
		name.setAlignmentX(LEFT_ALIGNMENT);
		summary.setAlignmentX(LEFT_ALIGNMENT);
		right.add(name);
		right.add(summary);

		JPanel ledgers = new JPanel(new GridLayout(3, 1, 0, 2));
		ledgers.setOpaque(false);
		ledgers.setBorder(new EmptyBorder(4, 0, 0, 0));
		quests.setToolTipText("Quests done, counted as the quest cape counts them");
		ledgers.add(quests);
		ledgers.add(diaries);
		ledgers.add(combat);
		ledgers.setAlignmentX(LEFT_ALIGNMENT);
		right.add(ledgers);
		add(right, BorderLayout.CENTER);
	}

	/**
	 * @param capeQuests the quests as the quest cape counts them (every quest, and the miniquests a quest needs
	 *                   first), so the crest and the cape's page always show the same figure
	 */
	public void update(AccountSnapshot account, CodexData data, Set<String> capeQuests)
	{
		int[] cape = capeCount(account, data, capeQuests);
		int questDone = cape[0];
		int questTotal = cape[1];
		int diaryTotal = data.diaries().size();
		int diaryDone = account.diariesCompleted();
		int caTotal = 0;
		int caDone = 0;
		for (String tier : CodexData.COMBAT_ACHIEVEMENT_TIERS)
		{
			caTotal += data.combatAchievementTasks(tier);
			caDone += Math.min(account.combatAchievementsDone(tier), data.combatAchievementTasks(tier));
		}

		boolean in = account.isLoggedIn();
		name.setText(in && account.getPlayerName() != null ? account.getPlayerName() : "Codex");
		summary.setText(in
			? "Total " + NUMBER.format(account.totalLevel()) + "  ·  Combat " + account.getCombatLevel()
			: "Your account compass");
		quests.set(questDone, questTotal, in);
		diaries.set(diaryDone, diaryTotal, in);
		combat.set(caDone, caTotal, in);

		double overall = !in ? 0 : (ratio(questDone, questTotal) + ratio(diaryDone, diaryTotal) + ratio(caDone, caTotal)) / 3;
		ring.setProgress(overall, in && overall >= 1, true);
		ring.setCenterText(in ? null : "Log in", Theme.TEXT_MUTED);
		ring.setToolTipText("Account completion across quests, diaries and combat achievement tasks");
	}

	/** Done and total over the cape's quests, skipping any name the data does not know, as the cape's plan does. */
	static int[] capeCount(AccountSnapshot account, CodexData data, Set<String> capeQuests)
	{
		int done = 0;
		int total = 0;
		for (String quest : capeQuests)
		{
			if (data.quest(quest) == null)
			{
				continue;
			}
			total++;
			if (account.questDone(quest))
			{
				done++;
			}
		}
		return new int[]{done, total};
	}

	private static double ratio(int done, int total)
	{
		return total <= 0 ? 0 : done / (double) total;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.INK, Theme.GOLD_DIM, 0.16), 0, h, Theme.INK));
		g.fillRect(0, 0, w, h);

		// Gilt rule along the bottom with a small diamond at its centre.
		int y = h - 2;
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.GOLD, 0), w / 2f, 0, Theme.alpha(Theme.GOLD, 150), true));
		g.drawLine(0, y, w, y);
		Path2D diamond = new Path2D.Float();
		diamond.moveTo(w / 2f, y - 3);
		diamond.lineTo(w / 2f + 3, y);
		diamond.lineTo(w / 2f, y + 3);
		diamond.lineTo(w / 2f - 3, y);
		diamond.closePath();
		g.setColor(Theme.GOLD);
		g.fill(diamond);
		g.dispose();
		super.paintComponent(graphics);
	}

	/** One labelled count with a hairline bar under it. */
	private static final class Ledger extends JPanel
	{
		private final JLabel count = Ui.label("-", Theme.small(), Theme.TEXT);
		private final ProgressBar bar = new ProgressBar(3);

		Ledger(String label)
		{
			super(new BorderLayout(0, 1));
			setOpaque(false);
			JPanel line = new JPanel(new BorderLayout());
			line.setOpaque(false);
			line.add(Ui.label(label, Theme.small(), Theme.TEXT_MUTED), BorderLayout.WEST);
			line.add(count, BorderLayout.EAST);
			add(line, BorderLayout.CENTER);
			add(bar, BorderLayout.SOUTH);
			setPreferredSize(new Dimension(10, 17));
		}

		void set(int done, int total, boolean known)
		{
			count.setText((known ? String.valueOf(done) : "-") + "/" + total);
			count.setForeground(known ? Theme.TEXT : Theme.TEXT_FAINT);
			bar.setProgress(ratio(done, total), total > 0 && done >= total, true);
		}
	}
}
