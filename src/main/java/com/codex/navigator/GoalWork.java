package com.codex.navigator;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Chains;
import com.codex.navigator.core.CompletionWatch;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.GoalBoard;
import com.codex.navigator.core.GoalStore;
import com.codex.navigator.core.Plan;
import com.codex.navigator.core.Planner;
import com.codex.navigator.core.Recommender;
import com.codex.navigator.core.Step;
import com.codex.navigator.overlay.FocusOverlay;
import com.google.gson.Gson;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The heavy part of a refresh: planning every pinned goal for the board other plugins read, the finished-goal
 * check and the overlay's focus line. With every goal in the game pinned this takes tens of milliseconds, a
 * visible stutter if done on the client thread, so the client thread only hands over the snapshot it read and the
 * work runs here, one job at a time and in the order handed over, on the background executor.
 * <p>
 * Jobs never touch the client. What they produce goes out through {@link Out}: the board is written back on the
 * client thread (where other plugins have always heard of it), the overlay view is a single volatile field, and
 * chat lines go through RuneLite's queue. Once {@link #close()} has run nothing more goes out.
 */
final class GoalWork
{
	private static final Logger log = LoggerFactory.getLogger(GoalWork.class);

	/** Where finished work goes. */
	interface Out
	{
		/** Saves the board under {@code profile}; called on the background lane. */
		void board(String profile, String json);

		/** Posts the finished-goal chat lines. */
		void finished(List<Plan> plans);

		/** Shows (or, with null, hides) the focus overlay. */
		void overlay(FocusOverlay.View view);
	}

	/** What the overlay should show, read on the lane so a setting change applies to the next job. */
	interface Settings
	{
		boolean showOverlay();

		boolean includeMiniquests();

		boolean suggestWithoutFocus();
	}

	private final Planner planner;
	private final Recommender recommender;
	private final Gson gson;
	private final Executor executor;
	private final Settings settings;
	private final Out out;

	private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
	/** Guards {@link #draining} and the queue's hand-over; held only for a moment, so the client thread never waits. */
	private final Object handOver = new Object();
	/** Held while a job runs; every field below is touched only under it. */
	private final Object working = new Object();
	private boolean draining;
	private volatile boolean closed;

	private final CompletionWatch completion = new CompletionWatch();
	private String lastBoard;
	private String lastBoardProfile;

	GoalWork(Planner planner, Recommender recommender, Gson gson, Executor executor, Settings settings, Out out)
	{
		this.planner = planner;
		this.recommender = recommender;
		this.gson = gson;
		this.executor = executor;
		this.settings = settings;
		this.out = out;
	}

	/**
	 * A new snapshot was published: check for finished goals (posting them only when {@code announce}), write the
	 * board under {@code boardProfile} if it is not null and has changed, and redraw the overlay.
	 */
	void published(AccountSnapshot account, GoalStore store, String boardProfile, boolean announce)
	{
		submit(() ->
		{
			if (account.isLoggedIn() && account.getPlayerName() != null)
			{
				List<Plan> finished = completion.finished(planner, store.goals(), account);
				if (announce && !finished.isEmpty())
				{
					out.finished(finished);
				}
			}
			writeBoard(account, store, boardProfile);
			overlayNow(account, store);
		});
	}

	/** The pins changed or the board was held back: write it under {@code profile} if it has changed. */
	void board(AccountSnapshot account, GoalStore store, String profile)
	{
		submit(() -> writeBoard(account, store, profile));
	}

	/** Redraws the overlay, as after a pin or a setting changed. */
	void overlay(AccountSnapshot account, GoalStore store)
	{
		submit(() -> overlayNow(account, store));
	}

	/** Runs any other job that must keep its place in line, such as reading Chronicle's journal. */
	void run(Runnable job)
	{
		submit(job);
	}

	/** Logged out or switched profile: the next snapshot is not compared with the last one. */
	void forget()
	{
		submit(completion::reset);
	}

	/** Stops all work: jobs still waiting are dropped, and one running now finishes before this returns. */
	void close()
	{
		closed = true;
		synchronized (handOver)
		{
			queue.clear();
		}
		synchronized (working)
		{
			// Nothing to do: taking the lock waits out a job that was already running.
			completion.reset();
		}
	}

	/** True once {@link #close()} has run; work handed on to another thread checks this before it acts. */
	boolean isClosed()
	{
		return closed;
	}

	private void submit(Runnable job)
	{
		synchronized (handOver)
		{
			if (closed)
			{
				return;
			}
			queue.add(job);
			if (draining)
			{
				return;
			}
			draining = true;
		}
		try
		{
			executor.execute(this::drain);
		}
		catch (RuntimeException e)
		{
			// The executor is gone (the client is closing): nothing more will run.
			synchronized (handOver)
			{
				draining = false;
				queue.clear();
			}
		}
	}

	private void drain()
	{
		while (true)
		{
			Runnable job;
			synchronized (handOver)
			{
				job = closed ? null : queue.poll();
				if (job == null)
				{
					draining = false;
					return;
				}
			}
			synchronized (working)
			{
				if (closed)
				{
					continue;
				}
				try
				{
					job.run();
				}
				catch (RuntimeException e)
				{
					// One bad job must not stop the lane: the next one starts from fresh state.
					log.warn("Codex Navigator: a background refresh failed", e);
					completion.reset();
				}
			}
		}
	}

	private void writeBoard(AccountSnapshot account, GoalStore store, String profile)
	{
		if (profile == null || !account.isLoggedIn())
		{
			return;
		}
		String json = gson.toJson(GoalBoard.of(planner, store.goals(), account));
		if (json.equals(lastBoard) && profile.equals(lastBoardProfile))
		{
			return;
		}
		lastBoard = json;
		lastBoardProfile = profile;
		out.board(profile, json);
	}

	private void overlayNow(AccountSnapshot account, GoalStore store)
	{
		out.overlay(view(account, store));
	}

	private FocusOverlay.View view(AccountSnapshot account, GoalStore store)
	{
		if (!settings.showOverlay() || !account.isLoggedIn())
		{
			return null;
		}
		Goal focus = store.focus();
		if (focus != null)
		{
			Plan plan = planner.plan(focus, account);
			Step next = plan.getNext();
			if (plan.isComplete())
			{
				Goal after = Chains.next(planner, focus, account, settings.includeMiniquests());
				return new FocusOverlay.View("CODEX  ·  COMPLETE", plan.getTitle(), 1,
					after != null && !store.isPinned(after) ? "Next in line: " + planner.plan(after, account).getTitle()
						: "Pick your next goal in the Codex");
			}
			return new FocusOverlay.View("CODEX  ·  FOCUS", plan.getTitle(), plan.getProgress(),
				next == null ? null : CodexNavigatorPlugin.stepText(next));
		}
		if (settings.suggestWithoutFocus())
		{
			List<Recommender.Pick> picks = recommender.readyNow(account, store.goals(), settings.includeMiniquests(), 1);
			if (!picks.isEmpty())
			{
				Recommender.Pick pick = picks.get(0);
				return new FocusOverlay.View("CODEX  ·  SUGGESTED", pick.getQuest().name, -1,
					pick.getReason() != null ? pick.getReason() : "Ready to start");
			}
		}
		return null;
	}
}
