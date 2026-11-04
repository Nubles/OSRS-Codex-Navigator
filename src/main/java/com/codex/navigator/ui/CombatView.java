package com.codex.navigator.ui;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.CombatTasks;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.Step;
import com.codex.navigator.data.CodexData;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Combat achievements laid out for one account: the tier ladder, the points to the next reward and what it brings,
 * the tasks nearest for this account and the bosses it already fights. Shared by the Atlas and a tier goal's page.
 */
final class CombatView
{
	/** Nearest tasks listed. */
	static final int NEAREST_SHOWN = 8;
	/** Bosses listed under "Your bosses". */
	static final int BOSSES_SHOWN = 6;

	private CombatView()
	{
	}

	/** The Atlas list: the ledger, the nearest tasks, the tiers and your bosses. */
	static StackPanel atlas(Navigation nav)
	{
		StackPanel stack = new StackPanel(6, 8);
		AccountSnapshot account = nav.account();
		CombatTasks tasks = account.combatTasks();
		int points = account.getCombatAchievementPoints();
		stack.add(new SectionHeader("Combat achievements", format(points) + " pts"));
		if (!account.isLoggedIn())
		{
			stack.add(GoalCard.hint("Log in to read your combat achievements. The game lists every task and which are done."));
			tiers(stack, nav, account);
			return stack;
		}
		int next = tasks.nextTier(points);
		stack.add(ledger(nav, tasks, points, next == 0 ? CombatTasks.TIERS.length : next, true, true));
		nearest(stack, tasks, 0);
		tiers(stack, nav, account);
		bosses(stack, tasks);
		stack.add(GoalCard.hint(footnote(tasks)));
		return stack;
	}

	/** A tier goal's own page, under its hero: the ledger for that tier, its reward and the nearest tasks. */
	static void goal(StackPanel stack, Navigation nav, Goal goal)
	{
		AccountSnapshot account = nav.account();
		if (!account.isLoggedIn())
		{
			return;
		}
		CombatTasks tasks = account.combatTasks();
		int tier = CombatTasks.tierOf(goal.getName());
		stack.add(ledger(nav, tasks, account.getCombatAchievementPoints(), tier, false, false));
		if (account.getCombatAchievementPoints() < tasks.threshold(tier))
		{
			nearest(stack, tasks, 0);
		}
		stack.add(GoalCard.hint(footnote(tasks)));
	}

	/**
	 * The ledger card: points against a tier's threshold on the ladder, the points still to earn and how many of the
	 * nearest tasks would earn them, then the tier's reward.
	 */
	static Card ledger(Navigation nav, CombatTasks tasks, int points, int tier, boolean withPin, boolean withHead)
	{
		Card card = new Card(new BorderLayout(0, 8));
		card.setBorder(new EmptyBorder(10, 10, 10, 10));
		card.highlighted(true);
		int need = tasks.threshold(tier);
		boolean reached = points >= need;
		String name = CombatTasks.tierName(tier);

		JPanel head = new JPanel(new BorderLayout(8, 0));
		head.setOpaque(false);
		JPanel words = column();
		if (withHead)
		{
			words.add(Ui.label(reached ? name.toUpperCase(Locale.ROOT) + " REWARD REACHED" : "NEXT REWARD", Theme.small(),
				reached ? Theme.GREEN : Theme.GOLD));
			words.add(Ui.label(name, Theme.title(), Theme.GOLD_BRIGHT));
		}
		String line = reached ? format(points) + " of " + format(need) + " points"
			: format(need - points) + " points to go";
		words.add(Ui.label(line, Theme.body(), reached ? Theme.GREEN : Theme.TEXT));
		if (!reached && tasks.isKnown())
		{
			int n = tasks.tasksToEarn(need - points);
			if (n > 0)
			{
				words.add(Ui.wrapped("About " + n + (n == 1 ? " task" : " tasks") + ", taking the nearest first",
					Theme.small(), Theme.TEXT_MUTED, withHead ? 120 : 190));
			}
		}
		head.add(words, BorderLayout.CENTER);
		if (withHead)
		{
			// On a tier's own page the hero above already carries the ring.
			ProgressRing ring = new ProgressRing(56, 5f);
			ring.setProgress(need <= 0 ? 0 : Math.min(1, points / (double) need), reached, false);
			ring.setCaption(format(points) + "/" + format(need));
			head.add(ring, BorderLayout.EAST);
		}
		card.add(head, BorderLayout.NORTH);

		card.add(new TierLadder(tasks, points, tier, t -> nav.open(Goal.combat(CombatTasks.tierName(t)))), BorderLayout.CENTER);

		JPanel foot = column();
		foot.add(Ui.label("AT " + name.toUpperCase(Locale.ROOT), Theme.small(), Theme.GOLD_DIM));
		for (String reward : CombatTasks.rewards(tier))
		{
			JLabel r = Ui.wrapped("• " + reward, Theme.small(), Theme.TEXT_MUTED, 190);
			r.setBorder(new EmptyBorder(2, 0, 0, 0));
			foot.add(r);
		}
		if (withPin)
		{
			Goal goal = Goal.combat(name);
			boolean pinned = nav.goals().isPinned(goal);
			JPanel buttons = new JPanel(new BorderLayout(6, 0));
			buttons.setOpaque(false);
			buttons.setBorder(new EmptyBorder(8, 0, 0, 0));
			buttons.add(new CodexButton(pinned ? "Pinned" : "Pin " + name, !pinned, () ->
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

	/** The nearest unfinished tasks, each with why it is near. */
	static void nearest(StackPanel stack, CombatTasks tasks, int tier)
	{
		if (!tasks.isKnown())
		{
			return;
		}
		List<CombatTasks.Near> near = tasks.nearest(tier, NEAREST_SHOWN);
		if (near.isEmpty())
		{
			return;
		}
		stack.add(new SectionHeader("Nearest tasks", "for you"));
		for (CombatTasks.Near n : near)
		{
			CombatTasks.Task t = n.getTask();
			Color accent = n.getKills() > 0 ? Theme.GOLD : null;
			String detail = n.getWhy() + "  ·  " + t.getTierName();
			Card row = Row.of(Step.Status.READY, t.getName(), detail, new Pill("+" + t.getPoints(), tierColour(t.getTier())),
				accent, null);
			row.setToolTipText("<html>" + Ui.escape(t.getName()) + (t.hasMonster() ? " (" + Ui.escape(t.getMonster()) + ")" : "")
				+ "<br>" + Ui.escape(t.getDescription()) + "<br>" + Ui.escape(t.getType()) + " · " + t.getTierName() + " · "
				+ t.getPoints() + (t.getPoints() == 1 ? " point" : " points") + "</html>");
			stack.add(row);
		}
	}

	/** Each tier: tasks done of its tasks and the points its reward needs. A tier opens its goal. */
	static void tiers(StackPanel stack, Navigation nav, AccountSnapshot account)
	{
		CombatTasks tasks = account.combatTasks();
		CodexData data = nav.planner().getData();
		int points = account.getCombatAchievementPoints();
		stack.add(new SectionHeader("Tiers", null));
		for (int tier = 1; tier <= CombatTasks.TIERS.length; tier++)
		{
			String name = CombatTasks.tierName(tier);
			int total = tasks.isKnown() ? tasks.tasksIn(tier) : data.combatAchievementTasks(name);
			int done = Math.min(total, tasks.isKnown() ? tasks.doneIn(tier) : account.combatAchievementsDone(name));
			boolean reached = account.isLoggedIn() && points >= tasks.threshold(tier);
			Goal goal = Goal.combat(name);
			Card card = new Card(new BorderLayout(0, 5));
			JPanel line = new JPanel(new BorderLayout());
			line.setOpaque(false);
			JLabel title = Ui.label(name, Theme.title(), reached ? Theme.GREEN : Theme.GOLD_BRIGHT);
			line.add(title, BorderLayout.WEST);
			line.add(Ui.label(done + " / " + total + " tasks", Theme.small(), Theme.TEXT_MUTED), BorderLayout.EAST);
			card.add(line, BorderLayout.NORTH);
			ProgressBar bar = new ProgressBar(5);
			bar.setProgress(total == 0 ? 0 : done / (double) total, total > 0 && done >= total, true);
			card.add(bar, BorderLayout.CENTER);
			String foot = (reached ? "Reward reached" : "Reward at " + format(tasks.threshold(tier)) + " points")
				+ (nav.goals().isPinned(goal) ? "  ·  pinned" : "");
			card.add(Ui.label(foot, Theme.small(), reached ? Theme.GREEN : nav.goals().isPinned(goal) ? Theme.GOLD : Theme.TEXT_FAINT),
				BorderLayout.SOUTH);
			card.onClick(() -> nav.open(goal));
			stack.add(card);
		}
	}

	/** The bosses the account fights, by kill count, with how many of their tasks are done. */
	static void bosses(StackPanel stack, CombatTasks tasks)
	{
		if (!tasks.isKnown())
		{
			return;
		}
		List<Map.Entry<String, int[]>> fought = new ArrayList<>();
		for (Map.Entry<String, int[]> e : tasks.byMonster().entrySet())
		{
			if (!"Other".equals(e.getKey()) && (tasks.kills(e.getKey()) > 0 || e.getValue()[0] > 0))
			{
				fought.add(e);
			}
		}
		if (fought.isEmpty())
		{
			return;
		}
		// Most kills first, then most tasks done.
		fought.sort((a, b) ->
		{
			int k = Integer.compare(tasks.kills(b.getKey()), tasks.kills(a.getKey()));
			return k != 0 ? k : Integer.compare(b.getValue()[0], a.getValue()[0]);
		});
		stack.add(new SectionHeader("Your bosses", fought.size() > BOSSES_SHOWN ? "+" + (fought.size() - BOSSES_SHOWN) + " more" : null));
		Card card = new Card(new BorderLayout());
		card.setBorder(new EmptyBorder(8, 10, 8, 10));
		StackPanel rows = new StackPanel(6, 0);
		for (int i = 0; i < Math.min(BOSSES_SHOWN, fought.size()); i++)
		{
			Map.Entry<String, int[]> e = fought.get(i);
			int done = e.getValue()[0];
			int total = e.getValue()[1];
			int kc = tasks.kills(e.getKey());
			JPanel row = new JPanel(new BorderLayout(0, 2));
			row.setOpaque(false);
			JPanel top = new JPanel(new BorderLayout(6, 0));
			top.setOpaque(false);
			top.add(Ui.label(e.getKey(), Theme.small(), Theme.TEXT), BorderLayout.CENTER);
			top.add(Ui.label((kc > 0 ? "KC " + format(kc) + "  ·  " : "") + done + "/" + total, Theme.small(),
				done >= total ? Theme.GREEN : Theme.GOLD), BorderLayout.EAST);
			row.add(top, BorderLayout.NORTH);
			ProgressBar bar = new ProgressBar(3);
			bar.setProgress(total == 0 ? 0 : done / (double) total, done >= total, false);
			row.add(bar, BorderLayout.CENTER);
			rows.add(row);
		}
		card.add(rows, BorderLayout.CENTER);
		stack.add(card);
	}

	static String footnote(CombatTasks tasks)
	{
		if (!tasks.isKnown())
		{
			return "Task counts come from the game. The in-game Combat Achievements window has each task's details.";
		}
		String kills = tasks.getKillSource() == null
			? " No kill counts saved yet: RuneLite keeps one as you kill each boss, and Codex Boss Pilot keeps its own."
			: " Kill counts from " + tasks.getKillSource() + ".";
		return "Tasks, points and tier thresholds come from the game. Nearest means bosses you already fight, then the "
			+ "easier tier." + kills;
	}

	static Color tierColour(int tier)
	{
		switch (tier)
		{
			case 1:
				return Theme.GREEN;
			case 2:
				return Theme.TEAL;
			case 3:
				return Theme.GOLD;
			case 4:
				return Theme.EMBER;
			default:
				return Theme.GOLD_BRIGHT;
		}
	}

	static String format(int n)
	{
		return String.format(Locale.ENGLISH, "%,d", n);
	}

	private static JPanel column()
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		return p;
	}
}
