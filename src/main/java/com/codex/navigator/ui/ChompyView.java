package com.codex.navigator.ui;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.ChompyHunt;
import com.codex.navigator.core.Effort;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.Step;
import com.codex.navigator.data.QuestDef;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Locale;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Chompy bird hats laid out for one account: the kills to the next hat and the time left at this player's own kill
 * rate, every hat along one track, and the Western Provinces Diary's hat tasks. Shared by the Atlas and a hat goal's
 * page.
 */
final class ChompyView
{
	private ChompyView()
	{
	}

	/** The Atlas list: the ledger for the next hat, the diary's hat tasks and every hat. */
	static StackPanel atlas(Navigation nav)
	{
		StackPanel stack = new StackPanel(6, 8);
		AccountSnapshot account = nav.account();
		ChompyHunt.View hunt = account.chompy();
		stack.add(new SectionHeader("Chompy hats", hunt.isKnown() ? CombatView.format(hunt.getKills()) + " kills" : null));
		if (!account.isLoggedIn())
		{
			stack.add(GoalCard.hint("Log in to see your chompy kills. They are kept for each account."));
			hats(stack, nav, hunt);
			return stack;
		}
		quest(stack, nav, account);
		ChompyHunt.Hat next = ChompyHunt.nextHat(Math.max(0, hunt.getKills()));
		stack.add(ledger(nav, hunt, next == null ? ChompyHunt.HATS[ChompyHunt.HATS.length - 1] : next, true));
		diary(stack, nav, account, true);
		hats(stack, nav, hunt);
		stack.add(GoalCard.hint(footnote(hunt)));
		return stack;
	}

	/** A hat goal's own page, under its hero: the quest when it is still to do, the ledger and the diary's tasks. */
	static void goal(StackPanel stack, Navigation nav, Goal goal)
	{
		AccountSnapshot account = nav.account();
		ChompyHunt.Hat hat = ChompyHunt.hat(goal.getName());
		if (hat == null)
		{
			return;
		}
		ChompyHunt.View hunt = account.chompy();
		stack.add(ledger(nav, hunt, hat, false));
		if (account.isLoggedIn())
		{
			diary(stack, nav, account, true);
		}
		stack.add(GoalCard.hint(footnote(hunt)));
	}

	/** True when the account still has to do Big Chompy Bird Hunting before any kill counts. */
	static boolean questPending(AccountSnapshot account)
	{
		return account.knowsQuest(ChompyHunt.QUEST) && !account.questDone(ChompyHunt.QUEST) && account.chompy().getKills() <= 0;
	}

	/** Big Chompy Bird Hunting as a row, while it is not done: nothing is counted until it is. */
	private static void quest(StackPanel stack, Navigation nav, AccountSnapshot account)
	{
		if (!questPending(account))
		{
			return;
		}
		QuestDef def = nav.planner().getData().quest(ChompyHunt.QUEST);
		if (def == null)
		{
			return;
		}
		Step step = nav.planner().questStep(def, account);
		String detail = step.getStatus() == Step.Status.BLOCKED ? step.getDetail() : "Opens chompy hunting and the hats";
		stack.add(new SectionHeader("First", null));
		stack.add(Row.of(step.getStatus(), def.name, detail, null, Theme.GOLD, () -> nav.open(Goal.quest(def.name))));
	}

	/**
	 * The ledger card: kills against one hat on the track, the kills still to go and the time they take at this
	 * player's rate, then the rate itself and, in the Atlas, a pin.
	 */
	static Card ledger(Navigation nav, ChompyHunt.View hunt, ChompyHunt.Hat hat, boolean withHead)
	{
		Card card = new Card(new BorderLayout(0, 8));
		card.setBorder(new EmptyBorder(10, 10, 10, 10));
		card.highlighted(true);
		int kills = hunt.getKills();
		boolean reached = hunt.isKnown() && kills >= hat.getKills();

		JPanel head = new JPanel(new BorderLayout(8, 0));
		head.setOpaque(false);
		JPanel words = column();
		if (withHead)
		{
			boolean all = reached && ChompyHunt.nextHat(kills) == null;
			words.add(Ui.label(all ? "EVERY HAT REACHED" : "NEXT HAT", Theme.small(), all ? Theme.GREEN : Theme.GOLD));
			words.add(Ui.wrapped(hat.getName(), Theme.title(), Theme.GOLD_BRIGHT, 120));
		}
		int wrap = withHead ? 120 : 190;
		if (!hunt.isKnown())
		{
			words.add(Ui.wrapped(CombatView.format(hat.getKills()) + " kills needed", Theme.body(), Theme.TEXT, wrap));
			words.add(Ui.wrapped(questPending(nav.account()) ? "Kills count once Big Chompy Bird Hunting is done"
				: "Check kills on your ogre bow to read your count", Theme.small(), Theme.TEXT_MUTED, wrap));
		}
		else if (reached)
		{
			words.add(Ui.wrapped(CombatView.format(kills) + " of " + CombatView.format(hat.getKills()) + " kills",
				Theme.body(), Theme.GREEN, wrap));
			words.add(Ui.wrapped("Claim it from Rantz with an ogre bow on you", Theme.small(), Theme.TEXT_MUTED, wrap));
		}
		else
		{
			int left = hat.getKills() - kills;
			words.add(Ui.wrapped(CombatView.format(left) + (left == 1 ? " kill" : " kills") + " to go", Theme.body(), Theme.TEXT, wrap));
			double hours = hunt.hoursTo(hat.getKills());
			String when = hours > 0 ? "About " + Effort.format(hours).substring(1) + " at your pace"
				: "Your pace shows after a few kills";
			words.add(Ui.wrapped(when, Theme.small(), hours > 0 ? Theme.TEAL : Theme.TEXT_MUTED, wrap));
		}
		head.add(words, BorderLayout.CENTER);
		if (withHead)
		{
			ProgressRing ring = new ProgressRing(56, 5f);
			ring.setProgress(!hunt.isKnown() ? 0 : Math.min(1, kills / (double) hat.getKills()), reached, false);
			ring.setCaption(hunt.isKnown() ? CombatView.format(kills) + "/" + CombatView.format(hat.getKills())
				: "? / " + CombatView.format(hat.getKills()));
			head.add(ring, BorderLayout.EAST);
		}
		card.add(head, BorderLayout.NORTH);

		card.add(new HatTrack(kills, ChompyHunt.rank(hat), i -> nav.open(Goal.chompy(ChompyHunt.HATS[i - 1].getName()))),
			BorderLayout.CENTER);

		JPanel foot = column();
		foot.add(Ui.label("YOUR PACE", Theme.small(), Theme.GOLD_DIM));
		String pace = hunt.getPerHour() > 0
			? Math.round(hunt.getPerHour()) + " kills an hour, learned from " + hours(hunt.getHours()) + " of hunting"
			: "Not learned yet. Kills less than five minutes apart are timed; the time between hunts never counts.";
		JLabel paceLine = Ui.wrapped(pace, Theme.small(), Theme.TEXT_MUTED, 190);
		paceLine.setBorder(new EmptyBorder(2, 0, 0, 0));
		foot.add(paceLine);
		if (withHead)
		{
			Goal goal = Goal.chompy(hat.getName());
			boolean pinned = nav.goals().isPinned(goal);
			JPanel buttons = new JPanel(new BorderLayout(6, 0));
			buttons.setOpaque(false);
			buttons.setBorder(new EmptyBorder(8, 0, 0, 0));
			buttons.add(new CodexButton(pinned ? "Pinned" : "Pin " + hat.getName(), !pinned, () ->
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

	/** The Western Provinces Diary's hat task in each tier, with how the kills stand against it. A row opens the tier. */
	static void diary(StackPanel stack, Navigation nav, AccountSnapshot account, boolean withHeader)
	{
		ChompyHunt.View hunt = account.chompy();
		if (withHeader)
		{
			stack.add(new SectionHeader("Western Provinces", "diary"));
		}
		for (int i = 0; i < ChompyHunt.DIARY_TIERS.length; i++)
		{
			String name = "Western " + ChompyHunt.DIARY_TIERS[i];
			int need = ChompyHunt.DIARY_KILLS[i];
			boolean done = account.diaryDone(name);
			boolean enough = hunt.isKnown() && hunt.getKills() >= need;
			Step.Status status = done ? Step.Status.DONE : enough ? Step.Status.READY
				: hunt.isKnown() ? Step.Status.BLOCKED : Step.Status.UNKNOWN;
			String detail;
			if (done)
			{
				detail = "Tier complete";
			}
			else if (enough)
			{
				detail = CombatView.format(need) + " kills reached  ·  claim a hat";
			}
			else if (hunt.isKnown())
			{
				int left = need - hunt.getKills();
				double hours = hunt.hoursTo(need);
				detail = CombatView.format(left) + " kills to go" + (hours > 0 ? "  ·  " + Effort.format(hours) : "");
			}
			else
			{
				detail = "Needs " + CombatView.format(need) + " kills";
			}
			Card row = Row.of(status, name, detail, new Pill(CombatView.format(need), enough || done ? Theme.GREEN : Theme.GOLD),
				null, () -> nav.open(Goal.diary(name)));
			row.setToolTipText("<html>" + Ui.escape(ChompyHunt.DIARY_TASKS[i]) + "<br>Chompy or jubbly bird kills count.</html>");
			stack.add(row);
		}
	}

	/**
	 * The chompy task of a Western Provinces diary tier, for that tier's own page: the kills it needs against the
	 * account's. Nothing for any other diary.
	 */
	static void diaryTask(StackPanel stack, Navigation nav, String diary)
	{
		int tier = -1;
		for (int i = 0; i < ChompyHunt.DIARY_TIERS.length; i++)
		{
			if (("Western " + ChompyHunt.DIARY_TIERS[i]).equals(diary))
			{
				tier = i;
			}
		}
		AccountSnapshot account = nav.account();
		if (tier < 0 || !account.isLoggedIn() || account.diaryDone(diary))
		{
			return;
		}
		ChompyHunt.View hunt = account.chompy();
		int need = ChompyHunt.DIARY_KILLS[tier];
		boolean enough = hunt.isKnown() && hunt.getKills() >= need;
		stack.add(new SectionHeader("Chompy task", null));
		Card card = new Card(new BorderLayout(0, 5));
		card.setBorder(new EmptyBorder(8, 10, 8, 10));
		card.add(Ui.wrapped(ChompyHunt.DIARY_TASKS[tier], Theme.small(), Theme.TEXT, 190), BorderLayout.NORTH);
		ProgressBar bar = new ProgressBar(4);
		bar.setProgress(!hunt.isKnown() ? 0 : Math.min(1, hunt.getKills() / (double) need), enough, false);
		card.add(bar, BorderLayout.CENTER);
		String foot = !hunt.isKnown() ? "Check kills on your ogre bow to read your count"
			: enough ? CombatView.format(hunt.getKills()) + " kills: enough, claim a hat from Rantz"
			: CombatView.format(hunt.getKills()) + " of " + CombatView.format(need) + " kills"
				+ (hunt.hoursTo(need) > 0 ? "  ·  " + Effort.format(hunt.hoursTo(need)) + " at your pace" : "");
		card.add(Ui.wrapped(foot, Theme.small(), enough ? Theme.GREEN : Theme.TEXT_MUTED, 190), BorderLayout.SOUTH);
		// The tier's kills are a hat's own (Ogre bowman, Marksman, Ogre forester, Ogre expert): the card opens that hat.
		Goal hat = Goal.chompy(ChompyHunt.bestHat(need).getName());
		card.onClick(() -> nav.open(hat));
		stack.add(card);
	}

	/** Every hat with its kills: reached in green, the next in gold, the rest faint. A row opens that hat's goal. */
	static void hats(StackPanel stack, Navigation nav, ChompyHunt.View hunt)
	{
		ChompyHunt.Hat best = hunt.isKnown() ? ChompyHunt.bestHat(hunt.getKills()) : null;
		ChompyHunt.Hat next = hunt.isKnown() ? ChompyHunt.nextHat(hunt.getKills()) : null;
		stack.add(new SectionHeader("Every hat", best == null ? ChompyHunt.HATS.length + " in all"
			: ChompyHunt.rank(best) + " of " + ChompyHunt.HATS.length));
		Card card = new Card(new BorderLayout());
		card.setBorder(new EmptyBorder(6, 10, 6, 10));
		StackPanel rows = new StackPanel(3, 0);
		for (ChompyHunt.Hat hat : ChompyHunt.HATS)
		{
			boolean reached = hunt.isKnown() && hunt.getKills() >= hat.getKills();
			Goal goal = Goal.chompy(hat.getName());
			boolean pinned = nav.goals().isPinned(goal);
			Color colour = reached ? Theme.GREEN : hat == next ? Theme.GOLD_BRIGHT : Theme.TEXT_MUTED;
			JPanel row = new JPanel(new BorderLayout(6, 0));
			row.setOpaque(false);
			row.add(Ui.label(hat.getName(), Theme.small(), colour), BorderLayout.CENTER);
			row.add(Ui.label((pinned ? "pinned  ·  " : "") + CombatView.format(hat.getKills()), Theme.small(), reached ? Theme.GREEN : hat == next ? Theme.GOLD : Theme.TEXT_FAINT),
				BorderLayout.EAST);
			row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			row.setToolTipText(hat.getName() + " hat" + (pinned ? ", pinned" : "") + ". Click to open it.");
			row.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseReleased(java.awt.event.MouseEvent e)
				{
					nav.open(goal);
				}
			});
			rows.add(row);
		}
		card.add(rows, BorderLayout.CENTER);
		stack.add(card);
	}

	static String footnote(ChompyHunt.View hunt)
	{
		String count = hunt.isKnown()
			? "Kills come from your ogre bow's Check kills, plus each chompy kill since. Jubbly kills count for hats too: "
			+ "check the bow again to take them in."
			: "Check kills on your ogre bow once and the Codex keeps the count from then on"
			+ (hunt.getUnsynced() > 0 ? " (" + hunt.getUnsynced() + " kills seen so far wait for it)." : ".");
		return count + " Hats and kills from the OSRS Wiki.";
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
