package com.codex.navigator.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * Which skills the pinned goals still need, and to what level: what the skills tab bars draw. Built from the same
 * plans the panel shows ({@link Planner#plan} and its {@link SkillGap}s), so a bar never disagrees with the panel.
 * Pure logic with no client access; immutable once built.
 */
public final class SkillNeeds
{
	/** One pinned goal's need of a skill. */
	public static final class Use
	{
		private final Goal goal;
		private final String title;
		private final int level;
		private final boolean boostable;

		Use(Goal goal, String title, int level, boolean boostable)
		{
			this.goal = goal;
			this.title = title;
			this.level = level;
			this.boostable = boostable;
		}

		public Goal getGoal()
		{
			return goal;
		}

		/** The goal as the panel names it, such as "Monkey Madness II" or "Ardougne Hard Diary". */
		public String getTitle()
		{
			return title;
		}

		/** The level this goal needs, counting every quest on its path. */
		public int getLevel()
		{
			return level;
		}

		/** True when a pie or potion can cover the level for every quest that asks for it. */
		public boolean isBoostable()
		{
			return boostable;
		}
	}

	/** Everything the pinned goals ask of one skill. */
	public static final class Need
	{
		private final Skill skill;
		private final List<Use> uses;
		private final List<String> holdsUp;

		Need(Skill skill, List<Use> uses, List<String> holdsUp)
		{
			this.skill = skill;
			this.uses = Collections.unmodifiableList(uses);
			this.holdsUp = Collections.unmodifiableList(holdsUp);
		}

		public Skill getSkill()
		{
			return skill;
		}

		/** The goals that need the skill, in pin order (the focus first). */
		public List<Use> getUses()
		{
			return uses;
		}

		/** The highest level any pinned goal needs: where the bar ends. */
		public int getTarget()
		{
			int max = 0;
			for (Use use : uses)
			{
				max = Math.max(max, use.level);
			}
			return max;
		}

		/**
		 * The lower levels other goals need on the way to {@link #getTarget()}, lowest first and each once: where the
		 * bar is notched.
		 */
		public List<Integer> getWaypoints()
		{
			int target = getTarget();
			List<Integer> out = new ArrayList<>();
			for (Use use : uses)
			{
				if (use.level < target && !out.contains(use.level))
				{
					out.add(use.level);
				}
			}
			Collections.sort(out);
			return out;
		}

		/**
		 * The goals whose very next step is training this skill: nothing else on their path can be done first.
		 * Empty when the skill holds nothing up today.
		 */
		public List<String> getHoldsUp()
		{
			return holdsUp;
		}

		public boolean isBlocking()
		{
			return !holdsUp.isEmpty();
		}
	}

	private static final SkillNeeds NONE = new SkillNeeds(new EnumMap<>(Skill.class));

	private final Map<Skill, Need> needs;

	private SkillNeeds(Map<Skill, Need> needs)
	{
		this.needs = Collections.unmodifiableMap(needs);
	}

	public static SkillNeeds none()
	{
		return NONE;
	}

	/** The need for a skill, or null when no unfinished pinned goal asks for more than the current level. */
	public Need get(Skill skill)
	{
		return needs.get(skill);
	}

	public Map<Skill, Need> all()
	{
		return needs;
	}

	public boolean isEmpty()
	{
		return needs.isEmpty();
	}

	/**
	 * Plans every pinned goal and gathers the levels they still need. Finished goals and levels already reached are
	 * left out; a level is marked as holding a goal up when the planner's next step for it is training that level.
	 */
	public static SkillNeeds of(Planner planner, List<Goal> pinned, AccountSnapshot account)
	{
		if (!account.isLoggedIn() || pinned.isEmpty())
		{
			return NONE;
		}
		Map<Skill, List<Use>> uses = new EnumMap<>(Skill.class);
		Map<Skill, List<String>> holdsUp = new EnumMap<>(Skill.class);
		for (Goal goal : pinned)
		{
			Plan plan = planner.plan(goal, account);
			if (plan.isComplete())
			{
				continue;
			}
			for (SkillGap gap : plan.getSkills())
			{
				if (!gap.isMet())
				{
					uses.computeIfAbsent(gap.getSkill(), s -> new ArrayList<>())
						.add(new Use(goal, plan.getTitle(), gap.getTarget(), gap.isBoostable()));
				}
			}
			Step next = plan.getNext();
			if (next != null && next.getKind() == Step.Kind.TRAIN && next.getSkill() != null)
			{
				holdsUp.computeIfAbsent(next.getSkill(), s -> new ArrayList<>()).add(plan.getTitle());
			}
		}
		Map<Skill, Need> out = new EnumMap<>(Skill.class);
		for (Map.Entry<Skill, List<Use>> e : uses.entrySet())
		{
			out.put(e.getKey(), new Need(e.getKey(), e.getValue(),
				holdsUp.getOrDefault(e.getKey(), Collections.emptyList())));
		}
		return new SkillNeeds(out);
	}

	/** Changes whenever a bar or a tooltip would; lets the client thread skip redrawing an unchanged tab. */
	public String signature()
	{
		StringBuilder sb = new StringBuilder();
		for (Need need : needs.values())
		{
			sb.append(need.skill.name()).append(need.holdsUp);
			for (Use use : need.uses)
			{
				sb.append('|').append(use.title).append(use.level).append(use.boostable);
			}
			sb.append(';');
		}
		return sb.toString();
	}
}
