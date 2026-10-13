package com.codex.navigator.ui;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.Plan;
import com.codex.navigator.core.Step;
import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.DiaryDef;
import com.codex.navigator.data.QuestDef;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.components.IconTextField;

/** Everything in the Codex, searchable: quests, diaries and combat achievement tiers. */
final class AtlasView extends JPanel
{
	private static final String[] FILTERS = {"All", "Ready", "To do", "Done"};

	private final Navigation nav;
	private final IconTextField search = new IconTextField();
	private final Segmented kind = new Segmented(false, "Quests", "Diaries", "Combat");
	private final Segmented filter = new Segmented(false, FILTERS);
	private final JScrollPane scroll;

	AtlasView(Navigation nav)
	{
		super(new BorderLayout());
		this.nav = nav;
		setOpaque(false);

		StackPanel controls = new StackPanel(6, 8);
		controls.setBorder(new EmptyBorder(8, 8, 4, 8));
		search.setIcon(IconTextField.Icon.SEARCH);
		search.setBackground(Theme.INK_DEEP);
		search.setHoverBackgroundColor(Theme.CARD);
		search.setPreferredSize(new java.awt.Dimension(10, 28));
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				rebuild();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				rebuild();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				rebuild();
			}
		});
		search.addClearListener(this::rebuild);
		controls.add(search);
		controls.add(kind);
		controls.add(filter);
		kind.onChange(i ->
		{
			filter.setVisible(i == 0);
			search.setVisible(i != 2);
			controls.revalidate();
			rebuild();
		});
		filter.onChange(i -> rebuild());
		add(controls, BorderLayout.NORTH);

		scroll = NavigatorPanel.scroller(new StackPanel(6, 8));
		add(scroll, BorderLayout.CENTER);
	}

	void rebuild()
	{
		int position = scroll.getVerticalScrollBar().getValue();
		StackPanel list;
		switch (kind.getSelected())
		{
			case 1:
				list = diaries();
				break;
			case 2:
				list = combat();
				break;
			default:
				list = quests();
		}
		scroll.setViewportView(list);
		SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(position));
	}

	private String query()
	{
		return search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
	}

	private StackPanel quests()
	{
		StackPanel stack = new StackPanel(4, 8);
		AccountSnapshot account = nav.account();
		String q = query();
		List<Step> steps = new ArrayList<>();
		Map<String, QuestDef> defs = new LinkedHashMap<>();
		for (QuestDef quest : nav.planner().getData().quests())
		{
			if (quest.miniquest && !nav.includeMiniquests())
			{
				continue;
			}
			if (!q.isEmpty() && !quest.name.toLowerCase(Locale.ROOT).contains(q)
				&& (quest.series == null || !quest.series.toLowerCase(Locale.ROOT).contains(q)))
			{
				continue;
			}
			Step step = nav.planner().questStep(quest, account);
			if (!matches(step.getStatus()))
			{
				continue;
			}
			steps.add(step);
			defs.put(quest.name, quest);
		}
		steps.sort(Comparator.comparingInt((Step s) -> order(s.getStatus())).thenComparing(Step::getTitle));
		stack.add(new SectionHeader(FILTERS[filter.getSelected()] + " quests", String.valueOf(steps.size())));
		for (Step step : steps)
		{
			QuestDef quest = defs.get(step.getTitle());
			Goal goal = Goal.quest(quest.name);
			String detail = step.getStatus() == Step.Status.BLOCKED ? step.getDetail() : CompassView.subtitle(quest);
			stack.add(Row.of(step.getStatus(), quest.name, detail,
				nav.goals().isPinned(goal) ? new Pill("Pinned", Theme.GOLD) : null, null, () -> nav.open(goal)));
		}
		if (steps.isEmpty())
		{
			stack.add(GoalCard.hint("Nothing matches. Try another word or filter."));
		}
		return stack;
	}

	private boolean matches(Step.Status status)
	{
		switch (filter.getSelected())
		{
			case 1:
				return status == Step.Status.READY || status == Step.Status.IN_PROGRESS;
			case 2:
				return status != Step.Status.DONE;
			case 3:
				return status == Step.Status.DONE;
			default:
				return true;
		}
	}

	private static int order(Step.Status status)
	{
		switch (status)
		{
			case IN_PROGRESS:
				return 0;
			case READY:
				return 1;
			case BLOCKED:
				return 2;
			case DONE:
				return 4;
			default:
				return 3;
		}
	}

	private StackPanel diaries()
	{
		StackPanel stack = new StackPanel(6, 8);
		AccountSnapshot account = nav.account();
		String q = query();
		Map<String, List<DiaryDef>> areas = new LinkedHashMap<>();
		for (DiaryDef d : nav.planner().getData().diaries())
		{
			if (q.isEmpty() || d.area.toLowerCase(Locale.ROOT).contains(q))
			{
				areas.computeIfAbsent(d.area, k -> new ArrayList<>()).add(d);
			}
		}
		stack.add(new SectionHeader("Achievement diaries", account.diariesCompleted() + "/" + nav.planner().getData().diaries().size()));
		List<String> names = new ArrayList<>(areas.keySet());
		names.sort(String::compareTo);
		for (String area : names)
		{
			List<DiaryDef> tiers = areas.get(area);
			tiers.sort(Comparator.comparingInt(DiaryDef::tierRank));
			Card card = new Card(new BorderLayout(0, 6));
			int done = 0;
			JPanel chips = new JPanel(new GridLayout(1, tiers.size(), 4, 0));
			chips.setOpaque(false);
			for (DiaryDef d : tiers)
			{
				Goal goal = Goal.diary(d.name);
				Plan plan = nav.planner().plan(goal, account);
				Step.Status status = plan.isComplete() ? Step.Status.DONE
					: plan.getNext() != null && plan.getNext().getKind() == Step.Kind.DIARY_TASKS ? Step.Status.READY : Step.Status.BLOCKED;
				done += plan.isComplete() ? 1 : 0;
				TierChip chip = new TierChip(d.tier.equals("Medium") ? "Med" : d.tier, status, plan.getProgress(), () -> nav.open(goal));
				chip.setToolTipText(d.name + (plan.getNext() != null ? ": " + plan.getNext().getTitle() : ""));
				chips.add(chip);
			}
			JPanel title = new JPanel(new BorderLayout());
			title.setOpaque(false);
			title.add(Ui.label(area, Theme.title(), Theme.GOLD_BRIGHT), BorderLayout.WEST);
			title.add(Ui.label(done + "/" + tiers.size(), Theme.small(), done == tiers.size() ? Theme.GREEN : Theme.TEXT_MUTED),
				BorderLayout.EAST);
			card.add(title, BorderLayout.NORTH);
			card.add(chips, BorderLayout.CENTER);
			stack.add(card);
		}
		return stack;
	}

	private StackPanel combat()
	{
		StackPanel stack = new StackPanel(6, 8);
		AccountSnapshot account = nav.account();
		CodexData data = nav.planner().getData();
		stack.add(new SectionHeader("Combat achievements", account.getCombatAchievementPoints() + " pts"));
		for (String tier : CodexData.COMBAT_ACHIEVEMENT_TIERS)
		{
			int total = data.combatAchievementTasks(tier);
			int done = Math.min(total, account.combatAchievementsDone(tier));
			Card card = new Card(new BorderLayout(0, 5));
			JPanel line = new JPanel(new BorderLayout());
			line.setOpaque(false);
			line.add(Ui.label(tier, Theme.title(), done >= total && total > 0 ? Theme.GREEN : Theme.GOLD_BRIGHT), BorderLayout.WEST);
			line.add(Ui.label(done + " / " + total + " tasks", Theme.small(), Theme.TEXT_MUTED), BorderLayout.EAST);
			card.add(line, BorderLayout.NORTH);
			ProgressBar bar = new ProgressBar(5);
			bar.setProgress(total == 0 ? 0 : done / (double) total, total > 0 && done >= total, true);
			card.add(bar, BorderLayout.CENTER);
			stack.add(card);
		}
		stack.add(GoalCard.hint("Task counts come from the game. The in-game Combat Achievements window has each task's details."));
		return stack;
	}
}
