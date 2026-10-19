package com.codex.navigator.ui;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Boosts;
import com.codex.navigator.core.Effort;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.Plan;
import com.codex.navigator.core.Planner;
import com.codex.navigator.core.SkillGap;
import com.codex.navigator.core.Step;
import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.QuestDef;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** One goal laid open: progress, the next step, every level it needs and the full path. */
final class DetailView
{
	private DetailView()
	{
	}

	/** Met levels beyond this many fold away behind a toggle, so the levels still to train lead. */
	private static final int MET_SHOWN = 3;

	static StackPanel build(Navigation nav, Goal goal, boolean showCompleted, boolean showMet, Runnable back,
		Runnable toggleCompleted, Runnable toggleMet)
	{
		Plan plan = nav.planner().plan(goal, nav.account());
		StackPanel stack = new StackPanel(6, 8);

		JLabel backLink = Ui.label("<  Back", Theme.small(), Theme.GOLD);
		backLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		backLink.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent e)
			{
				back.run();
			}
		});
		JPanel backRow = new JPanel(new BorderLayout());
		backRow.setOpaque(false);
		backRow.add(backLink, BorderLayout.WEST);
		stack.add(backRow);

		stack.add(hero(nav, plan));

		requirements(stack, nav, plan, showMet, toggleMet);
		if (goal.getType() == Goal.Type.DIARY && !plan.isComplete())
		{
			stack.add(GoalCard.hint("The Codex checks a diary's levels and quests. Its tasks are listed in the "
				+ "in-game Achievement Diary journal, and the tier ticks off here once the game marks it complete."));
		}

		List<Step> path = plan.getPath();
		if (!path.isEmpty() && !(path.size() == 1 && goal.getType() == Goal.Type.QUEST))
		{
			List<Step> shown = new ArrayList<>();
			int done = 0;
			for (Step step : path)
			{
				if (step.getStatus() == Step.Status.DONE)
				{
					done++;
					if (!showCompleted)
					{
						continue;
					}
				}
				shown.add(step);
			}
			stack.add(new SectionHeader("Path", plan.getQuestsDone() + "/" + plan.getQuestsTotal()));
			Card pathCard = new Card(new BorderLayout());
			pathCard.setBorder(new EmptyBorder(6, 8, 6, 8));
			StackPanel rows = new StackPanel(0, 0);
			if (done > 0)
			{
				JLabel toggle = Ui.label((showCompleted ? "Hide " : "Show ") + done + " completed", Theme.small(), Theme.GOLD);
				toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				toggle.setBorder(new EmptyBorder(2, 2, 6, 0));
				toggle.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseReleased(MouseEvent e)
					{
						toggleCompleted.run();
					}
				});
				rows.add(toggle);
			}
			for (int i = 0; i < shown.size(); i++)
			{
				Step step = shown.get(i);
				Runnable open = step.getQuest() != null && !step.getQuest().equals(goal.getName())
					? () -> nav.open(Goal.quest(step.getQuest())) : null;
				rows.add(new TimelineRow(step, i == 0, i == shown.size() - 1, open));
			}
			if (shown.isEmpty())
			{
				rows.add(Ui.label("Every quest on this path is done.", Theme.small(), Theme.GREEN));
			}
			pathCard.add(rows, BorderLayout.CENTER);
			stack.add(pathCard);
		}

		if (goal.getType() == Goal.Type.QUEST)
		{
			leadsTo(stack, nav, goal.getName());
		}
		if (goal.getType() == Goal.Type.SKILL && goal.getSkill() != null)
		{
			opens(stack, nav, goal);
		}
		return stack;
	}

	/** Every level, quest point and combat level the goal still asks for, unmet ones first. */
	private static void requirements(StackPanel stack, Navigation nav, Plan plan, boolean showMet, Runnable toggleMet)
	{
		AccountSnapshot account = nav.account();
		List<JPanel> unmetRows = new ArrayList<>();
		List<JPanel> metRows = new ArrayList<>();
		int boostOnly = 0;
		for (SkillGap gap : plan.getSkills())
		{
			(gap.isMet() ? metRows : unmetRows).add(skillRow(nav, gap));
			boostOnly += gap.isBoostable() ? 1 : 0;
		}
		if (plan.getQuestPointTarget() > 0)
		{
			int have = account.getQuestPoints();
			int need = plan.getQuestPointTarget();
			(have >= need ? metRows : unmetRows).add(meterRow(null, "Quest points", have, need,
				Math.min(1, have / (double) need), have >= need ? null : (need - have) + " more quest points"));
		}
		if (plan.getCombatTarget() > 0)
		{
			int have = account.getCombatLevel();
			int need = plan.getCombatTarget();
			(have >= need ? metRows : unmetRows).add(meterRow(null, "Combat level", have, need,
				Math.min(1, have / (double) need), null));
		}
		if (unmetRows.isEmpty() && metRows.isEmpty())
		{
			return;
		}
		int toTrain = unmetRows.size() - boostOnly;
		stack.add(new SectionHeader("Requirements", unmetRows.isEmpty() ? "all met"
			: toTrain == 0 ? boostOnly + " to boost" : unmetRows.size() + " to go"));
		Card card = new Card(new BorderLayout());
		StackPanel list = new StackPanel(5, 0);
		for (JPanel row : unmetRows)
		{
			list.add(row);
		}
		boolean fold = metRows.size() > MET_SHOWN && !unmetRows.isEmpty();
		if (fold && !showMet)
		{
			list.add(toggle("Show " + metRows.size() + " met", toggleMet));
		}
		else
		{
			for (JPanel row : metRows)
			{
				list.add(row);
			}
			if (fold)
			{
				list.add(toggle("Hide " + metRows.size() + " met", toggleMet));
			}
		}
		card.add(list, BorderLayout.CENTER);
		stack.add(card);
	}

	/** The quests and diary tiers that list this quest as a requirement: what finishing it opens up. */
	private static void leadsTo(StackPanel stack, Navigation nav, String quest)
	{
		CodexData data = nav.planner().getData();
		AccountSnapshot account = nav.account();
		List<String> quests = data.questsNeeding(quest);
		List<String> diaries = data.diariesNeeding(quest);
		List<JComponent> rows = new ArrayList<>();
		for (String name : quests)
		{
			QuestDef def = data.quest(name);
			if (def == null || (def.miniquest && !nav.includeMiniquests()))
			{
				continue;
			}
			Step step = nav.planner().questStep(def, account);
			String detail = step.getStatus() == Step.Status.BLOCKED ? step.getDetail() : CompassView.subtitle(def);
			rows.add(Row.of(step.getStatus(), name, detail, null, null, () -> nav.open(Goal.quest(name))));
		}
		for (String name : diaries)
		{
			Plan plan = nav.planner().plan(Goal.diary(name), account);
			Step.Status status = plan.isComplete() ? Step.Status.DONE
				: plan.isAwaitingTasks() ? Step.Status.READY : Step.Status.BLOCKED;
			String detail = plan.isComplete() ? "Diary complete" : plan.isAwaitingTasks() ? "Requirements met"
				: "Diary  ·  " + Math.round(plan.getProgress() * 100) + "% of requirements";
			rows.add(Row.of(status, plan.getTitle(), detail, null, null, () -> nav.open(Goal.diary(name))));
		}
		// With miniquests hidden, a quest that only leads to miniquests gets no section rather than an empty one.
		if (rows.isEmpty())
		{
			return;
		}
		int total = data.unlockCount(quest);
		stack.add(new SectionHeader("Leads to", total > rows.size() ? total + " in all" : null));
		for (JComponent row : rows)
		{
			stack.add(row);
		}
	}

	/** Rows listed under Opens before the rest fold into the header's count. */
	private static final int OPENS_SHOWN = 8;

	/**
	 * The quests and diary tiers a level goal opens: those whose requirement in the skill lies between the level
	 * now and the goal, each with the level it asks for and whether anything else still holds it back.
	 */
	private static void opens(StackPanel stack, Navigation nav, Goal goal)
	{
		AccountSnapshot account = nav.account();
		if (!account.isLoggedIn())
		{
			return;
		}
		Planner planner = nav.planner();
		net.runelite.api.Skill skill = goal.getSkill();
		// Quests and tiers together, in the order their levels come.
		List<Object[]> rows = new ArrayList<>();
		for (QuestDef def : planner.opens(skill, goal.getLevel(), account, nav.includeMiniquests()))
		{
			int need = Planner.need(def.skills, skill);
			List<String> blockers = planner.blockers(def, account);
			// The skill itself is one blocker; whatever else is left is what the level alone will not fix.
			blockers.remove(skill.getName() + " " + need);
			Step.Status status = blockers.isEmpty() ? Step.Status.READY : Step.Status.BLOCKED;
			String detail = skill.getName() + " " + need + (blockers.isEmpty() ? "  ·  then ready"
				: "  ·  also " + blockers.get(0) + (blockers.size() > 1 ? " +" + (blockers.size() - 1) : ""));
			rows.add(new Object[]{need, Row.of(status, def.name, detail, null, null, () -> nav.open(Goal.quest(def.name)))});
		}
		for (com.codex.navigator.data.DiaryDef def : planner.diariesOpened(skill, goal.getLevel(), account))
		{
			int need = Planner.need(def.skills, skill);
			rows.add(new Object[]{need, Row.of(Step.Status.BLOCKED, def.name + " Diary", skill.getName() + " " + need
				+ "  ·  diary tier", null, null, () -> nav.open(Goal.diary(def.name)))});
		}
		if (rows.isEmpty())
		{
			return;
		}
		rows.sort(java.util.Comparator.comparingInt(r -> (Integer) r[0]));
		stack.add(new SectionHeader("Opens", String.valueOf(rows.size())));
		int shown = Math.min(OPENS_SHOWN, rows.size());
		for (int i = 0; i < shown; i++)
		{
			stack.add((JComponent) rows.get(i)[1]);
		}
		if (rows.size() > shown)
		{
			JLabel tail = Ui.label("+ " + (rows.size() - shown) + " more up to " + skill.getName() + " " + goal.getLevel(),
				Theme.small(), Theme.TEXT_FAINT);
			tail.setBorder(new EmptyBorder(0, 4, 0, 0));
			stack.add(tail);
		}
	}

	private static JLabel toggle(String text, Runnable action)
	{
		JLabel toggle = Ui.label(text, Theme.small(), Theme.GOLD);
		toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		toggle.setBorder(new EmptyBorder(2, 24, 0, 0));
		toggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (toggle.contains(e.getPoint()))
				{
					action.run();
				}
			}
		});
		return toggle;
	}

	private static Card hero(Navigation nav, Plan plan)
	{
		Card card = new Card(new BorderLayout(0, 10));
		card.setBorder(new EmptyBorder(12, 10, 10, 10));
		card.highlighted(true);

		JPanel titles = new JPanel();
		titles.setOpaque(false);
		titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
		titles.add(Ui.wrapped(plan.getTitle(), Theme.title(), Theme.GOLD_BRIGHT, 168));
		titles.add(Ui.wrapped(plan.getSubtitle(), Theme.small(), Theme.TEXT_MUTED, 168));
		if (plan.getHoursLeft() > 0 && !plan.isAwaitingTasks())
		{
			boolean ownPace = usesPace(plan);
			JLabel time = Ui.label("About " + Effort.format(plan.getHoursLeft()).substring(1) + " of play left"
				+ (ownPace ? " at your pace" : ""), Theme.small(), Theme.TEXT_FAINT);
			time.setToolTipText(ownPace ? "Training at the pace you have shown, plus a typical play-through of each quest left"
				: "Training at steady rates plus a typical play-through of each quest left");
			titles.add(time);
			JLabel when = when(nav, plan, true);
			if (when != null)
			{
				titles.add(when);
			}
		}
		card.add(titles, BorderLayout.NORTH);

		JPanel middle = new JPanel(new BorderLayout(10, 0));
		middle.setOpaque(false);
		ProgressRing ring = new ProgressRing(74, 7f);
		ring.setProgress(plan.getProgress(), plan.isComplete(), true);
		if (plan.isAwaitingTasks())
		{
			ring.setCenterText("Tasks left");
		}
		else if (plan.getQuestsTotal() > 1)
		{
			ring.setCaption(plan.getQuestsDone() + "/" + plan.getQuestsTotal());
		}
		JPanel ringHolder = new JPanel(new BorderLayout());
		ringHolder.setOpaque(false);
		ringHolder.add(ring, BorderLayout.NORTH);
		middle.add(ringHolder, BorderLayout.WEST);

		JPanel actions = new JPanel();
		actions.setOpaque(false);
		actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
		boolean pinned = nav.goals().isPinned(plan.getGoal());
		boolean focus = plan.getGoal().equals(nav.goals().focus());
		actions.add(Ui.label(focus ? "Your focus" : pinned ? "Pinned" : "Not pinned", Theme.small(),
			focus ? Theme.GOLD : Theme.TEXT_MUTED));
		actions.add(spacer(6));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
		buttons.setOpaque(false);
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
		CodexButton pin = new CodexButton(pinned ? "Unpin" : "Pin goal", !pinned, () -> nav.goals().toggle(plan.getGoal()));
		pin.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		buttons.add(pin);
		if (pinned && !focus)
		{
			buttons.add(spacer(4));
			CodexButton makeFocus = new CodexButton("Make focus", true, () -> nav.goals().makeFocus(plan.getGoal()));
			makeFocus.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			buttons.add(makeFocus);
		}
		buttons.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		actions.add(buttons);
		middle.add(actions, BorderLayout.CENTER);
		card.add(middle, BorderLayout.CENTER);

		card.add(new NextStepBox(plan.getNext(), 152), BorderLayout.SOUTH);
		return card;
	}

	private static JPanel skillRow(Navigation nav, SkillGap gap)
	{
		String tip = gap.isMet() ? null : Planner.formatXp(gap.xpToGo()) + " xp to go, about "
			+ Effort.format(gap.hours()).substring(1) + (gap.getPace() != 1 ? " at your pace" : "");
		if (gap.isBoostable())
		{
			// No training needed: the level is met on the day with a pie or a potion.
			Boosts.Boost boost = Boosts.of(gap.getSkill());
			String note = "Boost with " + boost.getItem() + " (+" + boost.amount(gap.getCurrent()) + ")";
			return meterRow(nav.skillIcon(gap.getSkill()), gap.getSkill().getName(), gap.getCurrent(), gap.getTarget(),
				gap.fraction(), note + " when the quest asks for it, or train: " + tip, note);
		}
		return meterRow(nav.skillIcon(gap.getSkill()), gap.getSkill().getName(), gap.getCurrent(), gap.getTarget(),
			gap.fraction(), tip, null);
	}

	private static JPanel meterRow(BufferedImage icon, String name, int current, int target, double fraction, String tip)
	{
		return meterRow(icon, name, current, target, fraction, tip, null);
	}

	/**
	 * An icon, a name, "current / target" and a slim bar; met rows fade back and turn green. A boost note, when
	 * given, sits under the bar in teal and the figures turn teal too: the level is as good as met.
	 */
	private static JPanel meterRow(BufferedImage icon, String name, int current, int target, double fraction, String tip,
		String boostNote)
	{
		boolean met = current >= target;
		JPanel row = new JPanel(new BorderLayout(6, 2));
		row.setOpaque(false);
		JLabel iconLabel = new JLabel();
		if (icon != null)
		{
			iconLabel.setIcon(new ImageIcon(icon));
		}
		iconLabel.setHorizontalAlignment(JLabel.CENTER);
		iconLabel.setPreferredSize(new java.awt.Dimension(18, 18));
		row.add(iconLabel, BorderLayout.WEST);

		JPanel right = new JPanel(new BorderLayout(0, 2));
		right.setOpaque(false);
		JPanel line = new JPanel(new BorderLayout());
		line.setOpaque(false);
		line.add(Ui.label(name, Theme.small(), met ? Theme.TEXT_MUTED : Theme.TEXT), BorderLayout.WEST);
		line.add(Ui.label(current + " / " + target, Theme.small(), met ? Theme.GREEN : boostNote != null ? Theme.TEAL : Theme.GOLD),
			BorderLayout.EAST);
		right.add(line, BorderLayout.NORTH);
		ProgressBar bar = new ProgressBar(4);
		bar.setProgress(fraction, met, false);
		right.add(bar, BorderLayout.CENTER);
		if (boostNote != null)
		{
			right.add(Ui.label(boostNote, Theme.small(), Theme.TEAL), BorderLayout.SOUTH);
		}
		row.add(right, BorderLayout.CENTER);
		if (tip != null)
		{
			row.setToolTipText(tip);
		}
		return row;
	}

	/**
	 * "Around 3 Nov at your 40 min a day" (or just "Around 3 Nov" when short), from how much the account has played
	 * lately by Codex Session Chronicle; null without Chronicle or with nothing left to play.
	 */
	static JLabel when(Navigation nav, Plan plan, boolean full)
	{
		com.codex.navigator.core.PlayTime time = nav.playTime();
		if (time == null || plan.isComplete() || plan.getHoursLeft() <= 0 || plan.isAwaitingTasks())
		{
			return null;
		}
		long now = System.currentTimeMillis();
		java.time.ZoneId zone = java.time.ZoneId.systemDefault();
		String date = time.dateText(plan.getHoursLeft(), now, zone);
		String text = full ? time.describe(plan.getHoursLeft(), now, zone) : date == null ? "Over a year off" : "Around " + date;
		JLabel label = Ui.label(text, Theme.small(), Theme.TEAL);
		label.setToolTipText("If your play goes to this goal, at the " + time.rateText() + " you have played lately ("
			+ time.getSessions() + " sessions in Codex Session Chronicle)");
		return label;
	}

	/** True when a level still to train is timed at the player's learned pace rather than the steady rates. */
	static boolean usesPace(Plan plan)
	{
		for (SkillGap gap : plan.getSkills())
		{
			if (!gap.isMet() && !gap.isBoostable() && gap.getPace() != 1)
			{
				return true;
			}
		}
		return false;
	}

	private static JPanel spacer(int height)
	{
		JPanel spacer = new JPanel();
		spacer.setOpaque(false);
		spacer.setPreferredSize(new java.awt.Dimension(1, height));
		spacer.setMaximumSize(new java.awt.Dimension(1, height));
		spacer.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		return spacer;
	}
}
