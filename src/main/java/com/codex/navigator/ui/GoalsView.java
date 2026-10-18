package com.codex.navigator.ui;

import com.codex.navigator.core.Goal;
import com.codex.navigator.core.Plan;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Every pinned goal, focus first. */
final class GoalsView
{
	private GoalsView()
	{
	}

	static StackPanel build(Navigation nav)
	{
		StackPanel stack = new StackPanel(6, 8);
		List<Goal> goals = nav.goals().goals();
		if (goals.isEmpty())
		{
			Card card = new Card(new BorderLayout(0, 8));
			card.setBorder(new EmptyBorder(12, 10, 12, 10));
			JPanel text = new JPanel();
			text.setOpaque(false);
			text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
			text.add(Ui.label("NO GOALS PINNED", Theme.small(), Theme.GOLD));
			text.add(Ui.wrapped("Open anything in the Atlas and press Pin goal. The first goal you pin becomes your focus.",
				Theme.body(), Theme.TEXT, 168));
			card.add(text, BorderLayout.CENTER);
			JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			actions.setOpaque(false);
			actions.add(new CodexButton("Browse the Atlas", true, () -> nav.showTab(2)));
			card.add(actions, BorderLayout.SOUTH);
			stack.add(card);
			return stack;
		}

		Goal focus = nav.goals().focus();
		List<Plan> open = new ArrayList<>();
		List<Plan> finished = new ArrayList<>();
		for (Goal goal : goals)
		{
			Plan plan = nav.planner().plan(goal, nav.account());
			(plan.isComplete() && !goal.equals(focus) ? finished : open).add(plan);
		}
		if (!open.isEmpty())
		{
			stack.add(new SectionHeader("Pinned", String.valueOf(open.size())));
			for (Plan plan : open)
			{
				stack.add(GoalCard.build(nav, plan, plan.getGoal().equals(focus), true));
			}
		}
		if (!finished.isEmpty())
		{
			stack.add(new SectionHeader("Completed", String.valueOf(finished.size())));
			for (Plan plan : finished)
			{
				stack.add(GoalCard.build(nav, plan, false, true));
			}
		}
		stack.add(GoalCard.hint("Your focus also shows in game, and the chatbox tells you when a goal is done. Both can be turned off in the plugin settings."));
		return stack;
	}
}
