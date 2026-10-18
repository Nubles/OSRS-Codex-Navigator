package com.codex.navigator.ui;

import com.codex.navigator.core.Plan;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** A pinned goal: ring, title and the next step, in a large or compact size. */
final class GoalCard
{
	private GoalCard()
	{
	}

	static Card build(Navigation nav, Plan plan, boolean focus, boolean showActions)
	{
		boolean done = plan.isComplete();
		Card card = new Card(new BorderLayout(0, 8));
		card.setBorder(new EmptyBorder(10, 10, 10, 10));
		card.highlighted(focus && !done);
		card.accent(done ? Theme.GREEN : plan.isAwaitingTasks() ? Theme.TEAL : Theme.progressColor(plan.getProgress()));

		JPanel top = new JPanel(new BorderLayout(10, 0));
		top.setOpaque(false);
		int size = done && !focus ? 30 : focus ? 50 : 40;
		ProgressRing ring = new ProgressRing(size, focus ? 5f : 4f);
		ring.setProgress(plan.getProgress(), done, false);
		if (plan.isAwaitingTasks())
		{
			ring.setCenterText("Tasks");
		}
		JPanel ringHolder = new JPanel(new BorderLayout());
		ringHolder.setOpaque(false);
		ringHolder.add(ring, BorderLayout.NORTH);
		top.add(ringHolder, BorderLayout.WEST);

		int textWidth = 168 - size - (done && !focus && showActions ? 56 : 0);
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		if (focus)
		{
			text.add(Ui.label(done ? "FOCUS  ·  COMPLETE" : "FOCUS", Theme.small(), done ? Theme.GREEN : Theme.GOLD));
		}
		if (done && !focus)
		{
			JLabel title = Ui.label(plan.getTitle(), Theme.title(), Theme.TEXT_MUTED);
			title.setToolTipText(plan.getTitle());
			title.setMinimumSize(new java.awt.Dimension(10, title.getPreferredSize().height));
			text.add(title);
		}
		else
		{
			text.add(Ui.wrapped(plan.getTitle(), Theme.title(), Theme.GOLD_BRIGHT, textWidth));
		}
		if (done)
		{
			text.add(Ui.label("Complete", Theme.small(), Theme.GREEN));
		}
		else
		{
			text.add(Ui.wrapped(plan.getSubtitle(), Theme.small(), Theme.TEXT_MUTED, textWidth));
			if (plan.getQuestsTotal() > 1)
			{
				text.add(Ui.label(plan.getQuestsDone() + " of " + plan.getQuestsTotal() + " quests done", Theme.small(),
					Theme.TEXT_FAINT));
			}
		}
		top.add(text, BorderLayout.CENTER);
		card.add(top, BorderLayout.NORTH);

		if (!done || focus)
		{
			card.add(new NextStepBox(plan.getNext(), 150), BorderLayout.CENTER);
		}

		if (showActions)
		{
			JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
			actions.setOpaque(false);
			if (!focus && !done)
			{
				actions.add(new CodexButton("Make focus", false, () -> nav.goals().makeFocus(plan.getGoal())));
			}
			actions.add(new CodexButton("Unpin", false, () -> nav.goals().toggle(plan.getGoal())));
			if (done && !focus)
			{
				// A finished goal shrinks to one line: ring, name and its Unpin button side by side.
				JPanel holder = new JPanel(new BorderLayout());
				holder.setOpaque(false);
				holder.add(actions, BorderLayout.NORTH);
				top.add(holder, BorderLayout.EAST);
			}
			else
			{
				card.add(actions, BorderLayout.SOUTH);
			}
		}
		card.onClick(() -> nav.open(plan.getGoal()));
		return card;
	}

	static JLabel hint(String text)
	{
		return Ui.wrapped(text, Theme.small(), Theme.TEXT_FAINT, 190);
	}
}
