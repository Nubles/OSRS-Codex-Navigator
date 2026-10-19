package com.codex.navigator.ui;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Effort;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.Plan;
import com.codex.navigator.core.Recommender;
import com.codex.navigator.core.Step;
import com.codex.navigator.data.QuestDef;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** The home view: your focus, then what is worth doing right now. */
final class CompassView
{
	private CompassView()
	{
	}

	static StackPanel build(Navigation nav)
	{
		StackPanel stack = new StackPanel(6, 8);
		AccountSnapshot account = nav.account();
		if (!account.isLoggedIn())
		{
			stack.add(welcome(nav));
			return stack;
		}

		Goal focus = nav.goals().focus();
		if (focus != null)
		{
			Plan plan = nav.planner().plan(focus, account);
			stack.add(GoalCard.build(nav, plan, true, false));
		}
		else
		{
			stack.add(choose(nav));
		}

		Recommender rec = nav.recommender();
		List<QuestDef> active = rec.inProgress(account);
		if (!active.isEmpty())
		{
			stack.add(new SectionHeader("In progress", String.valueOf(active.size())));
			for (QuestDef q : active)
			{
				stack.add(Row.of(Step.Status.IN_PROGRESS, q.name, q.difficulty == null ? "" : q.difficulty, null, null,
					() -> nav.open(Goal.quest(q.name))));
			}
		}

		List<Goal> pinned = nav.goals().goals();
		List<Recommender.Pick> ready = rec.readyNow(account, pinned, nav.includeMiniquests(), 6);
		if (!ready.isEmpty())
		{
			stack.add(new SectionHeader("Best next"));
			if (!account.isMembers())
			{
				stack.add(GoalCard.hint("On a free world, so only free quests are suggested."));
			}
			for (Recommender.Pick pick : ready)
			{
				QuestDef q = pick.getQuest();
				stack.add(Row.of(Step.Status.READY, q.name, subtitle(q, pick.getHours()), pick.getReason() == null ? null
					: new Pill(pick.getReason(), pick.getServes().isEmpty() ? Theme.TEAL : Theme.GOLD), null,
					() -> nav.open(Goal.quest(q.name))));
			}
		}

		List<Recommender.Pick> almost = rec.almostThere(account, pinned, nav.includeMiniquests(), 4);
		if (!almost.isEmpty())
		{
			stack.add(new SectionHeader("Almost there"));
			for (Recommender.Pick pick : almost)
			{
				QuestDef q = pick.getQuest();
				stack.add(Row.of(Step.Status.BLOCKED, q.name, Effort.format(pick.getHours()) + "  ·  " + pick.getReason(),
					pick.getServes().isEmpty() ? null : new Pill("Goal", Theme.GOLD), null,
					() -> nav.open(Goal.quest(q.name))));
			}
		}

		List<Plan> diaries = rec.closestDiaries(account, 3);
		if (!diaries.isEmpty())
		{
			stack.add(new SectionHeader("Diaries within reach"));
			for (Plan plan : diaries)
			{
				stack.add(diaryRow(nav, plan));
			}
		}
		return stack;
	}

	/** About how long it takes to play and its quest points, such as "~35 min · 2 QP". */
	static String subtitle(QuestDef q, double hours)
	{
		// The time stands in for the difficulty here: it says the same thing in a unit worth planning with.
		String time = Effort.format(hours);
		return q.points > 0 ? time + "  ·  " + q.points + " QP" : q.miniquest ? time + "  ·  Miniquest" : time;
	}

	static String subtitle(QuestDef q)
	{
		String kind = q.miniquest ? "Miniquest" : q.difficulty;
		return q.points > 0 ? kind + "  ·  " + q.points + " QP" : kind;
	}

	private static Card diaryRow(Navigation nav, Plan plan)
	{
		Card card = new Card(new BorderLayout(0, 4));
		card.setBorder(new EmptyBorder(7, 10, 8, 10));
		JPanel line = new JPanel(new BorderLayout());
		line.setOpaque(false);
		line.add(Ui.label(plan.getTitle().replace(" Diary", ""), Theme.body(), Theme.TEXT), BorderLayout.WEST);
		boolean tasks = plan.isAwaitingTasks();
		line.add(tasks ? new Pill("Reqs met", Theme.TEAL)
			: Ui.label(Math.round(plan.getProgress() * 100) + "%", Theme.small(), Theme.TEXT_MUTED), BorderLayout.EAST);
		card.add(line, BorderLayout.NORTH);
		ProgressBar bar = new ProgressBar(4);
		bar.setProgress(plan.getProgress(), false, false);
		card.add(bar, BorderLayout.CENTER);
		Step next = plan.getNext();
		if (tasks)
		{
			card.add(Ui.label("Only the diary tasks are left", Theme.small(), Theme.TEXT_MUTED), BorderLayout.SOUTH);
		}
		else if (next != null)
		{
			JLabel label = Ui.label("Next: " + NextStepBox.verb(next) + next.getTitle(), Theme.small(), Theme.TEXT_MUTED);
			label.setToolTipText(label.getText());
			label.setMinimumSize(new java.awt.Dimension(10, label.getPreferredSize().height));
			card.add(label, BorderLayout.SOUTH);
		}
		card.onClick(() -> nav.open(plan.getGoal()));
		return card;
	}

	private static Card choose(Navigation nav)
	{
		Card card = new Card(new BorderLayout(0, 6));
		card.setBorder(new EmptyBorder(10, 10, 10, 10));
		card.highlighted(true);
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.add(Ui.label("SET A HEADING", Theme.small(), Theme.GOLD));
		text.add(Ui.wrapped("Pin any quest or diary and the Codex charts the whole path to it.", Theme.body(), Theme.TEXT, 168));
		card.add(text, BorderLayout.CENTER);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		actions.setOpaque(false);
		actions.add(new CodexButton("Browse the Atlas", true, () -> nav.showTab(2)));
		actions.add(new JPanel()
		{
			{
				setOpaque(false);
				setPreferredSize(new java.awt.Dimension(6, 1));
			}
		});
		actions.add(new CodexButton("Quest cape", false, () -> nav.goals().toggle(Goal.QUEST_CAPE)));
		card.add(actions, BorderLayout.SOUTH);
		return card;
	}

	private static Card welcome(Navigation nav)
	{
		Card card = new Card(new BorderLayout(0, 6));
		card.setBorder(new EmptyBorder(14, 12, 14, 12));
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.add(Ui.label("THE CODEX AWAITS", Theme.small(), Theme.GOLD));
		text.add(Ui.wrapped("Log in and the Codex reads your quests, diaries, levels and combat tasks straight from the game.",
			Theme.body(), Theme.TEXT, 168));
		text.add(Ui.wrapped("Nothing leaves your computer.", Theme.small(), Theme.TEXT_MUTED, 168));
		card.add(text, BorderLayout.CENTER);
		return card;
	}
}
