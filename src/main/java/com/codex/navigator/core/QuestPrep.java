package com.codex.navigator.core;

import com.codex.navigator.data.QuestDef;
import com.codex.navigator.data.QuestItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Getting ready for one quest: what still blocks it and how long each block takes, the levels a pie or potion
 * covers on the day, and what to bring (from the quest's OSRS Wiki page), each item checked against what the
 * account carries and its bank. Nothing is guessed: a kind of thing ("Food") or an unseen bank says so.
 */
public final class QuestPrep
{
	/** Where an item to bring stands. */
	public enum Have
	{
		/** In the pack or worn, enough of it. */
		CARRIED,
		/** Enough between the pack and the bank. */
		BANKED,
		/** Some, but not enough, with the bank known. */
		SHORT,
		/** None, with the bank known. */
		MISSING,
		/** Not enough here, but the quest itself hands it out or it can be got along the way. */
		IN_QUEST,
		/** Not enough carried, and the bank has not been seen. */
		UNSEEN,
		/** A kind of thing ("Food", "Any bow") that cannot be checked by item. */
		UNCHECKED
	}

	/** One item and where it stands. */
	public static final class Line
	{
		private final QuestItems.Item item;
		private final Have have;
		private final long carried;
		private final long banked;

		Line(QuestItems.Item item, Have have, long carried, long banked)
		{
			this.item = item;
			this.have = have;
			this.carried = carried;
			this.banked = banked;
		}

		public QuestItems.Item getItem()
		{
			return item;
		}

		public Have getHave()
		{
			return have;
		}

		public long getCarried()
		{
			return carried;
		}

		public long getBanked()
		{
			return banked;
		}

		/** True when nothing more needs doing for this item before setting off. */
		public boolean settled()
		{
			return have == Have.CARRIED || have == Have.BANKED || have == Have.IN_QUEST || have == Have.UNCHECKED;
		}
	}

	/** One thing still holding the quest back, with about how long it takes. */
	public static final class Blocker
	{
		public enum Kind
		{
			QUEST,
			SKILL,
			QUEST_POINTS,
			COMBAT
		}

		private final Kind kind;
		private final String title;
		private final String detail;
		private final double hours;
		private final String quest;
		private final Skill skill;

		Blocker(Kind kind, String title, String detail, double hours, String quest, Skill skill)
		{
			this.kind = kind;
			this.title = title;
			this.detail = detail;
			this.hours = hours;
			this.quest = quest;
			this.skill = skill;
		}

		public Kind getKind()
		{
			return kind;
		}

		/** "Plague City", "Agility 56", "32 quest points". */
		public String getTitle()
		{
			return title;
		}

		/** What is involved, such as "train to 51, then a summer pie"; may be null. */
		public String getDetail()
		{
			return detail;
		}

		/** About how many hours it takes; 0 when it cannot be timed (quest points, combat level). */
		public double getHours()
		{
			return hours;
		}

		/** The quest to open, for a quest blocker. */
		public String getQuest()
		{
			return quest;
		}

		public Skill getSkill()
		{
			return skill;
		}
	}

	private final String quest;
	private final QuestState state;
	private final List<Blocker> blockers;
	private final List<SkillGap> boosts;
	private final List<Line> items;
	private final List<Line> recommended;
	private final boolean listed;
	private final Holdings holdings;

	private QuestPrep(String quest, QuestState state, List<Blocker> blockers, List<SkillGap> boosts, List<Line> items,
		List<Line> recommended, boolean listed, Holdings holdings)
	{
		this.quest = quest;
		this.state = state;
		this.blockers = Collections.unmodifiableList(blockers);
		this.boosts = Collections.unmodifiableList(boosts);
		this.items = Collections.unmodifiableList(items);
		this.recommended = Collections.unmodifiableList(recommended);
		this.listed = listed;
		this.holdings = holdings;
	}

	/**
	 * The quest a plan's prep card is about: the one its next step names, or else the first quest on its path not yet
	 * done (when the next step is training for it). Null for a finished goal or a path with no quest left.
	 */
	public static String nextQuest(Plan plan)
	{
		if (plan == null || plan.isComplete())
		{
			return null;
		}
		Step next = plan.getNext();
		if (next != null && next.getKind() == Step.Kind.QUEST && next.getQuest() != null)
		{
			return next.getQuest();
		}
		for (Step step : plan.getPath())
		{
			if (step.getKind() == Step.Kind.QUEST && step.getQuest() != null && step.getStatus() != Step.Status.DONE)
			{
				return step.getQuest();
			}
		}
		return null;
	}

	/** Works out the prep for a quest; null when the quest is unknown or already finished. */
	public static QuestPrep of(Planner planner, String questName, AccountSnapshot account, Holdings holdings)
	{
		QuestDef quest = questName == null ? null : planner.getData().quest(questName);
		if (quest == null || account.questDone(quest.name))
		{
			return null;
		}
		Holdings have = holdings == null ? Holdings.none() : holdings;
		List<Blocker> blockers = new ArrayList<>();
		List<SkillGap> boosts = new ArrayList<>();
		for (String prereq : quest.prereqs)
		{
			if (!account.questDone(prereq))
			{
				Plan plan = planner.plan(Goal.quest(prereq), account);
				String detail = account.quest(prereq) == QuestState.IN_PROGRESS ? "in progress" : null;
				blockers.add(new Blocker(Blocker.Kind.QUEST, prereq, detail, plan.getHoursLeft(), prereq, null));
			}
		}
		for (Map.Entry<String, Integer> e : quest.skills.entrySet())
		{
			Skill skill = Planner.skill(e.getKey());
			int need = e.getValue();
			if (skill == null || account.level(skill) >= need)
			{
				continue;
			}
			int current = account.level(skill);
			boolean mayBoost = planner.getData().boostable(quest.name, skill.getName(), need);
			if (mayBoost && Boosts.reaches(skill, current, need, account))
			{
				boosts.add(new SkillGap(skill, current, need, account.xp(skill), account.pace(skill), true));
				continue;
			}
			int trainTo = mayBoost ? boostFrom(skill, current, need, account) : need;
			SkillGap gap = new SkillGap(skill, current, trainTo, account.xp(skill), account.pace(skill), false);
			String detail = trainTo < need ? "train to " + trainTo + ", then " + Boosts.of(skill).getItem() : null;
			blockers.add(new Blocker(Blocker.Kind.SKILL, skill.getName() + " " + need, detail, gap.hours(), null, skill));
		}
		if (account.getQuestPoints() < quest.questPoints)
		{
			blockers.add(new Blocker(Blocker.Kind.QUEST_POINTS, quest.questPoints + " quest points",
				(quest.questPoints - account.getQuestPoints()) + " more", 0, null, null));
		}
		if (account.getCombatLevel() < quest.combatLevel)
		{
			blockers.add(new Blocker(Blocker.Kind.COMBAT, "Combat level " + quest.combatLevel, null, 0, null, null));
		}

		QuestItems.Entry entry = planner.getData().questItems().of(quest.name);
		List<Line> items = new ArrayList<>();
		List<Line> recommended = new ArrayList<>();
		if (entry != null)
		{
			for (QuestItems.Item item : entry.items)
			{
				items.add(check(item, have));
			}
			for (QuestItems.Item item : entry.recommended)
			{
				recommended.add(check(item, have));
			}
		}
		return new QuestPrep(quest.name, account.quest(quest.name), blockers, boosts, items, recommended, entry != null,
			have);
	}

	/**
	 * The lowest level from which the skill's everyday boost reaches {@code need}, for a level the quest lets you
	 * boost but the boost does not yet cover; {@code need} itself when there is no boost this account can get.
	 */
	static int boostFrom(Skill skill, int current, int need, AccountSnapshot account)
	{
		Boosts.Boost boost = Boosts.of(skill);
		if (boost == null || !boost.obtainable(account))
		{
			return need;
		}
		for (int level = Math.max(1, current); level < need; level++)
		{
			if (level + boost.amount(level) >= need)
			{
				return level;
			}
		}
		return need;
	}

	/** Where one item stands, from what is carried and banked. */
	static Line check(QuestItems.Item item, Holdings have)
	{
		if (!item.checkable())
		{
			return new Line(item, Have.UNCHECKED, 0, 0);
		}
		long carried = have.carried(item.ids);
		long banked = have.banked(item.ids);
		Have state;
		if (carried >= item.qty)
		{
			state = Have.CARRIED;
		}
		else if (have.bankKnown() && carried + banked >= item.qty)
		{
			state = Have.BANKED;
		}
		else if (item.duringQuest)
		{
			state = Have.IN_QUEST;
		}
		else if (!have.bankKnown())
		{
			state = Have.UNSEEN;
		}
		else
		{
			state = carried + banked > 0 ? Have.SHORT : Have.MISSING;
		}
		return new Line(item, state, carried, banked);
	}

	public String getQuest()
	{
		return quest;
	}

	/** The quest's state in the game: not started, or in progress (some items may already be handed in). */
	public QuestState getState()
	{
		return state;
	}

	/** True when nothing holds the quest back (levels a boost covers aside). */
	public boolean isReady()
	{
		return blockers.isEmpty();
	}

	/** Prerequisite quests first, then levels, quest points and combat. */
	public List<Blocker> getBlockers()
	{
		return blockers;
	}

	/** About how long until every blocker is cleared, or 0 when ready. */
	public double getHoursToReady()
	{
		double total = 0;
		for (Blocker b : blockers)
		{
			total += b.getHours();
		}
		return total;
	}

	/** Levels the quest lets you boost that the everyday boost covers from where the account is. */
	public List<SkillGap> getBoosts()
	{
		return boosts;
	}

	/** The wiki's "Items required", each checked. Empty when the quest needs none or is not listed. */
	public List<Line> getItems()
	{
		return items;
	}

	/** The wiki's "Recommended" items, each checked where it can be. */
	public List<Line> getRecommended()
	{
		return recommended;
	}

	/** False when this quest's item list is not bundled yet: the card then says so instead of "nothing to bring". */
	public boolean isListed()
	{
		return listed;
	}

	public Holdings getHoldings()
	{
		return holdings;
	}

	/** How many required items are in one of these states. */
	public int count(Have... states)
	{
		int n = 0;
		for (Line line : items)
		{
			for (Have h : states)
			{
				if (line.getHave() == h)
				{
					n++;
					break;
				}
			}
		}
		return n;
	}

	/** Required items still to fetch or buy: short or missing with the bank known, or not carried with it unseen. */
	public int toGet()
	{
		return count(Have.SHORT, Have.MISSING, Have.UNSEEN);
	}

	/** The boost items this account carries or banks for the boosts it needs, by boost. */
	public long boostItems(SkillGap gap)
	{
		Boosts.Boost boost = Boosts.of(gap.getSkill());
		return boost == null ? 0 : holdings.carried(boost.getIds()) + holdings.banked(boost.getIds());
	}
}
