package com.codex.navigator.core;

import com.codex.navigator.data.QuestDef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Experience;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * What to do with a set amount of play, such as the next two hours. The route through every pinned goal is
 * walked in order: steps that fit in the time left are taken whole, and once nothing else fits the best training
 * step takes what remains. After each step the account is replanned as though it were done, so a quest that the
 * last one unlocked can follow it. The result says how far each goal moves.
 */
public final class SessionPlan
{
	/** Time left over below this is not worth another step. */
	private static final double MIN_STEP_HOURS = 5 / 60.0;
	/** A safety stop; each step finishes a quest or a level, so real plans end long before this. */
	private static final int MAX_STEPS = 40;

	/** One thing to do in the session. */
	public static final class Item
	{
		private final Step step;
		private final double hours;
		private final List<Goal> serves;
		private final boolean partial;

		Item(Step step, double hours, List<Goal> serves, boolean partial)
		{
			this.step = step;
			this.hours = hours;
			this.serves = Collections.unmodifiableList(serves);
			this.partial = partial;
		}

		/** The step as it should read: "Start Plague City", "Train Agility to 54". */
		public Step getStep()
		{
			return step;
		}

		/** About how long it takes. */
		public double getHours()
		{
			return hours;
		}

		/** The pinned goals it moves. */
		public List<Goal> getServes()
		{
			return serves;
		}

		/** True for training that stops short of the level the goals need, because the time ran out. */
		public boolean isPartial()
		{
			return partial;
		}
	}

	/** How one goal moves over the session. */
	public static final class Gain
	{
		private final Plan before;
		private final Plan after;

		Gain(Plan before, Plan after)
		{
			this.before = before;
			this.after = after;
		}

		public Plan getBefore()
		{
			return before;
		}

		public Plan getAfter()
		{
			return after;
		}
	}

	private final List<Item> items;
	private final List<Gain> gains;
	private final double used;

	private SessionPlan(List<Item> items, List<Gain> gains, double used)
	{
		this.items = Collections.unmodifiableList(items);
		this.gains = Collections.unmodifiableList(gains);
		this.used = used;
	}

	public List<Item> getItems()
	{
		return items;
	}

	/** Every unfinished pinned goal, in pin order, with where it stands now and after the session. */
	public List<Gain> getGains()
	{
		return gains;
	}

	/** Hours the steps add up to; at most the budget. */
	public double getUsed()
	{
		return used;
	}

	public static SessionPlan build(Planner planner, List<Goal> pinned, AccountSnapshot account, double budget)
	{
		List<Item> items = new ArrayList<>();
		AccountSnapshot now = account;
		double left = budget;
		for (int i = 0; i < MAX_STEPS && left >= MIN_STEP_HOURS; i++)
		{
			Route route = Route.build(planner, pinned, now);
			Route.Leg whole = null;
			Route.Leg train = null;
			for (Route.Leg leg : route.getLegs())
			{
				Step.Kind kind = leg.getStep().getKind();
				if (kind != Step.Kind.QUEST && kind != Step.Kind.TRAIN)
				{
					// Quest points, a combat level and diary tasks follow from other steps or cannot be timed.
					continue;
				}
				if (leg.getHours() <= left + 1e-9)
				{
					whole = leg;
					break;
				}
				if (train == null && kind == Step.Kind.TRAIN)
				{
					train = leg;
				}
			}
			if (whole != null)
			{
				items.add(new Item(whole.getStep(), whole.getHours(), whole.getServes(), false));
				now = apply(planner, now, whole, whole.getHours());
				left -= whole.getHours();
				continue;
			}
			if (train == null)
			{
				break;
			}
			// Nothing else fits: spend what is left on the best training step, as far as it gets.
			Skill skill = train.getStep().getSkill();
			int reached = Effort.xpAfter(skill, now.xp(skill), left, now.pace(skill), Experience.getXpForLevel(train.getTarget()));
			int level = Experience.getLevelForXp(reached);
			String title = level > now.level(skill) ? "Train " + skill.getName() + " to " + level
				: "Train " + skill.getName() + " toward " + (now.level(skill) + 1);
			Step step = new Step(Step.Kind.TRAIN, Step.Status.READY, title,
				"On the way to " + train.getTarget(), null, skill);
			items.add(new Item(step, left, train.getServes(), true));
			now = withXp(now, skill, reached);
			left = 0;
		}

		List<Gain> gains = new ArrayList<>();
		for (Goal goal : pinned)
		{
			Plan before = planner.plan(goal, account);
			if (!before.isComplete())
			{
				gains.add(new Gain(before, planner.plan(goal, now)));
			}
		}
		return new SessionPlan(items, gains, budget - left);
	}

	/** The account as it would be after a whole step: the quest finished, or the level reached. */
	private static AccountSnapshot apply(Planner planner, AccountSnapshot account, Route.Leg leg, double hours)
	{
		Step step = leg.getStep();
		if (step.getKind() == Step.Kind.QUEST)
		{
			QuestDef quest = planner.getData().quest(step.getQuest());
			AccountSnapshot.Builder b = account.toBuilder().quest(step.getQuest(), QuestState.FINISHED);
			if (quest != null && account.quest(quest.name) != QuestState.FINISHED)
			{
				b.questPoints(account.getQuestPoints() + quest.points);
			}
			return b.build();
		}
		Skill skill = step.getSkill();
		return withXp(account, skill, Math.max(account.xp(skill), Experience.getXpForLevel(leg.getTarget())));
	}

	/** The account with a skill's experience raised, its level and (for combat skills) its combat level to match. */
	private static AccountSnapshot withXp(AccountSnapshot account, Skill skill, int xp)
	{
		AccountSnapshot.Builder b = account.toBuilder().skill(skill, Experience.getLevelForXp(xp), xp);
		AccountSnapshot raised = b.build();
		int combat = Experience.getCombatLevel(raised.level(Skill.ATTACK), raised.level(Skill.STRENGTH),
			raised.level(Skill.DEFENCE), raised.level(Skill.HITPOINTS), raised.level(Skill.MAGIC),
			raised.level(Skill.RANGED), raised.level(Skill.PRAYER));
		return combat > account.getCombatLevel() ? b.combatLevel(combat).build() : raised;
	}
}
