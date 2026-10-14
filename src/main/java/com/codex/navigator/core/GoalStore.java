package com.codex.navigator.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The player's pinned goals, in order; the first is the focus. Persistence is
 * left to the plugin, which saves {@link #keys()} per RuneScape profile.
 */
public final class GoalStore
{
	private final List<Goal> goals = new CopyOnWriteArrayList<>();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private Consumer<List<String>> saver = keys -> { };

	public void setSaver(Consumer<List<String>> saver)
	{
		this.saver = saver;
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	public List<Goal> goals()
	{
		return Collections.unmodifiableList(new ArrayList<>(goals));
	}

	public Goal focus()
	{
		return goals.isEmpty() ? null : goals.get(0);
	}

	public boolean isPinned(Goal goal)
	{
		return goals.contains(goal);
	}

	public void toggle(Goal goal)
	{
		if (!goals.remove(goal))
		{
			goals.add(goal);
		}
		changed(true);
	}

	public void makeFocus(Goal goal)
	{
		goals.remove(goal);
		goals.add(0, goal);
		changed(true);
	}

	/** Replaces the list from saved keys without saving it back. */
	public void load(List<String> keys)
	{
		goals.clear();
		if (keys != null)
		{
			for (String key : keys)
			{
				Goal goal = Goal.fromKey(key);
				if (goal != null && !goals.contains(goal))
				{
					goals.add(goal);
				}
			}
		}
		changed(false);
	}

	public List<String> keys()
	{
		List<String> keys = new ArrayList<>();
		for (Goal goal : goals)
		{
			keys.add(goal.key());
		}
		return keys;
	}

	private void changed(boolean save)
	{
		if (save)
		{
			saver.accept(keys());
		}
		for (Runnable listener : listeners)
		{
			listener.run();
		}
	}
}
