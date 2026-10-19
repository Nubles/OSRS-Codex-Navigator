package com.codex.navigator.ui;

import com.codex.navigator.core.Goal;
import com.codex.navigator.core.Effort;
import com.codex.navigator.core.Plan;
import com.codex.navigator.core.Route;
import com.codex.navigator.core.SessionPlan;
import com.codex.navigator.core.Step;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Every pinned goal, focus first. */
final class GoalsView
{
	/** Route steps listed before the rest fold into a count. */
	private static final int ROUTE_SHOWN = 5;
	/** The session lengths offered, in hours, and their labels. */
	static final double[] SESSION_HOURS = {1, 2, 3, 4};
	private static final String[] SESSION_LABELS = {"1 h", "2 h", "3 h", "4 h"};
	/** Steps listed in the session plan before the rest fold into a count. */
	private static final int SESSION_SHOWN = 6;

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
			text.add(Ui.wrapped("Open a quest or diary in the Atlas and press Pin goal" + (nav.account().isLoggedIn()
				? ", or pin a level below" : "") + ". The first goal you pin becomes your focus.", Theme.body(), Theme.TEXT, 168));
			card.add(text, BorderLayout.CENTER);
			JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			actions.setOpaque(false);
			actions.add(new CodexButton("Browse the Atlas", true, () -> nav.showTab(2)));
			card.add(actions, BorderLayout.SOUTH);
			stack.add(card);
			Card levels = LevelPicker.build(nav);
			if (levels != null)
			{
				stack.add(levels);
			}
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
		if (nav.account().isLoggedIn() && !open.isEmpty())
		{
			int choice = Math.max(0, Math.min(SESSION_HOURS.length - 1, nav.sessionChoice()));
			SessionPlan session = SessionPlan.build(nav.planner(), goals, nav.account(), SESSION_HOURS[choice]);
			if (!session.getItems().isEmpty())
			{
				stack.add(new SectionHeader("Next session"));
				stack.add(session(nav, session, choice));
			}
		}
		if (!open.isEmpty())
		{
			stack.add(new SectionHeader("Pinned", String.valueOf(open.size())));
			for (Plan plan : open)
			{
				stack.add(GoalCard.build(nav, plan, plan.getGoal().equals(focus), true));
			}
		}
		// The whole route comes after the goals: the session above already leads with its first steps.
		Route route = Route.build(nav.planner(), goals, nav.account());
		if (route.getGoals() >= 2 && !route.getLegs().isEmpty())
		{
			stack.add(new SectionHeader("Your route", route.getGoals() + " goals"));
			stack.add(route(nav, route));
		}
		if (!finished.isEmpty())
		{
			stack.add(new SectionHeader("Completed", String.valueOf(finished.size())));
			for (Plan plan : finished)
			{
				stack.add(GoalCard.build(nav, plan, false, true));
			}
		}
		Card levels = LevelPicker.build(nav);
		if (levels != null)
		{
			stack.add(levels);
		}
		stack.add(GoalCard.hint("Your focus also shows in game, and the chatbox tells you when a goal is done. Both can be turned off in the plugin settings."));
		return stack;
	}

	/**
	 * Every pinned goal's path laid over the others: the steps you can take now, those shared by the most
	 * goals first, each saying which goals it moves and about how long it takes.
	 */
	private static Card route(Navigation nav, Route route)
	{
		Card card = new Card(new BorderLayout());
		card.setBorder(new EmptyBorder(6, 8, 6, 8));
		StackPanel rows = new StackPanel(0, 0);
		List<Route.Leg> legs = route.getLegs();
		int shown = Math.min(ROUTE_SHOWN, legs.size());
		int more = legs.size() - shown + route.getBlocked();
		for (int i = 0; i < shown; i++)
		{
			Route.Leg leg = legs.get(i);
			Step step = leg.getStep();
			String title = NextStepBox.verb(step) + step.getTitle();
			String detail = serves(nav, leg.getServes()) + (leg.getHours() > 0 ? "  ·  " + Effort.format(leg.getHours()) : "");
			Step row = new Step(step.getKind(), step.getStatus(), title, detail, step.getQuest(), step.getSkill());
			Goal target = step.getQuest() != null ? Goal.quest(step.getQuest()) : leg.getServes().get(0);
			rows.add(new TimelineRow(row, i == 0, i == shown - 1 && more == 0, () -> nav.open(target)));
		}
		if (more > 0)
		{
			JLabel tail = Ui.label("+ " + more + " more step" + (more == 1 ? "" : "s") + " on the route", Theme.small(),
				Theme.TEXT_FAINT);
			tail.setBorder(new EmptyBorder(0, 22, 2, 0));
			rows.add(tail);
		}
		card.add(rows, BorderLayout.CENTER);
		return card;
	}

	/**
	 * What to do with the next hour or few: a length picker, the steps that fill it, and how far each goal moves.
	 */
	private static Card session(Navigation nav, SessionPlan session, int choice)
	{
		Card card = new Card(new BorderLayout(0, 6));
		card.setBorder(new EmptyBorder(8, 8, 8, 8));
		Segmented lengths = new Segmented(false, SESSION_LABELS);
		lengths.setSelected(choice, false);
		lengths.onChange(nav::chooseSession);
		card.add(lengths, BorderLayout.NORTH);

		StackPanel rows = new StackPanel(0, 0);
		List<SessionPlan.Item> items = session.getItems();
		int shown = Math.min(SESSION_SHOWN, items.size());
		for (int i = 0; i < shown; i++)
		{
			SessionPlan.Item item = items.get(i);
			Step step = item.getStep();
			String title = NextStepBox.verb(step) + step.getTitle();
			String detail = Effort.format(item.getHours()) + "  ·  " + serves(nav, item.getServes());
			Step row = new Step(step.getKind(), step.getStatus(), title, detail, step.getQuest(), step.getSkill());
			Goal target = step.getQuest() != null ? Goal.quest(step.getQuest()) : item.getServes().get(0);
			rows.add(new TimelineRow(row, i == 0, i == shown - 1 && shown == items.size(), () -> nav.open(target)));
		}
		if (items.size() > shown)
		{
			JLabel tail = Ui.label("+ " + (items.size() - shown) + " more", Theme.small(), Theme.TEXT_FAINT);
			tail.setBorder(new EmptyBorder(0, 22, 2, 0));
			rows.add(tail);
		}
		card.add(rows, BorderLayout.CENTER);

		StackPanel gains = new StackPanel(5, 0);
		gains.setBorder(new EmptyBorder(2, 0, 0, 0));
		for (SessionPlan.Gain gain : session.getGains())
		{
			gains.add(gainRow(gain));
		}
		double spare = SESSION_HOURS[choice] - session.getUsed();
		if (spare >= 0.25)
		{
			gains.add(Ui.label(Effort.format(spare) + " spare: nothing else on your goals fits", Theme.small(), Theme.TEXT_FAINT));
		}
		card.add(gains, BorderLayout.SOUTH);
		return card;
	}

	/** A goal's name, where it stands now and after the session, and a bar with the gain lit. */
	private static JPanel gainRow(SessionPlan.Gain gain)
	{
		Plan before = gain.getBefore();
		Plan after = gain.getAfter();
		JPanel row = new JPanel(new BorderLayout(0, 2));
		row.setOpaque(false);
		JPanel line = new JPanel(new BorderLayout(6, 0));
		line.setOpaque(false);
		JLabel name = Ui.label(before.getTitle(), Theme.small(), Theme.TEXT);
		name.setToolTipText(before.getTitle());
		name.setMinimumSize(new java.awt.Dimension(10, name.getPreferredSize().height));
		line.add(name, BorderLayout.CENTER);
		String figures = after.isComplete() ? "Done!" : percent(before) + " to " + percent(after);
		line.add(Ui.label(figures, Theme.small(), after.isComplete() ? Theme.GREEN
			: after.getProgress() > before.getProgress() ? Theme.GOLD_BRIGHT : Theme.TEXT_FAINT), BorderLayout.EAST);
		row.add(line, BorderLayout.NORTH);
		ProgressBar bar = new ProgressBar(4);
		bar.setProgress(after.getProgress(), after.isComplete(), false);
		bar.setBaseline(before.getProgress());
		row.add(bar, BorderLayout.CENTER);
		return row;
	}

	private static String percent(Plan plan)
	{
		return Math.round(plan.getProgress() * 100) + "%";
	}

	/** "For Dragon Slayer II" when one goal is served, otherwise a count: two titles would not fit. */
	static String serves(Navigation nav, List<Goal> goals)
	{
		if (goals.size() == 1)
		{
			return "For " + nav.planner().plan(goals.get(0), nav.account()).getTitle();
		}
		return "For " + goals.size() + " goals";
	}
}
