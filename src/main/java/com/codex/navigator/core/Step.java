package com.codex.navigator.core;

import net.runelite.api.Skill;

/** One line on a goal's path: a quest to do, a level to reach, or something to check by hand. */
public final class Step
{
	public enum Kind
	{
		QUEST,
		TRAIN,
		QUEST_POINTS,
		COMBAT,
		DIARY_TASKS,
		CHECK
	}

	public enum Status
	{
		DONE,
		IN_PROGRESS,
		READY,
		BLOCKED,
		UNKNOWN
	}

	private final Kind kind;
	private final Status status;
	private final String title;
	private final String detail;
	private final String quest;
	private final Skill skill;

	public Step(Kind kind, Status status, String title, String detail, String quest, Skill skill)
	{
		this.kind = kind;
		this.status = status;
		this.title = title;
		this.detail = detail;
		this.quest = quest;
		this.skill = skill;
	}

	public Kind getKind()
	{
		return kind;
	}

	public Status getStatus()
	{
		return status;
	}

	public String getTitle()
	{
		return title;
	}

	/** Why the step is blocked, or what it involves; may be empty. */
	public String getDetail()
	{
		return detail;
	}

	/** The quest this step is about, for quest steps. */
	public String getQuest()
	{
		return quest;
	}

	/** The skill to train, for training steps. */
	public Skill getSkill()
	{
		return skill;
	}
}
