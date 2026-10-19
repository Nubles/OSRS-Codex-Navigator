package com.codex.navigator.ui;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Effort;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Skill;

/**
 * The skills whose training speed the Codex has learned, and how they compare with its steady rates. Every time
 * estimate in the panel already uses these; the card says so, so a shorter or longer estimate is never a mystery.
 */
final class PaceCard
{
	/** Skills listed before the rest fold into a count. */
	private static final int SHOWN = 5;

	private PaceCard()
	{
	}

	static Card build(Navigation nav)
	{
		AccountSnapshot account = nav.account();
		List<Skill> skills = new ArrayList<>(account.paces().keySet());
		// The skills learned from the most play first: those are the figures to trust.
		skills.sort((a, b) -> Double.compare(account.paceHours(b), account.paceHours(a)));
		Card card = new Card(new BorderLayout(0, 6));
		card.setBorder(new EmptyBorder(8, 10, 8, 10));
		StackPanel rows = new StackPanel(6, 0);
		for (int i = 0; i < Math.min(SHOWN, skills.size()); i++)
		{
			rows.add(row(nav, skills.get(i)));
		}
		if (skills.size() > SHOWN)
		{
			rows.add(Ui.label("+ " + (skills.size() - SHOWN) + " more skills", Theme.small(), Theme.TEXT_FAINT));
		}
		card.add(rows, BorderLayout.CENTER);
		card.add(GoalCard.hint("Learned from your own training. Every time in the Codex uses it."), BorderLayout.SOUTH);
		return card;
	}

	private static JPanel row(Navigation nav, Skill skill)
	{
		AccountSnapshot account = nav.account();
		double factor = account.pace(skill);
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		JLabel icon = new JLabel();
		java.awt.image.BufferedImage image = nav.skillIcon(skill);
		if (image != null)
		{
			icon.setIcon(new ImageIcon(image));
		}
		icon.setPreferredSize(new Dimension(18, 18));
		icon.setVerticalAlignment(JLabel.TOP);
		row.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel(new BorderLayout(0, 1));
		text.setOpaque(false);
		JPanel line = new JPanel(new BorderLayout());
		line.setOpaque(false);
		line.add(Ui.label(skill.getName(), Theme.small(), Theme.TEXT), BorderLayout.WEST);
		line.add(Ui.label(compare(factor), Theme.small(), color(factor)), BorderLayout.EAST);
		text.add(line, BorderLayout.NORTH);
		String rate = perHour(Effort.xpPerHour(skill, account.level(skill)) * factor);
		text.add(Ui.label("~" + rate + " xp/h  ·  from " + hours(account.paceHours(skill)), Theme.small(),
			Theme.TEXT_MUTED), BorderLayout.SOUTH);
		row.add(text, BorderLayout.CENTER);
		row.setToolTipText(String.format("Your %s training runs at %.2f times the Codex's steady rate", skill.getName(), factor));
		return row;
	}

	/** "40% faster" or "25% slower", against the steady rates. */
	static String compare(double factor)
	{
		int percent = (int) Math.round(Math.abs(factor - 1) * 100);
		if (percent == 0)
		{
			return "as usual";
		}
		if (factor > 1)
		{
			return factor >= 2 ? String.format("%.1fx faster", factor) : percent + "% faster";
		}
		return percent + "% slower";
	}

	private static Color color(double factor)
	{
		return factor >= 1 ? Theme.TEAL : Theme.EMBER;
	}

	private static String hours(double hours)
	{
		if (hours < 1)
		{
			return Math.max(1, Math.round(hours * 60)) + " min";
		}
		double tenths = Math.round(hours * 10) / 10.0;
		return (tenths == Math.floor(tenths) ? String.valueOf((long) tenths) : String.valueOf(tenths)) + " h";
	}

	/**
	 * A rate by the suite's one rule: in full below 10,000 ("8,240"), whole thousands below a million ("57k"),
	 * millions to one decimal ("1.2M"), billions to two. A figure that rounds up to a thousand of one unit is written
	 * in the next ("1.0M", never "1000k").
	 */
	static String perHour(double xp)
	{
		long n = Math.round(Math.max(0, xp));
		if (n < 10_000)
		{
			return String.format(Locale.ENGLISH, "%,d", n);
		}
		if (n < 999_500)
		{
			return Math.round(n / 1_000.0) + "k";
		}
		if (n < 999_950_000)
		{
			return String.format(Locale.ENGLISH, "%.1fM", n / 1_000_000.0);
		}
		return String.format(Locale.ENGLISH, "%.2fB", n / 1_000_000_000.0);
	}
}
