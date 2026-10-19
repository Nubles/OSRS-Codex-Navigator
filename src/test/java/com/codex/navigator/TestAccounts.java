package com.codex.navigator;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.DiaryDef;
import com.codex.navigator.data.QuestDef;
import net.runelite.api.Experience;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/** Accounts for tests: a mid-game main, a maxed account with everything done, and a first-day account. */
public final class TestAccounts
{
	private static final String[] DONE = {"Cook's Assistant", "Sheep Shearer", "Rune Mysteries", "The Restless Ghost",
		"Plague City", "Biohazard", "Waterfall Quest", "Druidic Ritual", "Witch's House", "Lost City", "Death Plateau",
		"Priest in Peril", "Nature Spirit", "Fight Arena", "Tree Gnome Village", "The Grand Tree", "Dwarf Cannon",
		"Murder Mystery", "Imp Catcher", "Doric's Quest", "Ernest the Chicken", "Goblin Diplomacy", "Romeo & Juliet",
		"Vampyre Slayer", "Demon Slayer", "X Marks the Spot", "Client of Kourend", "Gertrude's Cat", "Sea Slug",
		"Jungle Potion", "Monk's Friend", "Hazeel Cult", "Clock Tower", "The Knight's Sword", "Black Knights' Fortress",
		"Witch's Potion", "Pirate's Treasure", "Prince Ali Rescue", "Underground Pass", "Merlin's Crystal",
		"Elemental Workshop I", "Fishing Contest", "Big Chompy Bird Hunting", "Tribal Totem", "Sheep Herder"};

	private TestAccounts()
	{
	}

	/** A members main around combat 78 with 45 quests done; Agility 52 sits four levels under Regicide's 56. */
	public static AccountSnapshot.Builder mid(CodexData data)
	{
		AccountSnapshot.Builder b = new AccountSnapshot.Builder().loggedIn(true).playerName("Nubles").combatLevel(78).members(true);
		for (QuestDef q : data.quests())
		{
			b.quest(q.name, QuestState.NOT_STARTED);
		}
		int qp = 0;
		for (String d : DONE)
		{
			b.quest(d, QuestState.FINISHED);
			QuestDef q = data.quest(d);
			qp += q == null ? 0 : q.points;
		}
		b.quest("Holy Grail", QuestState.IN_PROGRESS);
		Object[][] levels = {{Skill.ATTACK, 70}, {Skill.STRENGTH, 72}, {Skill.DEFENCE, 65}, {Skill.HITPOINTS, 70},
			{Skill.RANGED, 68}, {Skill.MAGIC, 64}, {Skill.PRAYER, 47}, {Skill.AGILITY, 52}, {Skill.THIEVING, 49},
			{Skill.HERBLORE, 38}, {Skill.CRAFTING, 40}, {Skill.FLETCHING, 55}, {Skill.SLAYER, 58}, {Skill.HUNTER, 41},
			{Skill.MINING, 55}, {Skill.SMITHING, 40}, {Skill.FISHING, 60}, {Skill.COOKING, 62}, {Skill.FIREMAKING, 50},
			{Skill.WOODCUTTING, 60}, {Skill.RUNECRAFT, 35}, {Skill.CONSTRUCTION, 45}, {Skill.FARMING, 42}, {Skill.SAILING, 1}};
		for (Object[] o : levels)
		{
			int level = (Integer) o[1];
			b.skill((Skill) o[0], level, Experience.getXpForLevel(level) + 1200);
		}
		b.questPoints(qp);
		b.diary("Ardougne Easy").diary("Varrock Easy").diary("Lumbridge Easy").diary("Falador Easy");
		return b;
	}

	/** Every skill at 200M experience, every quest finished, every diary and every combat achievement tier done. */
	public static AccountSnapshot maxed(CodexData data)
	{
		AccountSnapshot.Builder b = new AccountSnapshot.Builder().loggedIn(true).playerName("Maxed Main").combatLevel(126).members(true);
		int qp = 0;
		for (QuestDef q : data.quests())
		{
			b.quest(q.name, QuestState.FINISHED);
			qp += q.points;
		}
		for (Skill skill : Skill.values())
		{
			if (skill != Skill.OVERALL)
			{
				b.skill(skill, 99, 200_000_000);
			}
		}
		for (DiaryDef diary : data.diaries())
		{
			b.diary(diary.name);
		}
		for (String tier : CodexData.COMBAT_ACHIEVEMENT_TIERS)
		{
			b.combatAchievements(tier, data.combatAchievementTasks(tier));
		}
		return b.questPoints(qp).combatAchievementPoints(2_630).build();
	}

	/** Logged in on the first day: nothing done, every skill at its starting level. */
	public static AccountSnapshot fresh(CodexData data)
	{
		AccountSnapshot.Builder b = new AccountSnapshot.Builder().loggedIn(true).playerName("Fresh Start").combatLevel(3).members(true);
		for (QuestDef q : data.quests())
		{
			b.quest(q.name, QuestState.NOT_STARTED);
		}
		for (Skill skill : Skill.values())
		{
			if (skill != Skill.OVERALL)
			{
				int level = skill == Skill.HITPOINTS ? 10 : 1;
				b.skill(skill, level, Experience.getXpForLevel(level));
			}
		}
		return b.build();
	}
}
