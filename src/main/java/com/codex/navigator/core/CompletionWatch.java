package com.codex.navigator.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Spots pinned goals that became complete between two published snapshots. Each goal is judged against
 * the previous snapshot itself, so a goal pinned mid-session is announced when finished even if nothing
 * was published in between, and a goal finished while unpinned is not announced when pinned again.
 */
public final class CompletionWatch
{
	private AccountSnapshot last;

	/** The goals complete in {@code account} that were not complete in the previous snapshot. */
	public List<Plan> finished(Planner planner, List<Goal> goals, AccountSnapshot account)
	{
		AccountSnapshot before = last;
		last = account;
		List<Plan> done = new ArrayList<>();
		if (before == null || !before.isLoggedIn())
		{
			return done;
		}
		for (Goal goal : goals)
		{
			Plan plan = planner.plan(goal, account);
			if (plan.isComplete() && !planner.plan(goal, before).isComplete())
			{
				done.add(plan);
			}
		}
		return done;
	}

	/** Forgets the previous snapshot, as after logging out or switching profile. */
	public void reset()
	{
		last = null;
	}
}
