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
}
