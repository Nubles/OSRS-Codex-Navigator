package com.codex.navigator.ui;

import com.codex.navigator.core.Goal;
import com.codex.navigator.core.Plan;
import com.codex.navigator.core.SkillGap;
import com.codex.navigator.core.Step;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
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

	static StackPanel build(Navigation nav, Goal goal, boolean showCompleted, Runnable back, Runnable toggleCompleted)
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

		if (!plan.getSkills().isEmpty())
		{
			int unmet = 0;
			for (SkillGap gap : plan.getSkills())
			{
				unmet += gap.isMet() ? 0 : 1;
			}
			stack.add(new SectionHeader("Levels", unmet == 0 ? "all met" : unmet + " to train"));
			Card skills = new Card(new BorderLayout());
			StackPanel list = new StackPanel(5, 0);
			for (SkillGap gap : plan.getSkills())
			{
				list.add(skillRow(nav, gap));
			}
			skills.add(list, BorderLayout.CENTER);
			stack.add(skills);
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
		return stack;
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
		if (plan.getQuestsTotal() > 1)
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
		JPanel row = new JPanel(new BorderLayout(6, 2));
		row.setOpaque(false);
		BufferedImage icon = nav.skillIcon(gap.getSkill());
		JLabel iconLabel = new JLabel();
		if (icon != null)
		{
			iconLabel.setIcon(new ImageIcon(icon));
		}
		iconLabel.setPreferredSize(new java.awt.Dimension(18, 18));
		row.add(iconLabel, BorderLayout.WEST);

		JPanel right = new JPanel(new BorderLayout(0, 2));
		right.setOpaque(false);
		JPanel line = new JPanel(new BorderLayout());
		line.setOpaque(false);
		line.add(Ui.label(gap.getSkill().getName(), Theme.small(), gap.isMet() ? Theme.TEXT_MUTED : Theme.TEXT), BorderLayout.WEST);
		line.add(Ui.label(gap.getCurrent() + " / " + gap.getTarget(), Theme.small(), gap.isMet() ? Theme.GREEN : Theme.GOLD),
			BorderLayout.EAST);
		right.add(line, BorderLayout.NORTH);
		ProgressBar bar = new ProgressBar(4);
		bar.setProgress(gap.fraction(), gap.isMet(), false);
		right.add(bar, BorderLayout.CENTER);
		row.add(right, BorderLayout.CENTER);
		if (!gap.isMet())
		{
			row.setToolTipText(com.codex.navigator.core.Planner.formatXp(gap.xpToGo()) + " xp to go");
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
