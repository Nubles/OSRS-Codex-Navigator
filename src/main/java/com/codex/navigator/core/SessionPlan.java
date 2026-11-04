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
			// A hat leading the pins is hunted first, at this player's own rate.
			Item hunt = hunt(planner, pinned, now, left, true);
			if (hunt != null)
			{
				items.add(hunt);
				now = afterHunt(now, hunt, left);
				left -= hunt.getHours();
				continue;
			}
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
				// Nothing to quest or train: a pinned hat further down takes what is left.
				hunt = hunt(planner, pinned, now, left, false);
				if (hunt == null)
				{
					break;
				}
				items.add(hunt);
				now = afterHunt(now, hunt, left);
				left -= hunt.getHours();
				continue;
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

	/**
	 * Hunting chompy birds for a pinned hat, as long as the hat or the time allows, at the kill rate learned from this
	 * player; null when no hat is pinned that can be timed. With {@code leading}, only a hat that is the first
	 * unfinished pin counts.
	 */
	private static Item hunt(Planner planner, List<Goal> pinned, AccountSnapshot now, double left, boolean leading)
	{
		ChompyHunt.View hunt = now.chompy();
		for (Goal goal : pinned)
		{
			Plan plan = planner.plan(goal, now);
			if (plan.isComplete())
			{
				continue;
			}
			Step next = plan.getNext();
			if (goal.getType() == Goal.Type.CHOMPY && next != null && next.getKind() == Step.Kind.CHOMPY
				&& hunt.getPerHour() > 0 && plan.getHoursLeft() > 0)
			{
				ChompyHunt.Hat hat = ChompyHunt.hat(goal.getName());
				double hours = Math.min(left, plan.getHoursLeft());
				int kills = (int) Math.floor(hours * hunt.getPerHour() + 1e-9);
				if (kills <= 0)
				{
					return null;
				}
				boolean whole = kills >= hat.getKills() - hunt.getKills();
				String title = whole ? next.getTitle() : "Kill " + kills + " chompy birds toward " + hat.getName();
				Step step = new Step(Step.Kind.CHOMPY, Step.Status.READY, title,
					"At your " + Math.round(hunt.getPerHour()) + " an hour", null, null);
				List<Goal> serves = new ArrayList<>();
				serves.add(goal);
				return new Item(step, whole ? plan.getHoursLeft() : hours, serves, !whole);
			}
			if (leading)
			{
				return null;
			}
		}
		return null;
	}

	/** The account after a hunt: the kills it makes added to the count. */
	private static AccountSnapshot afterHunt(AccountSnapshot account, Item hunt, double left)
	{
		ChompyHunt.View v = account.chompy();
		double made = Math.min(left, hunt.getHours()) * v.getPerHour();
		int kills = hunt.isPartial() ? (int) Math.floor(made + 1e-9) : (int) Math.ceil(made - 1e-6);
		return account.toBuilder().chompy(v.plus(kills)).build();
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
