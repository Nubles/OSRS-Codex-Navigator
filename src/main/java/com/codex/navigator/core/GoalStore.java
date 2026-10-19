package com.codex.navigator.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The player's pinned goals, in order; the first is the focus. Persistence is
 * left to the plugin, which saves {@link #keys()} per RuneScape profile.
 */
public final class GoalStore
{
	/**
	 * At most this many goals are held: more than every quest, diary and the cape together, so only a damaged
	 * save could reach it, and its first goals (the focus and those just after) are the ones kept.
	 */
	public static final int MAX_GOALS = 400;

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
			if (goals.size() >= MAX_GOALS)
			{
				return;
			}
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
		load(keys, null);
	}

	/**
	 * Replaces the list from saved keys, then adds any {@code extra} goals that are not already there,
	 * such as pins made while logged out. Saves only when an extra goal was actually added.
	 */
	public void load(List<String> keys, List<String> extra)
	{
		// Gathered in a set first: a long damaged list must not be checked against itself goal by goal.
		Set<Goal> loaded = new LinkedHashSet<>();
		addAll(loaded, keys);
		int before = loaded.size();
		addAll(loaded, extra);
		goals.clear();
		goals.addAll(loaded);
		changed(loaded.size() > before);
	}

	private static void addAll(Set<Goal> into, List<String> keys)
	{
		if (keys == null)
		{
			return;
		}
		for (String key : keys)
		{
			if (into.size() >= MAX_GOALS)
			{
				return;
			}
			Goal goal = key == null ? null : Goal.fromKey(key);
			if (goal != null)
			{
				into.add(goal);
			}
		}
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
