package com.codex.navigator.ui;

import com.codex.navigator.core.AccountSnapshot;
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
		return stack;
	}

	/** Every level, quest point and combat level the goal still asks for, unmet ones first. */
	private static void requirements(StackPanel stack, Navigation nav, Plan plan, boolean showMet, Runnable toggleMet)
	{
		AccountSnapshot account = nav.account();
		List<JPanel> unmetRows = new ArrayList<>();
		List<JPanel> metRows = new ArrayList<>();
		for (SkillGap gap : plan.getSkills())
		{
			(gap.isMet() ? metRows : unmetRows).add(skillRow(nav, gap));
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
		stack.add(new SectionHeader("Requirements", unmetRows.isEmpty() ? "all met" : unmetRows.size() + " to go"));
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
		return meterRow(nav.skillIcon(gap.getSkill()), gap.getSkill().getName(), gap.getCurrent(), gap.getTarget(),
			gap.fraction(), gap.isMet() ? null : Planner.formatXp(gap.xpToGo()) + " xp to go");
	}

	/** An icon, a name, "current / target" and a slim bar; met rows fade back and turn green. */
	private static JPanel meterRow(BufferedImage icon, String name, int current, int target, double fraction, String tip)
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
		line.add(Ui.label(current + " / " + target, Theme.small(), met ? Theme.GREEN : Theme.GOLD), BorderLayout.EAST);
		right.add(line, BorderLayout.NORTH);
		ProgressBar bar = new ProgressBar(4);
		bar.setProgress(fraction, met, false);
		right.add(bar, BorderLayout.CENTER);
		row.add(right, BorderLayout.CENTER);
		if (tip != null)
		{
			row.setToolTipText(tip);
		}
		return row;
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
