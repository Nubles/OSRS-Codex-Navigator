package com.codex.navigator.ui;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.GoalStore;
import com.codex.navigator.core.Planner;
import com.codex.navigator.core.Recommender;
import java.awt.image.BufferedImage;
import net.runelite.api.Skill;

/** What every view can ask of the panel: the current account, the planner, and where to go next. */
interface Navigation
{
	AccountSnapshot account();

	Planner planner();

	Recommender recommender();

	GoalStore goals();

	boolean includeMiniquests();

	BufferedImage skillIcon(Skill skill);

	void open(Goal goal);

	void showTab(int tab);

	void showAtlas(int list);

	/** Which session length is chosen in the Goals view's session plan, as an index into its choices. */
	int sessionChoice();

	/** Chooses a session length and redraws the plan. */
	void chooseSession(int choice);

	/** True while the Goals view's level picker is open. */
	boolean levelPickerOpen();

	/** The skill chosen in the level picker, or null before one is chosen. */
	Skill levelSkill();

	/** Opens or closes the level picker with a skill chosen (or none), and redraws the Goals view. */
	void levelPicker(boolean open, Skill skill);
}
