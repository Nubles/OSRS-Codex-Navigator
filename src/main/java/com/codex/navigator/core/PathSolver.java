package com.codex.navigator.core;

import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.DiaryDef;
import com.codex.navigator.data.QuestDef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Experience;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * The fastest order to a big goal: every quest it needs and every level they ask for, laid out so the experience
 * quests reward is banked before any of it is trained by hand.
 *
 * <p>The rule is "train only when stuck". Every quest that can be started is done first, since its reward only ever
 * shortens training that comes later. When nothing can be started, the solver unlocks the quest that costs the least
 * training to open (quest points first come from the quickest quests outside the goal), banks what it rewards, and
 * looks again. A goal's own levels (a diary's) are trained last, after every reward is in. The result names the
 * critical path, the longest chain of steps that wait on each other, and the skills whose training gates the most.
 *
 * <p>Pure logic over the bundled data and an account snapshot, so it runs on any thread and is tested without a game.
 */
public final class PathSolver
{
	/** Melee skills trained, one level at a time, when only a combat level stands in the way. */
	private static final Skill[] COMBAT = {Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS};

	private final Planner planner;
	private final CodexData data;

	public PathSolver(Planner planner)
	{
		this.planner = planner;
		this.data = planner.getData();
	}

	/** True for goals the solver orders: a quest, a diary tier or the quest cape. */
	public static boolean solves(Goal goal)
	{
		return goal != null && goal.getType() != Goal.Type.SKILL && goal.getType() != Goal.Type.COMBAT
			&& goal.getType() != Goal.Type.CHOMPY;
	}

	/** One step of the order. */
	public static final class Leg
	{
		public enum Kind
		{
			/** A quest the goal needs. */
			QUEST,
			/** A quest outside the goal, done for its quest points. */
			POINTS,
			/** Training one skill from one level to another. */
			TRAIN,
			/** Something the solver cannot time, such as a quest missing from the data. */
			CHECK
		}

		private final Kind kind;
		private final String title;
		private final String quest;
		private final Skill skill;
		private final int from;
		private final int to;
		private final double hours;
		/** For training, the quest it opens; null for the goal's own levels. */
		private final String opens;
		/** Experience a quest rewards that later training is spared, by skill. */
		private final Map<Skill, Double> xp;
		private final boolean boosted;
		final List<Integer> after = new ArrayList<>();
		private boolean critical;

		Leg(Kind kind, String title, String quest, Skill skill, int from, int to, double hours, String opens,
			Map<Skill, Double> xp, boolean boosted)
		{
			this.kind = kind;
			this.title = title;
			this.quest = quest;
			this.skill = skill;
			this.from = from;
			this.to = to;
			this.hours = hours;
			this.opens = opens;
			this.xp = xp == null ? Collections.emptyMap() : xp;
			this.boosted = boosted;
		}

		public Kind getKind()
		{
			return kind;
		}

		public String getTitle()
		{
			return title;
		}

		public String getQuest()
		{
			return quest;
		}

		public Skill getSkill()
		{
			return skill;
		}

		public int getFrom()
		{
			return from;
		}

		public int getTo()
		{
			return to;
		}

		public double getHours()
		{
			return hours;
		}

		public String getOpens()
		{
			return opens;
		}

		public Map<Skill, Double> getXp()
		{
			return xp;
		}

		/** True when the quest is started with a boost rather than trained for. */
		public boolean isBoosted()
		{
			return boosted;
		}

		/** True when the step lies on the critical path. */
		public boolean isCritical()
		{
			return critical;
		}
	}

	/** One skill whose training gates the goal. */
	public static final class Gate
	{
		private final Skill skill;
		private final int from;
		private final int to;
		private final double hours;
		private final List<String> opens;

		Gate(Skill skill, int from, int to, double hours, List<String> opens)
		{
			this.skill = skill;
			this.from = from;
			this.to = to;
			this.hours = hours;
			this.opens = Collections.unmodifiableList(opens);
		}

		public Skill getSkill()
		{
			return skill;
		}

		public int getFrom()
		{
			return from;
		}

		public int getTo()
		{
			return to;
		}

		public double getHours()
		{
			return hours;
		}

		/** The quests this training opens, in order. */
		public List<String> getOpens()
		{
			return opens;
		}
	}

	/** The solved order and what it adds up to. */
	public static final class Solution
	{
		private final Goal goal;
		private final String title;
		private final List<Leg> legs;
		private final double hours;
		private final double questHours;
		private final double trainHours;
		private final double byHandHours;
		private final List<Gate> gates;
		private final double xpBanked;
		/** Experience trained by hand in this order. */
		double trainedXp;

		Solution(Goal goal, String title, List<Leg> legs, double questHours, double trainHours, double byHandHours,
			List<Gate> gates, double xpBanked)
		{
			this.goal = goal;
			this.title = title;
			this.legs = Collections.unmodifiableList(legs);
			this.questHours = questHours;
			this.trainHours = trainHours;
			this.hours = questHours + trainHours;
			this.byHandHours = byHandHours;
			this.gates = Collections.unmodifiableList(gates);
			this.xpBanked = xpBanked;
		}

		public Goal getGoal()
		{
			return goal;
		}

		public String getTitle()
		{
			return title;
		}

		public List<Leg> getLegs()
		{
			return legs;
		}

		/** About how long the whole order takes. */
		public double getHours()
		{
			return hours;
		}

		public double getQuestHours()
		{
			return questHours;
		}

		public double getTrainHours()
		{
			return trainHours;
		}

		/** The same goal with every level trained by hand, as if no quest rewarded experience. */
		public double getByHandHours()
		{
			return byHandHours;
		}

		/** Hours the order saves against training every level first; never negative. */
		public double getSaved()
		{
			return Math.max(0, byHandHours - hours);
		}

		/** Skills whose training gates the goal, most hours first. */
		public List<Gate> getGates()
		{
			return gates;
		}

		/** Experience quest rewards spare from training by hand: what training every level up front would add. */
		public double getXpBanked()
		{
			return xpBanked;
		}

		/** The legs on the critical path, in order. */
		public List<Leg> critical()
		{
			List<Leg> out = new ArrayList<>();
			for (Leg leg : legs)
			{
				if (leg.isCritical())
				{
					out.add(leg);
				}
			}
			return out;
		}

		/** Hours along the critical path. */
		public double criticalHours()
		{
			double h = 0;
			for (Leg leg : critical())
			{
				h += leg.getHours();
			}
			return h;
		}

		public boolean isEmpty()
		{
			return legs.isEmpty();
		}
	}

	/** What the account looks like as the order is followed. */
	private final class State
	{
		final AccountSnapshot account;
		final Map<Skill, Double> xp = new EnumMap<>(Skill.class);
		final Set<String> done = new HashSet<>();
		final Map<String, Integer> legOfQuest = new HashMap<>();
		final Map<Skill, Integer> lastTrain = new EnumMap<>(Skill.class);
		/** Quests whose reward in a skill came in since it was last trained: its next training waits on them. */
		final Map<Skill, List<Integer>> rewardsSince = new EnumMap<>(Skill.class);
		int questPoints;
		int combatBase;
		int combatStart;
		/** False for the comparison run that trains every level by hand, as if no quest rewarded experience. */
		final boolean rewards;
		/** Experience trained by hand so far. */
		double trainedXp;

		State(AccountSnapshot account, boolean rewards)
		{
			this.account = account;
			this.rewards = rewards;
			for (Skill skill : Skill.values())
			{
				int real = account.xp(skill);
				int floor = Experience.getXpForLevel(Math.max(1, Math.min(99, account.level(skill))));
				xp.put(skill, (double) Math.max(real, floor));
			}
			questPoints = account.getQuestPoints();
			combatBase = account.getCombatLevel();
			combatStart = combatOf();
		}

		int level(Skill skill)
		{
			return Experience.getLevelForXp((int) Math.min(Experience.MAX_SKILL_XP, xp.get(skill)));
		}

		int combat()
		{
			return combatBase + combatOf() - combatStart;
		}

		private int combatOf()
		{
			return Experience.getCombatLevel(level(Skill.ATTACK), level(Skill.STRENGTH), level(Skill.DEFENCE),
				level(Skill.HITPOINTS), level(Skill.MAGIC), level(Skill.RANGED), level(Skill.PRAYER));
		}

		boolean questDone(String name)
		{
			return done.contains(name) || account.questDone(name);
		}

		/** Hours to train a skill from where the order has it to a level; 0 when already there. */
		double hoursTo(Skill skill, int target)
		{
			int need = Experience.getXpForLevel(Math.max(1, Math.min(99, target)));
			double have = xp.get(skill);
			return have >= need ? 0 : Effort.trainingHours(skill, (int) have, target, account.pace(skill));
		}

		/** A level the quest asks for that a boost covers from where the order has the skill. */
		boolean boostCovers(QuestDef quest, Skill skill, int need)
		{
			return data.boostable(quest.name, skill.getName(), need) && Boosts.reaches(skill, level(skill), need, account);
		}
	}

	/** Recent orders by goal and account, so a refresh with nothing new costs nothing; the panel and the board share it. */
	private final Map<List<Object>, Solution> recent = Collections.synchronizedMap(
		new LinkedHashMap<List<Object>, Solution>(16, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<List<Object>, Solution> eldest)
			{
				return size() > 8;
			}
		});

	/**
	 * As {@link #solve}, reusing a recent answer while the goal and the account's levels, quests and pace are the same.
	 * Experience alone moving does not count, as training ticks it up with every action. Safe on any thread.
	 */
	public Solution solveCached(Goal goal, AccountSnapshot account)
	{
		List<Object> key = java.util.Arrays.asList(goal, account.structureSignature(), account.paces());
		if (recent.containsKey(key))
		{
			return recent.get(key);
		}
		Solution solution = solve(goal, account);
		recent.put(key, solution);
		return solution;
	}

	/** Solves the order for a goal, or returns null for a level goal, an unknown goal or one already complete. */
	public Solution solve(Goal goal, AccountSnapshot account)
	{
		if (!solves(goal))
		{
			return null;
		}
		Plan plan = planner.plan(goal, account);
		if (plan.isComplete())
		{
			return null;
		}
		Set<String> closure = new LinkedHashSet<>();
		Map<Skill, Integer> ownLevels = new EnumMap<>(Skill.class);
		int qpTarget = 0;
		int combatTarget = 0;
		switch (goal.getType())
		{
			case QUEST:
				if (data.quest(goal.getName()) == null)
				{
					return null;
				}
				closure.addAll(questsOf(plan));
				break;
			case DIARY:
			{
				DiaryDef diary = data.diary(goal.getName());
				if (diary == null)
				{
					return null;
				}
				closure.addAll(questsOf(plan));
				for (Map.Entry<String, Integer> e : diary.skills.entrySet())
				{
					Skill skill = Planner.skill(e.getKey());
					if (skill != null)
					{
						ownLevels.merge(skill, e.getValue(), Math::max);
					}
				}
				qpTarget = diary.questPoints;
				combatTarget = diary.combatLevel;
				break;
			}
			default:
				closure.addAll(questsOf(plan));
		}
		// The same order worked without quest rewards: what training every level up front would cost.
		Solution byHand = order(goal, plan, closure, ownLevels, qpTarget, combatTarget, account, false, -1, 0);
		return order(goal, plan, closure, ownLevels, qpTarget, combatTarget, account, true, byHand.getHours(),
			byHand.trainedXp);
	}

	/** The quests on a plan's path, in the planner's prerequisites-first order. */
	private static List<String> questsOf(Plan plan)
	{
		List<String> out = new ArrayList<>();
		for (Step step : plan.getPath())
		{
			out.add(step.getQuest() != null ? step.getQuest() : step.getTitle());
		}
		return out;
	}

	private Solution order(Goal goal, Plan plan, Set<String> closure, Map<Skill, Integer> ownLevels, int qpTarget,
		int combatTarget, AccountSnapshot account, boolean rewards, double byHandHours, double byHandXp)
	{
		State state = new State(account, rewards);
		List<Leg> legs = new ArrayList<>();
		Map<Skill, Integer> trainedFrom = new EnumMap<>(Skill.class);
		Map<Skill, Double> trainHoursBySkill = new EnumMap<>(Skill.class);
		Map<Skill, List<String>> opensBySkill = new EnumMap<>(Skill.class);

		List<String> remaining = new ArrayList<>();
		for (String name : closure)
		{
			QuestDef quest = data.quest(name);
			if (quest == null)
			{
				legs.add(new Leg(Leg.Kind.CHECK, name, null, null, 0, 0, 0, null, null, false));
				continue;
			}
			if (!state.questDone(name))
			{
				remaining.add(name);
			}
		}
		Set<Skill> needed = neededSkills(remaining, ownLevels);

		int guard = 0;
		while (!remaining.isEmpty() && guard++ < 2000)
		{
			String ready = firstReady(remaining, state);
			if (ready != null)
			{
				doQuest(ready, Leg.Kind.QUEST, state, legs, needed);
				remaining.remove(ready);
				continue;
			}
			// Stuck: open the quest that costs least to open, by training or by quest points.
			String cheapest = null;
			double cheapestCost = Double.MAX_VALUE;
			for (String name : remaining)
			{
				QuestDef quest = data.quest(name);
				if (!prereqsDone(quest, state))
				{
					continue;
				}
				double cost = openCost(quest, state);
				if (cost < cheapestCost)
				{
					cheapestCost = cost;
					cheapest = name;
				}
			}
			if (cheapest == null)
			{
				// A prerequisite is outside the data or the data loops: say so and stop rather than guess.
				for (String name : remaining)
				{
					legs.add(new Leg(Leg.Kind.CHECK, name, name, null, 0, 0, 0, null, null, false));
				}
				break;
			}
			QuestDef quest = data.quest(cheapest);
			for (Map.Entry<String, Integer> e : quest.skills.entrySet())
			{
				Skill skill = Planner.skill(e.getKey());
				if (skill == null || state.level(skill) >= e.getValue() || state.boostCovers(quest, skill, e.getValue()))
				{
					continue;
				}
				train(skill, e.getValue(), quest.name, state, legs, trainedFrom, trainHoursBySkill, opensBySkill);
			}
			if (state.questPoints < quest.questPoints)
			{
				earnPoints(quest.questPoints, closure, remaining, state, legs, needed);
			}
			if (state.combat() < quest.combatLevel)
			{
				trainCombat(quest.combatLevel, quest.name, state, legs, trainedFrom, trainHoursBySkill, opensBySkill);
			}
			if (!ready(quest, state))
			{
				// Quest points no quest can give yet: stop with the quest marked to check.
				legs.add(new Leg(Leg.Kind.CHECK, quest.name, quest.name, null, 0, 0, 0, null, null, false));
				remaining.remove(cheapest);
			}
		}

		// The goal's own levels come last, once every reward toward them is in.
		for (Map.Entry<Skill, Integer> e : ownLevels.entrySet())
		{
			if (state.level(e.getKey()) < e.getValue())
			{
				train(e.getKey(), e.getValue(), null, state, legs, trainedFrom, trainHoursBySkill, opensBySkill);
			}
		}
		if (state.questPoints < qpTarget)
		{
			earnPoints(qpTarget, closure, remaining, state, legs, needed);
		}
		if (state.combat() < combatTarget)
		{
			trainCombat(combatTarget, null, state, legs, trainedFrom, trainHoursBySkill, opensBySkill);
		}

		double questHours = 0;
		double trainHours = 0;
		for (Leg leg : legs)
		{
			if (leg.getKind() == Leg.Kind.TRAIN)
			{
				trainHours += leg.getHours();
			}
			else
			{
				questHours += leg.getHours();
			}
		}
		markCritical(legs);
		List<Gate> gates = new ArrayList<>();
		for (Map.Entry<Skill, Double> e : trainHoursBySkill.entrySet())
		{
			gates.add(new Gate(e.getKey(), trainedFrom.get(e.getKey()), state.level(e.getKey()), e.getValue(),
				opensBySkill.getOrDefault(e.getKey(), Collections.emptyList())));
		}
		gates.sort(Comparator.comparingDouble((Gate g) -> -g.getHours()).thenComparing(g -> g.getSkill().getName()));
		Solution solution = new Solution(goal, plan.getTitle(), legs, questHours, trainHours,
			byHandHours < 0 ? questHours + trainHours : byHandHours, gates, Math.max(0, byHandXp - state.trainedXp));
		solution.trainedXp = state.trainedXp;
		return solution;
	}

	/** Skills some level on the way asks for: only their rewards count as banked. */
	private Set<Skill> neededSkills(List<String> quests, Map<Skill, Integer> ownLevels)
	{
		Set<Skill> needed = new HashSet<>(ownLevels.keySet());
		for (String name : quests)
		{
			for (String skillName : data.quest(name).skills.keySet())
			{
				Skill skill = Planner.skill(skillName);
				if (skill != null)
				{
					needed.add(skill);
				}
			}
		}
		return needed;
	}

	/** A quest that can be done now: one already started first, then the one that opens most, then the quickest. */
	private String firstReady(List<String> remaining, State state)
	{
		String best = null;
		for (String name : remaining)
		{
			QuestDef quest = data.quest(name);
			if (!ready(quest, state))
			{
				continue;
			}
			if (state.account.quest(name) == QuestState.IN_PROGRESS)
			{
				return name;
			}
			if (best == null || better(quest, data.quest(best)))
			{
				best = name;
			}
		}
		return best;
	}

	private boolean better(QuestDef a, QuestDef b)
	{
		int byUnlocks = Integer.compare(data.unlockCount(a.name), data.unlockCount(b.name));
		if (byUnlocks != 0)
		{
			return byUnlocks > 0;
		}
		return Effort.questHours(a) < Effort.questHours(b);
	}

	private boolean prereqsDone(QuestDef quest, State state)
	{
		for (String prereq : quest.prereqs)
		{
			if (!state.questDone(prereq))
			{
				return false;
			}
		}
		return true;
	}

	private boolean ready(QuestDef quest, State state)
	{
		if (state.account.quest(quest.name) == QuestState.IN_PROGRESS)
		{
			return true;
		}
		if (!prereqsDone(quest, state) || state.questPoints < quest.questPoints || state.combat() < quest.combatLevel)
		{
			return false;
		}
		for (Map.Entry<String, Integer> e : quest.skills.entrySet())
		{
			Skill skill = Planner.skill(e.getKey());
			if (skill != null && state.level(skill) < e.getValue() && !state.boostCovers(quest, skill, e.getValue()))
			{
				return false;
			}
		}
		return true;
	}

	/** The training (and, roughly, the quest points) it takes to open a quest whose prerequisites are done. */
	private double openCost(QuestDef quest, State state)
	{
		double cost = 0;
		for (Map.Entry<String, Integer> e : quest.skills.entrySet())
		{
			Skill skill = Planner.skill(e.getKey());
			if (skill != null && !state.boostCovers(quest, skill, e.getValue()))
			{
				cost += state.hoursTo(skill, e.getValue());
			}
		}
		if (state.questPoints < quest.questPoints)
		{
			// About a quest an hour's worth of points per point missing: a rough price that sends the solver to
			// the training first when both would do.
			cost += (quest.questPoints - state.questPoints) * 0.5;
		}
		if (state.combat() < quest.combatLevel)
		{
			cost += (quest.combatLevel - state.combat()) * 2;
		}
		return cost;
	}

	/** Does a quest, banking its rewards, and returns the reward experience that counts toward a needed level. */
	private double doQuest(String name, Leg.Kind kind, State state, List<Leg> legs, Set<Skill> needed)
	{
		QuestDef quest = data.quest(name);
		boolean started = state.account.quest(name) == QuestState.IN_PROGRESS;
		double hours = started ? Effort.questHours(quest) / 2 : Effort.questHours(quest);
		Map<Skill, Double> rewards = new EnumMap<>(Skill.class);
		double banked = 0;
		for (Map.Entry<String, Double> e : state.rewards ? data.questXp(name).entrySet() : Collections.<Map.Entry<String, Double>>emptySet())
		{
			Skill skill = Planner.skill(e.getKey());
			if (skill != null && e.getValue() > 0)
			{
				rewards.put(skill, e.getValue());
				if (needed.contains(skill))
				{
					banked += e.getValue();
				}
			}
		}
		boolean boosted = false;
		for (Map.Entry<String, Integer> e : quest.skills.entrySet())
		{
			Skill skill = Planner.skill(e.getKey());
			boosted |= skill != null && state.level(skill) < e.getValue();
		}
		Leg leg = new Leg(kind, name, name, null, 0, 0, hours, null, rewards, boosted);
		int index = legs.size();
		for (String prereq : quest.prereqs)
		{
			Integer before = state.legOfQuest.get(prereq);
			if (before != null)
			{
				leg.after.add(before);
			}
		}
		for (Map.Entry<String, Integer> e : quest.skills.entrySet())
		{
			Skill skill = Planner.skill(e.getKey());
			Integer trained = skill == null ? null : state.lastTrain.get(skill);
			if (trained != null)
			{
				leg.after.add(trained);
			}
		}
		legs.add(leg);
		state.legOfQuest.put(name, index);
		state.done.add(name);
		state.questPoints += quest.points;
		for (Map.Entry<Skill, Double> e : state.rewards ? rewards.entrySet() : Collections.<Map.Entry<Skill, Double>>emptySet())
		{
			state.xp.merge(e.getKey(), e.getValue(), Double::sum);
			state.rewardsSince.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(index);
		}
		return banked;
	}

	private void train(Skill skill, int target, String opens, State state, List<Leg> legs, Map<Skill, Integer> trainedFrom,
		Map<Skill, Double> hoursBySkill, Map<Skill, List<String>> opensBySkill)
	{
		int from = state.level(skill);
		double hours = state.hoursTo(skill, target);
		Leg leg = new Leg(Leg.Kind.TRAIN, "Train " + skill.getName() + " to " + target, null, skill, from, target, hours,
			opens, null, false);
		Integer previous = state.lastTrain.get(skill);
		if (previous != null)
		{
			leg.after.add(previous);
		}
		List<Integer> rewarded = state.rewardsSince.remove(skill);
		if (rewarded != null)
		{
			leg.after.addAll(rewarded);
		}
		state.lastTrain.put(skill, legs.size());
		legs.add(leg);
		double reached = Math.max(state.xp.get(skill), (double) Experience.getXpForLevel(target));
		state.trainedXp += reached - state.xp.get(skill);
		state.xp.put(skill, reached);
		trainedFrom.putIfAbsent(skill, from);
		hoursBySkill.merge(skill, hours, Double::sum);
		List<String> opened = opensBySkill.computeIfAbsent(skill, k -> new ArrayList<>());
		if (opens != null && !opened.contains(opens))
		{
			opened.add(opens);
		}
	}

	/**
	 * Raises the combat level to a target by training melee: one level at a time of whichever melee skill is quickest
	 * to raise next, worked out first and then trained as one stretch per skill.
	 */
	private void trainCombat(int target, String opens, State state, List<Leg> legs, Map<Skill, Integer> trainedFrom,
		Map<Skill, Double> hoursBySkill, Map<Skill, List<String>> opensBySkill)
	{
		Map<Skill, Double> saved = new EnumMap<>(Skill.class);
		for (Skill skill : COMBAT)
		{
			saved.put(skill, state.xp.get(skill));
		}
		Map<Skill, Integer> goal = new EnumMap<>(Skill.class);
		while (state.combat() < target)
		{
			Skill best = null;
			double bestHours = Double.MAX_VALUE;
			for (Skill skill : COMBAT)
			{
				int level = state.level(skill);
				double h = level >= 99 ? Double.MAX_VALUE : state.hoursTo(skill, level + 1);
				if (h < bestHours)
				{
					bestHours = h;
					best = skill;
				}
			}
			if (best == null)
			{
				break;
			}
			int next = state.level(best) + 1;
			goal.put(best, next);
			state.xp.put(best, (double) Experience.getXpForLevel(next));
		}
		state.xp.putAll(saved);
		for (Skill skill : COMBAT)
		{
			Integer level = goal.get(skill);
			if (level != null)
			{
				train(skill, level, opens, state, legs, trainedFrom, hoursBySkill, opensBySkill);
			}
		}
		if (state.combat() < target)
		{
			// Every melee skill is 99 and it still falls short: the rest is magic, ranged or prayer, not timed here.
			state.combatBase += target - state.combat();
		}
	}

	/**
	 * Quest points from the quickest points per hour that can be done now outside the goal, until the target is met
	 * or nothing more can be done. Returns the reward experience banked toward needed levels.
	 */
	private double earnPoints(int target, Set<String> closure, List<String> remaining, State state, List<Leg> legs,
		Set<Skill> needed)
	{
		double banked = 0;
		while (state.questPoints < target)
		{
			QuestDef best = null;
			for (QuestDef q : data.quests())
			{
				if (q.points <= 0 || q.miniquest || state.questDone(q.name) || remaining.contains(q.name)
					|| closure.contains(q.name) || !ready(q, state))
				{
					continue;
				}
				if (best == null || q.points / Effort.questHours(q) > best.points / Effort.questHours(best))
				{
					best = q;
				}
			}
			if (best == null)
			{
				return banked;
			}
			banked += doQuest(best.name, Leg.Kind.POINTS, state, legs, needed);
		}
		return banked;
	}

	/**
	 * The longest chain of steps that wait on each other, ending at the last step: the steps no reordering can take
	 * off the way. Each step waits on its prerequisite quests and the training that opened it.
	 */
	private static void markCritical(List<Leg> legs)
	{
		int n = legs.size();
		if (n == 0)
		{
			return;
		}
		double[] finish = new double[n];
		int[] from = new int[n];
		int end = 0;
		for (int i = 0; i < n; i++)
		{
			from[i] = -1;
			double start = 0;
			for (int before : legs.get(i).after)
			{
				if (finish[before] > start)
				{
					start = finish[before];
					from[i] = before;
				}
			}
			finish[i] = start + legs.get(i).getHours();
			if (finish[i] >= finish[end])
			{
				end = i;
			}
		}
		for (int i = end; i >= 0; i = from[i])
		{
			legs.get(i).critical = true;
		}
	}

	/** The order as plain lines for the goal board: "Start Regicide", "Train Agility to 56". */
	public static List<String> lines(Solution solution)
	{
		List<String> out = new ArrayList<>();
		for (Leg leg : solution.getLegs())
		{
			switch (leg.getKind())
			{
				case QUEST:
				case POINTS:
					out.add("Start " + leg.getTitle());
					break;
				case TRAIN:
					out.add(leg.getTitle());
					break;
				default:
					out.add("Check " + leg.getTitle());
			}
		}
		return out;
	}

	/** Skill names by hours gated, for the goal board. */
	public static Map<String, Double> gateHours(Solution solution)
	{
		Map<String, Double> out = new LinkedHashMap<>();
		for (Gate gate : solution.getGates())
		{
			out.put(gate.getSkill().getName(), Math.round(gate.getHours() * 2) / 2.0);
		}
		return out;
	}
}
