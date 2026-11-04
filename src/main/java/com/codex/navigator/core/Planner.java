package com.codex.navigator.core;

import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.DiaryDef;
import com.codex.navigator.data.QuestDef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Works out the path to a goal from the bundled requirement data and an account snapshot.
 * Pure logic with no client access, so it is safe to run on any thread.
 */
public final class Planner
{

	private volatile Set<String> capeQuests;
	private final CodexData data;

	public Planner(CodexData data)
	{
		this.data = data;
	}

	public CodexData getData()
	{
		return data;
	}

	private volatile PathSolver solver;

	/** The fastest-path solver over this planner, shared so its last answer is reused. */
	public PathSolver solver()
	{
		PathSolver s = solver;
		if (s == null)
		{
			s = new PathSolver(this);
			solver = s;
		}
		return s;
	}

	public static Skill skill(String name)
	{
		try
		{
			return Skill.valueOf(name.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	public static String formatXp(int xp)
	{
		// Plans are made on the Swing thread and the background lane at once; a shared NumberFormat is not safe there.
		return String.format(Locale.ENGLISH, "%,d", xp);
	}

	public Plan plan(Goal goal, AccountSnapshot account)
	{
		switch (goal.getType())
		{
			case QUEST:
				return planQuest(goal, account);
			case DIARY:
				return planDiary(goal, account);
			case SKILL:
				return planLevel(goal, account);
			case COMBAT:
				return planCombat(goal, account);
			case CHOMPY:
				return planChompy(goal, account);
			case CHARTING:
				return planCharting(goal, account);
			case QUEST_CAPE:
			default:
				return planQuestCape(goal, account);
		}
	}

	private Plan planQuest(Goal goal, AccountSnapshot account)
	{
		QuestDef quest = data.quest(goal.getName());
		if (quest == null)
		{
			return unknown(goal);
		}
		Set<String> closure = new LinkedHashSet<>();
		collect(quest.name, closure);
		String subtitle = (quest.miniquest ? "Miniquest" : quest.difficulty == null ? "Quest" : quest.difficulty)
			+ (quest.points > 0 ? " · " + quest.points + "\u00a0QP" : "");
		return build(goal, quest.name, subtitle, account.questDone(quest.name), closure,
			new EnumMap<>(Skill.class), 0, 0, false, account);
	}

	private Plan planDiary(Goal goal, AccountSnapshot account)
	{
		DiaryDef diary = data.diary(goal.getName());
		if (diary == null)
		{
			return unknown(goal);
		}
		Set<String> closure = new LinkedHashSet<>();
		for (String q : diary.quests)
		{
			collect(q, closure);
		}
		if (diary.allQuests)
		{
			for (QuestDef q : data.questCapeQuests())
			{
				collect(q.name, closure);
			}
		}
		Map<Skill, Integer> own = new EnumMap<>(Skill.class);
		addSkills(own, diary.skills);
		return build(goal, diary.name + " Diary", "Achievement diary", account.diaryDone(diary.name),
			closure, own, diary.questPoints, diary.combatLevel, true, account);
	}

	/**
	 * A combat achievement tier's reward: reached once the account's points meet the game's threshold for it. Tasks are
	 * done in game, so the plan has no path and no time estimate, only the points still to earn and the nearest task.
	 */
	private Plan planCombat(Goal goal, AccountSnapshot account)
	{
		int tier = CombatTasks.tierOf(goal.getName());
		if (tier == 0)
		{
			return unknown(goal);
		}
		CombatTasks tasks = account.combatTasks();
		int need = tasks.threshold(tier);
		int points = account.getCombatAchievementPoints();
		String title = CombatTasks.tierName(tier) + " combat achievements";
		String subtitle = "Combat achievements · " + String.format(java.util.Locale.ENGLISH, "%,d", need) + "\u00a0pts";
		boolean complete = account.isLoggedIn() && points >= need;
		double progress = complete ? 1 : need <= 0 ? 0 : Math.min(0.99, points / (double) need);
		Step next = null;
		if (!complete)
		{
			next = combatStep(tasks, tier, need - points, account.isLoggedIn());
		}
		return new Plan(goal, title, subtitle, complete, progress, new ArrayList<>(), new ArrayList<>(), next, 0, 0);
	}

	/**
	 * A chompy bird hat: Big Chompy Bird Hunting first (its own path, as a quest goal plans it), then the kills, timed
	 * at this player's own kill rate once one has been learned. Kills are only known once the ogre bow has been checked.
	 */
	private Plan planChompy(Goal goal, AccountSnapshot account)
	{
		ChompyHunt.Hat hat = ChompyHunt.hat(goal.getName());
		if (hat == null)
		{
			return unknown(goal);
		}
		ChompyHunt.View hunt = account.chompy();
		String title = hat.getName() + " hat";
		String subtitle = "Chompy bird hat · " + formatXp(hat.getKills()) + "\u00a0kills";
		boolean complete = hunt.isKnown() && hunt.getKills() >= hat.getKills();
		QuestDef quest = data.quest(ChompyHunt.QUEST);
		if (!complete && quest != null && account.knowsQuest(quest.name)
			&& !account.questDone(quest.name) && hunt.getKills() <= 0)
		{
			// The quest stands in the way: plan it as its own goal would be planned, then the kills on top.
			Set<String> closure = new LinkedHashSet<>();
			collect(quest.name, closure);
			Plan questPlan = build(goal, title, subtitle, false, closure, new EnumMap<>(Skill.class), 0, 0, false, account);
			return new Plan(goal, title, subtitle, false, questPlan.getProgress() * 0.25, questPlan.getPath(),
				questPlan.getSkills(), questPlan.getNext(), questPlan.getQuestsDone(), questPlan.getQuestsTotal(),
				questPlan.getQuestPointTarget(), questPlan.getCombatTarget(), questPlan.getHoursLeft());
		}
		double progress = complete ? 1 : !hunt.isKnown() ? 0 : Math.min(0.99, hunt.getKills() / (double) hat.getKills());
		Step next = complete ? null : chompyStep(hunt, hat);
		return new Plan(goal, title, subtitle, complete, progress, new ArrayList<>(), new ArrayList<>(), next, 0, 0, 0, 0,
			complete ? 0 : hunt.hoursTo(hat.getKills()));
	}

	/** "Kill 120 more chompy birds", with the time at this player's rate, or how to read the count. */
	static Step chompyStep(ChompyHunt.View hunt, ChompyHunt.Hat hat)
	{
		if (!hunt.isKnown())
		{
			return new Step(Step.Kind.CHOMPY, Step.Status.UNKNOWN, "Check kills on your ogre bow",
				"The bow's count is the game's; the Codex adds each kill after it"
					+ (hunt.getUnsynced() > 0 ? " (" + hunt.getUnsynced() + " seen so far)" : ""), null, null);
		}
		int left = hat.getKills() - hunt.getKills();
		String detail = hunt.getPerHour() > 0
			? Effort.format(left / hunt.getPerHour()).substring(1) + " at your " + Math.round(hunt.getPerHour()) + " an hour"
			: "Hunt a while and the Codex learns your kill rate";
		return new Step(Step.Kind.CHOMPY, Step.Status.READY, "Kill " + formatXp(left) + " more chompy "
			+ (left == 1 ? "bird" : "birds") + " for " + hat.getName(), detail, null, null);
	}

	/**
	 * Every sea chart: the charts left as the game counts them, the Sailing level the last ones need, and the nearest
	 * chart this player can make now as the next step. Timed at this player's own charts an hour once one is learned,
	 * plus the Sailing training still in the way at steady rates.
	 */
	private Plan planCharting(Goal goal, AccountSnapshot account)
	{
		SeaCharting.View view = account.charting();
		String title = "Sea charting";
		String subtitle = "Sailing · " + SeaCharting.TOTAL + "\u00a0charts";
		boolean known = account.isLoggedIn() && view.isKnown();
		int done = known ? view.doneCount() : 0;
		boolean complete = known && done >= SeaCharting.TOTAL;
		List<SkillGap> skills = new ArrayList<>();
		double hours = 0;
		if (account.isLoggedIn() && !complete)
		{
			SkillGap gap = new SkillGap(Skill.SAILING, account.level(Skill.SAILING), SeaCharting.FULL_LEVEL,
				account.xp(Skill.SAILING), account.pace(Skill.SAILING), false);
			if (!gap.isMet())
			{
				skills.add(gap);
				hours += gap.hours();
			}
			hours += view.hoursLeft();
		}
		double progress = complete ? 1 : Math.min(0.99, done / (double) SeaCharting.TOTAL);
		Step next = complete ? null : chartingStep(view, account.isLoggedIn() ? account.level(Skill.SAILING) : 0, known);
		return new Plan(goal, title, subtitle, complete, progress, new ArrayList<>(), skills, next, 0, 0, 0, 0,
			view.getPerHour() > 0 ? hours : 0);
	}

	/** "Chart: <task>" for the nearest chart open at this level, or the level that opens the next one. */
	static Step chartingStep(SeaCharting.View view, int level, boolean known)
	{
		if (!known)
		{
			return new Step(Step.Kind.CHARTING, Step.Status.UNKNOWN, "Log in to read your sea charts",
				"The game keeps which charts are done", null, null);
		}
		List<SeaCharting.Near> near = view.nearest(level, 1);
		if (!near.isEmpty())
		{
			SeaCharting.Chart c = near.get(0).getChart();
			return new Step(Step.Kind.CHARTING, Step.Status.READY, c.getTask(),
				c.getKind().getLabel() + " chart in " + c.getSea().getName(), null, null);
		}
		int at = view.nextLevel(level);
		if (at > 0)
		{
			return new Step(Step.Kind.TRAIN, Step.Status.BLOCKED, "Train Sailing to " + at,
				"For the next charts", null, Skill.SAILING);
		}
		return new Step(Step.Kind.CHARTING, Step.Status.READY, "Every chart is made", "Claim the bonus", null, null);
	}

	/** "Earn 37 more points", with the nearest task when the game's list is known. */
	static Step combatStep(CombatTasks tasks, int tier, int toGo, boolean loggedIn)
	{
		if (!loggedIn)
		{
			return new Step(Step.Kind.COMBAT_TASKS, Step.Status.UNKNOWN, "Log in to read your combat achievements",
				null, null, null);
		}
		String detail;
		List<CombatTasks.Near> nearest = tasks.nearest(0, 1);
		if (!nearest.isEmpty())
		{
			CombatTasks.Task t = nearest.get(0).getTask();
			boolean named = !t.hasMonster() || t.getName().contains(t.getMonster());
			detail = "Nearest: " + t.getName() + (named ? "" : " at " + t.getMonster()) + ", "
				+ t.getTierName() + " · " + nearest.get(0).getWhy();
		}
		else
		{
			detail = "Your Combat Achievements window lists the tasks";
		}
		return new Step(Step.Kind.COMBAT_TASKS, Step.Status.READY, "Earn " + toGo + (toGo == 1 ? " more point" : " more points")
			+ " for " + CombatTasks.tierName(tier), detail, null, null);
	}

	/**
	 * A level in one skill: no quests on the way, only the training. The subtitle says what reaching it opens,
	 * which is usually why a player sets one.
	 */
	private Plan planLevel(Goal goal, AccountSnapshot account)
	{
		Skill skill = goal.getSkill();
		int level = goal.getLevel();
		if (skill == null || level == 0)
		{
			return unknown(goal);
		}
		Map<Skill, Integer> targets = new EnumMap<>(Skill.class);
		targets.put(skill, level);
		boolean complete = account.isLoggedIn() && account.level(skill) >= level;
		// Miniquests are left out of the count, as the quest counts elsewhere leave them out.
		int opens = account.isLoggedIn() ? opens(skill, level, account, false).size() : 0;
		String subtitle = "Level goal" + (opens > 0 ? " · opens " + opens + (opens == 1 ? " quest" : " quests") : "");
		return build(goal, level + " " + skill.getName(), subtitle, complete, new LinkedHashSet<>(), targets, 0, 0,
			false, account);
	}

	/**
	 * The unfinished quests (miniquests too, when asked) whose own requirement in this skill is above the current
	 * level and no more than {@code level}: what training to that level opens. Highest requirement last, so the
	 * list reads in the order the levels come. Members' quests are left out on a free-to-play account.
	 */
	public List<QuestDef> opens(Skill skill, int level, AccountSnapshot account, boolean includeMiniquests)
	{
		int current = account.level(skill);
		List<QuestDef> out = new ArrayList<>();
		for (QuestDef q : data.quests())
		{
			Integer need = need(q.skills, skill);
			if (need == null || need <= current || need > level || account.questDone(q.name)
				|| q.miniquest && !includeMiniquests || !account.isMembers() && account.isLoggedIn() && !data.isFree(q))
			{
				continue;
			}
			out.add(q);
		}
		out.sort(Comparator.comparingInt((QuestDef q) -> need(q.skills, skill)).thenComparing(q -> q.name));
		return out;
	}

	/** The unfinished diary tiers whose requirement in this skill is above the current level and no more than {@code level}. */
	public List<DiaryDef> diariesOpened(Skill skill, int level, AccountSnapshot account)
	{
		int current = account.level(skill);
		List<DiaryDef> out = new ArrayList<>();
		for (DiaryDef d : data.diaries())
		{
			Integer need = need(d.skills, skill);
			if (need != null && need > current && need <= level && !account.diaryDone(d.name))
			{
				out.add(d);
			}
		}
		out.sort(Comparator.comparingInt((DiaryDef d) -> need(d.skills, skill)).thenComparing(d -> d.name));
		return out;
	}

	/** A requirement for this skill in a requirement map keyed by skill name, or null. */
	public static Integer need(Map<String, Integer> skills, Skill skill)
	{
		for (Map.Entry<String, Integer> e : skills.entrySet())
		{
			if (skill == skill(e.getKey()))
			{
				return e.getValue();
			}
		}
		return null;
	}

	/**
	 * Levels worth offering as a goal in this skill, above the current level, at most {@code max} of them in
	 * order: the next levels that open quests or diary tiers, the next round ten, and 99.
	 */
	public List<Integer> suggestedLevels(Skill skill, AccountSnapshot account, boolean includeMiniquests, int max)
	{
		int current = Math.max(1, account.level(skill));
		java.util.TreeSet<Integer> unlocks = new java.util.TreeSet<>();
		for (QuestDef q : opens(skill, 99, account, includeMiniquests))
		{
			unlocks.add(need(q.skills, skill));
		}
		for (DiaryDef d : diariesOpened(skill, 99, account))
		{
			unlocks.add(need(d.skills, skill));
		}
		java.util.TreeSet<Integer> out = new java.util.TreeSet<>();
		int round = Math.min(99, (current / 10 + 1) * 10);
		if (round > current)
		{
			out.add(round);
		}
		if (current < 99)
		{
			out.add(99);
		}
		for (int level : unlocks)
		{
			if (out.size() >= max)
			{
				break;
			}
			out.add(level);
		}
		// Keep the nearest levels and 99 when there are too many.
		List<Integer> list = new ArrayList<>(out);
		while (list.size() > max && list.size() > 1)
		{
			list.remove(list.size() - 2);
		}
		return list;
	}

	/**
	 * Every quest the quest cape needs, with the miniquests those quests need first, prerequisites first. The data
	 * never changes, so it is worked out once.
	 */
	public Set<String> capeQuests()
	{
		Set<String> closure = capeQuests;
		if (closure == null)
		{
			closure = new LinkedHashSet<>();
			for (QuestDef q : data.questCapeQuests())
			{
				collect(q.name, closure);
			}
			closure = Collections.unmodifiableSet(closure);
			capeQuests = closure;
		}
		return closure;
	}

	private Plan planQuestCape(Goal goal, AccountSnapshot account)
	{
		Set<String> closure = capeQuests();
		boolean complete = true;
		for (QuestDef q : data.questCapeQuests())
		{
			complete &= account.questDone(q.name);
		}
		return build(goal, "Quest Point Cape", "Every quest in Gielinor", complete, closure,
			new EnumMap<>(Skill.class), 0, 0, false, account);
	}

	private Plan unknown(Goal goal)
	{
		Step check = new Step(Step.Kind.CHECK, Step.Status.UNKNOWN, goal.getName(), "Not in the Codex data yet", null, null);
		List<Step> path = new ArrayList<>();
		path.add(check);
		return new Plan(goal, goal.getName(), "Unknown goal", false, 0, path, new ArrayList<>(), check, 0, 0);
	}

	/** Adds a quest and everything it depends on, prerequisites first. */
	private void collect(String name, Set<String> closure)
	{
		collect(name, closure, new HashSet<>());
	}

	private void collect(String name, Set<String> closure, Set<String> visiting)
	{
		// The visiting set stops a cycle in the data from recursing forever.
		if (closure.contains(name) || !visiting.add(name))
		{
			return;
		}
		QuestDef quest = data.quest(name);
		if (quest != null)
		{
			for (String prereq : quest.prereqs)
			{
				collect(prereq, closure, visiting);
			}
		}
		closure.add(name);
	}

	private Plan build(Goal goal, String title, String subtitle, boolean complete, Set<String> closure,
		Map<Skill, Integer> targets, int questPointTarget, int combatTarget, boolean diaryTasks, AccountSnapshot account)
	{
		List<Step> path = new ArrayList<>();
		int questsDone = 0;
		int questsTotal = 0;
		double score = 0;
		int items = 0;
		double hours = 0;
		// A level counts as boostable only if every requirement above the current level allows a boost and the
		// everyday boost reaches it. A diary's own levels are never marked, so they always need training.
		Map<Skill, Boolean> boostable = new EnumMap<>(Skill.class);
		for (Map.Entry<Skill, Integer> e : targets.entrySet())
		{
			boostable.put(e.getKey(), account.level(e.getKey()) >= e.getValue());
		}

		for (String name : closure)
		{
			QuestDef quest = data.quest(name);
			if (quest == null)
			{
				path.add(new Step(Step.Kind.CHECK, Step.Status.UNKNOWN, name, "Check this one in game", null, null));
				continue;
			}
			questsTotal++;
			items++;
			Step step = questStep(quest, account);
			path.add(step);
			if (step.getStatus() == Step.Status.DONE)
			{
				questsDone++;
				score += 1;
				continue;
			}
			if (step.getStatus() == Step.Status.IN_PROGRESS)
			{
				score += 0.5;
				hours += Effort.questHours(quest) / 2;
			}
			else
			{
				hours += Effort.questHours(quest);
			}
			addSkills(targets, quest.skills);
			markBoostable(boostable, quest, account);
			questPointTarget = Math.max(questPointTarget, quest.questPoints);
			combatTarget = Math.max(combatTarget, quest.combatLevel);
		}

		List<SkillGap> skills = new ArrayList<>();
		for (Map.Entry<Skill, Integer> e : targets.entrySet())
		{
			SkillGap gap = new SkillGap(e.getKey(), account.level(e.getKey()), e.getValue(), account.xp(e.getKey()),
				account.pace(e.getKey()), boostable.getOrDefault(e.getKey(), false));
			skills.add(gap);
			score += gap.fraction();
			// A level a pie or potion covers costs no training time.
			hours += gap.isBoostable() ? 0 : gap.hours();
			items++;
		}
		// Unmet levels quickest first, so the next step is the level that frees the path soonest; levels a boost
		// covers go after the ones that need training.
		skills.sort(Comparator.comparing(SkillGap::isMet).thenComparing(SkillGap::isBoostable)
			.thenComparingDouble(SkillGap::hours).thenComparingInt(SkillGap::xpToGo));

		if (questPointTarget > 0)
		{
			score += Math.min(1, account.getQuestPoints() / (double) questPointTarget);
			items++;
		}
		if (combatTarget > 0)
		{
			score += Math.min(1, account.getCombatLevel() / (double) combatTarget);
			items++;
		}

		double progress = complete ? 1 : items == 0 ? 0 : Math.min(0.99, score / items);
		Step next = complete ? null : next(path, skills, questPointTarget, combatTarget, diaryTasks, title, account);
		return new Plan(goal, title, subtitle, complete, progress, path, skills, next, questsDone, questsTotal,
			questPointTarget, combatTarget, complete ? 0 : hours);
	}

	private Step next(List<Step> path, List<SkillGap> skills, int questPointTarget, int combatTarget,
		boolean diaryTasks, String title, AccountSnapshot account)
	{
		for (Step step : path)
		{
			if (step.getStatus() == Step.Status.IN_PROGRESS)
			{
				return step;
			}
		}
		// Of the quests that can be started now, the quickest: every one is needed, and a short one is progress sooner.
		Step quickest = null;
		double quickestHours = Double.MAX_VALUE;
		for (Step step : path)
		{
			if (step.getStatus() == Step.Status.READY && step.getKind() == Step.Kind.QUEST)
			{
				QuestDef quest = data.quest(step.getQuest());
				double hours = quest == null ? Double.MAX_VALUE - 1 : Effort.questHours(quest);
				if (hours < quickestHours)
				{
					quickest = step;
					quickestHours = hours;
				}
			}
		}
		if (quickest != null)
		{
			return quickest;
		}
		for (SkillGap gap : skills)
		{
			if (!gap.isMet() && !gap.isBoostable())
			{
				return trainStep(gap);
			}
		}
		if (account.getQuestPoints() < questPointTarget)
		{
			int missing = questPointTarget - account.getQuestPoints();
			return new Step(Step.Kind.QUEST_POINTS, Step.Status.READY, "Earn " + missing + " more quest points",
				account.getQuestPoints() + " of " + questPointTarget + " QP", null, null);
		}
		if (account.getCombatLevel() < combatTarget)
		{
			return new Step(Step.Kind.COMBAT, Step.Status.READY, "Reach combat level " + combatTarget,
				"Currently " + account.getCombatLevel(), null, null);
		}
		for (Step step : path)
		{
			if (step.getStatus() == Step.Status.UNKNOWN)
			{
				return step;
			}
		}
		if (diaryTasks)
		{
			return new Step(Step.Kind.DIARY_TASKS, Step.Status.READY, "Finish the " + title + " tasks",
				"Every listed requirement is met", null, null);
		}
		return null;
	}

	public static Step trainStep(SkillGap gap)
	{
		String skillName = gap.getSkill().getName();
		return new Step(Step.Kind.TRAIN, Step.Status.READY, "Train " + skillName + " to " + gap.getTarget(),
			formatXp(gap.xpToGo()) + " xp to go", null, gap.getSkill());
	}

	public Step questStep(QuestDef quest, AccountSnapshot account)
	{
		QuestState state = account.quest(quest.name);
		if (state == QuestState.FINISHED)
		{
			return new Step(Step.Kind.QUEST, Step.Status.DONE, quest.name, "Complete", quest.name, null);
		}
		if (state == QuestState.IN_PROGRESS)
		{
			return new Step(Step.Kind.QUEST, Step.Status.IN_PROGRESS, quest.name, "In progress", quest.name, null);
		}
		List<String> blockers = blockers(quest, account);
		if (blockers.isEmpty())
		{
			return new Step(Step.Kind.QUEST, Step.Status.READY, quest.name, "Ready to start", quest.name, null);
		}
		List<SkillGap> boosts = boostGaps(quest, account);
		if (boosts != null)
		{
			return new Step(Step.Kind.QUEST, Step.Status.READY, quest.name, boostDetail(boosts), quest.name, null);
		}
		String detail = "Needs " + blockers.get(0) + (blockers.size() > 1 ? " +" + (blockers.size() - 1) + " more" : "");
		return new Step(Step.Kind.QUEST, Step.Status.BLOCKED, quest.name, detail, quest.name, null);
	}

	/** Everything stopping a quest from being started right now, prerequisites first. */
	public List<String> blockers(QuestDef quest, AccountSnapshot account)
	{
		List<String> blockers = new ArrayList<>();
		for (String prereq : quest.prereqs)
		{
			if (!account.questDone(prereq))
			{
				blockers.add(prereq);
			}
		}
		for (Map.Entry<String, Integer> e : quest.skills.entrySet())
		{
			Skill skill = skill(e.getKey());
			if (skill != null && account.level(skill) < e.getValue())
			{
				blockers.add(skill.getName() + " " + e.getValue());
			}
		}
		if (account.getQuestPoints() < quest.questPoints)
		{
			blockers.add(quest.questPoints + " quest points");
		}
		if (account.getCombatLevel() < quest.combatLevel)
		{
			blockers.add("combat " + quest.combatLevel);
		}
		return blockers;
	}

	/**
	 * The levels to boost for a quest held back only by levels that its everyday boosts cover, such as Regicide
	 * at 52 Agility (a summer pie gives +5). Null when anything else holds it back, when a level may not be
	 * boosted or the boost falls short, and when nothing holds it back at all.
	 */
	public List<SkillGap> boostGaps(QuestDef quest, AccountSnapshot account)
	{
		List<SkillGap> gaps = levelGapsOnly(quest, account);
		if (gaps == null || gaps.isEmpty())
		{
			return null;
		}
		List<SkillGap> out = new ArrayList<>();
		for (SkillGap gap : gaps)
		{
			if (!data.boostable(quest.name, gap.getSkill().getName(), gap.getTarget())
				|| !Boosts.reaches(gap.getSkill(), gap.getCurrent(), gap.getTarget(), account))
			{
				return null;
			}
			out.add(new SkillGap(gap.getSkill(), gap.getCurrent(), gap.getTarget(), account.xp(gap.getSkill()),
				gap.getPace(), true));
		}
		return out;
	}

	/** "Boost Agility 52 to 56 with a summer pie (+5)", with "+1 more" when there are several. */
	public static String boostDetail(List<SkillGap> gaps)
	{
		SkillGap first = gaps.get(0);
		String text = Boosts.describe(first.getSkill(), first.getCurrent(), first.getTarget());
		return gaps.size() > 1 ? text + " +" + (gaps.size() - 1) + " more" : text;
	}

	/** Clears a skill's boostable mark when this quest asks for more than the current level without allowing a boost. */
	private void markBoostable(Map<Skill, Boolean> boostable, QuestDef quest, AccountSnapshot account)
	{
		for (Map.Entry<String, Integer> e : quest.skills.entrySet())
		{
			Skill skill = skill(e.getKey());
			if (skill == null)
			{
				continue;
			}
			int current = account.level(skill);
			int need = e.getValue();
			boolean ok = current >= need
				|| data.boostable(quest.name, skill.getName(), need) && Boosts.reaches(skill, current, need, account);
			boostable.merge(skill, ok, Boolean::logicalAnd);
		}
	}

	/** Level gaps for a quest whose prerequisite quests are all done, or null if a quest is still missing. */
	public List<SkillGap> levelGapsOnly(QuestDef quest, AccountSnapshot account)
	{
		for (String prereq : quest.prereqs)
		{
			if (!account.questDone(prereq))
			{
				return null;
			}
		}
		if (account.getQuestPoints() < quest.questPoints || account.getCombatLevel() < quest.combatLevel)
		{
			return null;
		}
		List<SkillGap> gaps = new ArrayList<>();
		for (Map.Entry<String, Integer> e : quest.skills.entrySet())
		{
			Skill skill = skill(e.getKey());
			if (skill != null && account.level(skill) < e.getValue())
			{
				gaps.add(new SkillGap(skill, account.level(skill), e.getValue(), account.xp(skill), account.pace(skill), false));
			}
		}
		return gaps;
	}

	private static void addSkills(Map<Skill, Integer> targets, Map<String, Integer> skills)
	{
		for (Map.Entry<String, Integer> e : skills.entrySet())
		{
			Skill skill = skill(e.getKey());
			if (skill != null)
			{
				targets.merge(skill, e.getValue(), Math::max);
			}
		}
	}

	public static int countDone(Collection<QuestDef> quests, AccountSnapshot account)
	{
		int done = 0;
		for (QuestDef q : quests)
		{
			if (account.questDone(q.name))
			{
				done++;
			}
		}
		return done;
	}
}
