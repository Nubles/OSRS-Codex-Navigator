package com.codex.navigator.ui;

import com.codex.navigator.core.Chains;
import com.codex.navigator.core.Effort;
import com.codex.navigator.core.Goal;
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

		int textWidth = 168 - size;
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
		boolean compact = done && !focus;
		if (done && !(compact && showActions))
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
			if (plan.getHoursLeft() > 0 && !plan.isAwaitingTasks())
			{
				JLabel time = Ui.label(Effort.format(plan.getHoursLeft()) + " of play left", Theme.small(), Theme.TEXT_FAINT);
				time.setToolTipText(DetailView.usesPace(plan)
					? "Training at the pace you have shown, plus a typical play-through of each quest left"
					: "Training at steady rates plus a typical play-through of each quest left");
				text.add(time);
				JLabel when = DetailView.when(nav, plan, false);
				if (when != null)
				{
					text.add(when);
				}
			}
		}
		top.add(text, BorderLayout.CENTER);
		card.add(top, BorderLayout.NORTH);

		Goal after = done ? Chains.next(nav.planner(), plan.getGoal(), nav.account(), nav.includeMiniquests()) : null;
		if (after != null && nav.goals().isPinned(after))
		{
			after = null;
		}
		if (!done || focus)
		{
			card.add(after != null ? chain(nav, after, focus) : new NextStepBox(plan.getNext(), 150), BorderLayout.CENTER);
		}
		else if (after != null && !showActions)
		{
			card.add(chain(nav, after, focus), BorderLayout.CENTER);
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
			if (compact)
			{
				// A finished goal shrinks to its ring, its name, and "Complete" beside the Unpin button.
				JPanel line = new JPanel(new BorderLayout());
				line.setOpaque(false);
				line.setAlignmentX(JPanel.LEFT_ALIGNMENT);
				line.add(Ui.label("Complete", Theme.small(), Theme.GREEN), BorderLayout.WEST);
				line.add(actions, BorderLayout.EAST);
				text.add(line);
				card.setBorder(new EmptyBorder(8, 10, 8, 10));
				if (after != null)
				{
					card.add(chain(nav, after, focus), BorderLayout.SOUTH);
				}
			}
			else
			{
				card.add(actions, BorderLayout.SOUTH);
			}
		}
		card.onClick(() -> nav.open(plan.getGoal()));
		return card;
	}

	/**
	 * The next link in a finished goal's chain, ready to pin: the next diary tier, or the quest this one
	 * leads straight into. Pressing it on the focus card makes the successor the new focus.
	 */
	static JPanel chain(Navigation nav, Goal next, boolean asFocus)
	{
		Plan plan = nav.planner().plan(next, nav.account());
		JPanel box = new JPanel(new BorderLayout(6, 0));
		box.setOpaque(false);
		box.setBorder(new EmptyBorder(6, 0, 0, 0));
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.add(Ui.label("NEXT IN LINE", Theme.small(), Theme.GOLD));
		JLabel name = Ui.label(plan.getTitle(), Theme.body(), Theme.TEXT);
		name.setToolTipText(plan.getTitle());
		name.setMinimumSize(new java.awt.Dimension(10, name.getPreferredSize().height));
		text.add(name);
		text.setMinimumSize(new java.awt.Dimension(10, 10));
		text.setPreferredSize(new java.awt.Dimension(10, text.getPreferredSize().height));
		box.add(text, BorderLayout.CENTER);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		// After the focus is reached, its successor takes over as focus; otherwise it joins the list.
		holder.add(asFocus ? new CodexButton("Focus", true, () -> nav.goals().makeFocus(next))
			: new CodexButton("Pin", true, () -> nav.goals().toggle(next)), BorderLayout.SOUTH);
		box.add(holder, BorderLayout.EAST);
		return box;
	}

	static JLabel hint(String text)
	{
		return Ui.wrapped(text, Theme.small(), Theme.TEXT_FAINT, 190);
	}
}
