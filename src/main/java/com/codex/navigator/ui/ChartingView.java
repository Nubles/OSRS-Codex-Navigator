package com.codex.navigator.ui;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Effort;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.SeaCharting;
import com.codex.navigator.core.Step;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Skill;

/**
 * Sea charting laid out for one account: the charts done of all 358 and the time left at this player's own pace, the
 * six kinds as a compass rose, the nearest charts the player's Sailing level allows, and every ocean with its seas.
 * Shared by the Atlas and the charting goal's page.
 */
final class ChartingView
{
	/** Nearest charts listed. */
	static final int NEAREST_SHOWN = 5;
	/** Oceans opened to show their seas; the panel's own state, kept while it runs. */
	private static final Set<String> OPEN = new HashSet<>();

	private ChartingView()
	{
	}

	/** The Atlas list: the ledger, the nearest charts and the oceans. */
	static StackPanel atlas(Navigation nav)
	{
		StackPanel stack = new StackPanel(6, 8);
		AccountSnapshot account = nav.account();
		SeaCharting.View view = account.charting();
		boolean known = account.isLoggedIn() && view.isKnown();
		stack.add(new SectionHeader("Sea charting", known ? view.doneCount() + " / " + SeaCharting.TOTAL : SeaCharting.TOTAL + " charts"));
		if (!known)
		{
			stack.add(GoalCard.hint("Log in to read your sea charts. The game keeps which are done for each account."));
			oceans(stack, nav, SeaCharting.View.NONE, () -> nav.showAtlas(4));
			return stack;
		}
		stack.add(ledger(nav, account, true));
		nearest(stack, account);
		oceans(stack, nav, view, () -> nav.showAtlas(4));
		stack.add(GoalCard.hint(footnote(view)));
		return stack;
	}

	/** The charting goal's own page, under its hero. */
	static void goal(StackPanel stack, Navigation nav, Goal goal)
	{
		AccountSnapshot account = nav.account();
		SeaCharting.View view = account.charting();
		if (!account.isLoggedIn() || !view.isKnown())
		{
			return;
		}
		stack.add(ledger(nav, account, false));
		nearest(stack, account);
		oceans(stack, nav, view, () -> nav.open(goal));
		stack.add(GoalCard.hint(footnote(view)));
	}

	/**
	 * The ledger card: charts left of all 358 and the time they take at this player's rate, the six kinds as a rose, the
	 * rewards for the lot, the rate itself and, in the Atlas, a pin.
	 */
	static Card ledger(Navigation nav, AccountSnapshot account, boolean withHead)
	{
		SeaCharting.View view = account.charting();
		int level = account.level(Skill.SAILING);
		int done = view.doneCount();
		int left = SeaCharting.TOTAL - done;
		boolean all = left <= 0;
		Card card = new Card(new BorderLayout(0, 8));
		card.setBorder(new EmptyBorder(10, 10, 10, 10));
		card.highlighted(true);

		JPanel head = new JPanel(new BorderLayout(8, 0));
		head.setOpaque(false);
		JPanel words = column();
		int wrap = withHead ? 120 : 190;
		if (withHead)
		{
			words.add(Ui.label(all ? "EVERY SEA CHARTED" : "EVERY SEA CHART", Theme.small(), all ? Theme.GREEN : Theme.GOLD));
			words.add(Ui.wrapped(all ? "All 358 charted" : left + (left == 1 ? " chart" : " charts") + " to go",
				Theme.title(), Theme.GOLD_BRIGHT, wrap));
		}
		else
		{
			words.add(Ui.wrapped(all ? "All 358 charted" : done + " of " + SeaCharting.TOTAL + " charted", Theme.body(),
				all ? Theme.GREEN : Theme.TEXT, wrap));
		}
		if (!all)
		{
			int open = view.openAt(level);
			String reach = open >= left ? "All open at your " + level + " Sailing"
				: open + " open at your " + level + " Sailing";
			words.add(Ui.wrapped(reach, Theme.body(), Theme.TEXT, wrap));
			double hours = view.hoursLeft();
			String when = hours > 0 ? "About " + Effort.format(hours).substring(1) + " of charting at your pace"
				: "Your pace shows after a few charts";
			words.add(Ui.wrapped(when, Theme.small(), hours > 0 ? Theme.TEAL : Theme.TEXT_MUTED, wrap));
		}
		head.add(words, BorderLayout.CENTER);
		if (withHead)
		{
			ProgressRing ring = new ProgressRing(56, 5f);
			ring.setProgress(done / (double) SeaCharting.TOTAL, all, false);
			ring.setCaption(done + "/" + SeaCharting.TOTAL);
			head.add(ring, BorderLayout.EAST);
		}
		card.add(head, BorderLayout.NORTH);

		card.add(new ChartRose(view, level), BorderLayout.CENTER);

		JPanel foot = column();
		foot.add(Ui.label("FOR CHARTING IT ALL", Theme.small(), Theme.GOLD_DIM));
		bullet(foot, CombatView.format(SeaCharting.FULL_BONUS_XP) + " Sailing experience, on top of each chart's own");
		bullet(foot, "Ocean bonuses from " + SeaCharting.CHARTS_NPC + " in the Pandemonium Cave");
		if (level < SeaCharting.FULL_LEVEL)
		{
			bullet(foot, "The last charts need " + SeaCharting.FULL_LEVEL + " Sailing");
		}
		JLabel paceHead = Ui.label("YOUR PACE", Theme.small(), Theme.GOLD_DIM);
		paceHead.setBorder(new EmptyBorder(6, 0, 0, 0));
		foot.add(paceHead);
		String pace = view.getPerHour() > 0
			? Math.round(view.getPerHour()) + " charts an hour, learned from " + hours(view.getHours()) + " of charting"
			: "Not learned yet. Charts made less than twenty minutes apart are timed; the time between trips never counts.";
		JLabel paceLine = Ui.wrapped(pace, Theme.small(), Theme.TEXT_MUTED, 190);
		paceLine.setBorder(new EmptyBorder(2, 0, 0, 0));
		foot.add(paceLine);
		if (withHead)
		{
			Goal goal = Goal.charting();
			boolean pinned = nav.goals().isPinned(goal);
			JPanel buttons = new JPanel(new BorderLayout(6, 0));
			buttons.setOpaque(false);
			buttons.setBorder(new EmptyBorder(8, 0, 0, 0));
			buttons.add(new CodexButton(pinned ? "Pinned" : "Pin sea charting", !pinned, () ->
			{
				if (!nav.goals().isPinned(goal))
				{
					nav.goals().toggle(goal);
				}
				nav.open(goal);
			}), BorderLayout.WEST);
			buttons.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			foot.add(buttons);
		}
		card.add(foot, BorderLayout.SOUTH);
		return card;
	}

	/**
	 * The nearest unfinished charts the player's Sailing level allows, each with its sea, its kind and how far that sea
	 * lies, then the level that opens the next ones.
	 */
	static void nearest(StackPanel stack, AccountSnapshot account)
	{
		SeaCharting.View view = account.charting();
		int level = account.level(Skill.SAILING);
		List<SeaCharting.Near> near = view.nearest(level, NEAREST_SHOWN);
		int next = view.nextLevel(level);
		if (near.isEmpty() && next == 0)
		{
			return;
		}
		stack.add(new SectionHeader("Nearest charts", view.hasWhere() ? "from here" : "at " + level + " Sailing"));
		for (SeaCharting.Near n : near)
		{
			stack.add(chartRow(view, n));
		}
		if (next > 0)
		{
			int opens = view.openAt(next) - view.openAt(level);
			stack.add(Row.of(Step.Status.BLOCKED, "At " + next + " Sailing",
				opens + (opens == 1 ? " more chart opens" : " more charts open"), null, null, null));
		}
	}

	/** One chart: its sea and kind, the task as the Captain's log hints it, and how far its sea lies. */
	static Card chartRow(SeaCharting.View view, SeaCharting.Near near)
	{
		SeaCharting.Chart c = near.getChart();
		Card card = new Card(new BorderLayout(8, 0));
		card.setBorder(new EmptyBorder(6, 8, 7, 8));
		JPanel glyphHolder = new JPanel(new BorderLayout());
		glyphHolder.setOpaque(false);
		glyphHolder.add(new Glyph(Step.Status.READY, 14), BorderLayout.NORTH);
		glyphHolder.setBorder(new EmptyBorder(1, 0, 0, 0));
		card.add(glyphHolder, BorderLayout.WEST);

		JPanel body = column();
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(Ui.label(c.getSea().getName(), Theme.body(), Theme.TEXT), BorderLayout.CENTER);
		top.add(new Pill(c.getKind().getLabel(), kindColour(c.getKind())), BorderLayout.EAST);
		top.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		body.add(top);
		JLabel task = Ui.wrapped(c.getTask(), Theme.small(), Theme.TEXT_MUTED, 158);
		task.setBorder(new EmptyBorder(2, 0, 0, 0));
		body.add(task);
		String where = near.getDistance() < 0 ? null
			: near.getDistance() < 64 ? (near.isLearned() ? "Here, where you charted before" : "Here")
			: "About " + CombatView.format(Math.round(near.getDistance() / 10f) * 10) + " tiles away";
		if (where != null)
		{
			JLabel w = Ui.label(where, Theme.small(), near.getDistance() < 64 ? Theme.TEAL : Theme.TEXT_FAINT);
			w.setBorder(new EmptyBorder(2, 0, 0, 0));
			body.add(w);
		}
		card.add(body, BorderLayout.CENTER);
		card.setToolTipText("<html>" + Ui.escape(c.getTask()) + "<br>" + Ui.escape(c.getKind().getLabel()) + " chart, "
			+ Ui.escape(c.getKind().getTool()) + "<br>" + c.getLevel() + " Sailing"
			+ (c.isLevelConfirmed() ? "" : " (from Quest Helper's list; the wiki leaves it blank)") + "<br>"
			+ Ui.escape(c.getSea().getName()) + ", " + Ui.escape(c.getSea().getOcean()) + "</html>");
		return card;
	}

	/** Each ocean with its charts done; an ocean opens to its seas. */
	static void oceans(StackPanel stack, Navigation nav, SeaCharting.View view, Runnable redraw)
	{
		List<String> oceans = SeaCharting.oceans();
		int[][] byOcean = view.byOcean();
		int[][] bySea = view.bySea();
		stack.add(new SectionHeader("Oceans", oceans.size() + " oceans · " + SeaCharting.seas().size() + " seas"));
		for (int o = 0; o < oceans.size(); o++)
		{
			String ocean = oceans.get(o);
			int done = byOcean[o][0];
			int total = byOcean[o][1];
			boolean full = total > 0 && done >= total;
			boolean open = OPEN.contains(ocean);
			Card card = new Card(new BorderLayout(0, 5));
			JPanel line = new JPanel(new BorderLayout());
			line.setOpaque(false);
			line.add(Ui.label((open ? "▾ " : "▸ ") + ocean, Theme.title(), full ? Theme.GREEN : Theme.GOLD_BRIGHT),
				BorderLayout.WEST);
			line.add(Ui.label(done + " / " + total, Theme.small(), full ? Theme.GREEN : Theme.TEXT_MUTED), BorderLayout.EAST);
			card.add(line, BorderLayout.NORTH);
			ProgressBar bar = new ProgressBar(5);
			bar.setProgress(total == 0 ? 0 : done / (double) total, full, true);
			card.add(bar, BorderLayout.CENTER);
			if (open)
			{
				JPanel seas = new JPanel(new GridLayout(0, 1, 0, 2));
				seas.setOpaque(false);
				seas.setBorder(new EmptyBorder(4, 0, 0, 0));
				for (SeaCharting.Sea sea : SeaCharting.seas())
				{
					if (!sea.getOcean().equals(ocean))
					{
						continue;
					}
					int[] n = bySea[sea.getIndex()];
					boolean seaFull = n[1] > 0 && n[0] >= n[1];
					JPanel row = new JPanel(new BorderLayout(6, 0));
					row.setOpaque(false);
					row.add(Ui.label(sea.getName(), Theme.small(), seaFull ? Theme.GREEN : n[0] > 0 ? Theme.TEXT : Theme.TEXT_MUTED),
						BorderLayout.CENTER);
					row.add(Ui.label(n[0] + "/" + n[1], Theme.small(), seaFull ? Theme.GREEN : n[0] > 0 ? Theme.GOLD : Theme.TEXT_FAINT),
						BorderLayout.EAST);
					seas.add(row);
				}
				card.add(seas, BorderLayout.SOUTH);
			}
			else
			{
				int seas = 0;
				int charted = 0;
				for (SeaCharting.Sea sea : SeaCharting.seas())
				{
					if (sea.getOcean().equals(ocean))
					{
						seas++;
						int[] n = bySea[sea.getIndex()];
						charted += n[1] > 0 && n[0] >= n[1] ? 1 : 0;
					}
				}
				card.add(Ui.label(charted + " of " + seas + (seas == 1 ? " sea" : " seas") + " fully charted", Theme.small(),
					full ? Theme.GREEN : Theme.TEXT_FAINT), BorderLayout.SOUTH);
			}
			card.setToolTipText(open ? "Click to fold the seas away" : "Click to see each sea");
			card.onClick(() ->
			{
				if (!OPEN.remove(ocean))
				{
					OPEN.add(ocean);
				}
				redraw.run();
			});
			stack.add(card);
		}
	}

	/** Opens an ocean's seas in the list, as a click would; for tests and pictures. */
	static void open(String ocean, boolean value)
	{
		if (value)
		{
			OPEN.add(ocean);
		}
		else
		{
			OPEN.remove(ocean);
		}
	}

	static String footnote(SeaCharting.View view)
	{
		String places = view.placesLearned() > 0
			? " Navigator has seen " + view.placesLearned() + (view.placesLearned() == 1 ? " chart" : " charts")
			+ " made and measures from where you made them."
			: " Distances are to each sea's middle until you chart there; then to where you charted.";
		return "Charts done come from the game. Charting every sea needs 78 Sailing, an eternal brazier for icy seas, an "
			+ "adamant helm or better for tangled kelp and an adamant keel or better for crystal-flecked waters." + places
			+ " Tasks, kinds and levels from the OSRS Wiki.";
	}

	static Color kindColour(SeaCharting.Kind kind)
	{
		switch (kind)
		{
			case GENERIC:
				return Theme.GOLD;
			case SPYGLASS:
				return Theme.GOLD_BRIGHT;
			case CRATE:
				return Theme.EMBER;
			case CURRENT:
				return Theme.TEAL;
			case DIVING:
				return Theme.mix(Theme.TEAL, Theme.GREEN, 0.5);
			default:
				return Theme.GREEN;
		}
	}

	private static void bullet(JPanel foot, String text)
	{
		JLabel r = Ui.wrapped("• " + text, Theme.small(), Theme.TEXT_MUTED, 190);
		r.setBorder(new EmptyBorder(2, 0, 0, 0));
		foot.add(r);
	}

	private static String hours(double h)
	{
		if (h < 1)
		{
			return Math.max(1, Math.round(h * 60)) + " minutes";
		}
		return String.format(Locale.ENGLISH, "%.1f hours", h);
	}

	private static JPanel column()
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		return p;
	}
}
