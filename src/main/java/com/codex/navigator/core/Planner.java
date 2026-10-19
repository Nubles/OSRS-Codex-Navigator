package com.codex.navigator.core;

import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.DiaryDef;
import com.codex.navigator.data.QuestDef;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
	private static final NumberFormat XP_FORMAT = NumberFormat.getIntegerInstance(Locale.ENGLISH);

	private final CodexData data;

	public Planner(CodexData data)
	{
		this.data = data;
	}

	public CodexData getData()
	{
		return data;
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
		return XP_FORMAT.format(xp);
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

	private Plan planQuestCape(Goal goal, AccountSnapshot account)
	{
		Set<String> closure = new LinkedHashSet<>();
		for (QuestDef q : data.questCapeQuests())
		{
			collect(q.name, closure);
		}
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
