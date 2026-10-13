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
		Card card = new Card(new BorderLayout(0, 8));
		card.setBorder(new EmptyBorder(10, 10, 10, 10));
		card.highlighted(focus);
		card.accent(plan.isComplete() ? Theme.GREEN : Theme.progressColor(plan.getProgress()));

		JPanel top = new JPanel(new BorderLayout(10, 0));
		top.setOpaque(false);
		ProgressRing ring = new ProgressRing(focus ? 50 : 40, focus ? 5f : 4f);
		ring.setProgress(plan.getProgress(), plan.isComplete(), false);
		JPanel ringHolder = new JPanel(new BorderLayout());
		ringHolder.setOpaque(false);
		ringHolder.add(ring, BorderLayout.NORTH);
		top.add(ringHolder, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		if (focus)
		{
			text.add(Ui.label("FOCUS", Theme.small(), Theme.GOLD));
		}
		text.add(Ui.wrapped(plan.getTitle(), Theme.title(), Theme.GOLD_BRIGHT, focus ? 124 : 136));
		String sub = plan.getSubtitle();
		if (plan.getQuestsTotal() > 1)
		{
			sub += "  ·  " + plan.getQuestsDone() + "/" + plan.getQuestsTotal() + " quests";
		}
		text.add(Ui.wrapped(sub, Theme.small(), Theme.TEXT_MUTED, focus ? 124 : 136));
		top.add(text, BorderLayout.CENTER);
		card.add(top, BorderLayout.NORTH);

		if (focus || !plan.isComplete())
		{
			card.add(new NextStepBox(plan.getNext(), 150), BorderLayout.CENTER);
		}

		if (showActions)
		{
			JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
			actions.setOpaque(false);
			if (!focus)
			{
				actions.add(new CodexButton("Focus", false, () -> nav.goals().makeFocus(plan.getGoal())));
			}
			actions.add(new CodexButton("Unpin", false, () -> nav.goals().toggle(plan.getGoal())));
			card.add(actions, BorderLayout.SOUTH);
		}
		card.onClick(() -> nav.open(plan.getGoal()));
		return card;
	}

	static JLabel hint(String text)
	{
		return Ui.wrapped(text, Theme.small(), Theme.TEXT_FAINT, 190);
	}
}
