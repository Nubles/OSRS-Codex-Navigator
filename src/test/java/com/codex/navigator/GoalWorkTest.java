package com.codex.navigator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.GoalBoard;
import com.codex.navigator.core.GoalStore;
import com.codex.navigator.core.Plan;
import com.codex.navigator.core.Planner;
import com.codex.navigator.core.PublishGate;
import com.codex.navigator.core.Recommender;
import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.DiaryDef;
import com.codex.navigator.data.QuestDef;
import com.codex.navigator.overlay.FocusOverlay;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;

/**
 * The refresh's planning runs on a background lane: what the client thread does per refresh (build the snapshot,
 * gate it, hand it over) stays well under two milliseconds with every goal in the game pinned, and the lane still
 * writes the same board, announces finished goals once, and stops dead when the plugin is turned off.
 */
public class GoalWorkTest
{
	private static final Gson GSON = new Gson();
	private static final CodexData DATA = CodexData.load(GSON);
	private static final Planner PLANNER = new Planner(DATA);
	private static final Recommender RECOMMENDER = new Recommender(PLANNER);

	/** What the lane sent out. */
	private static final class Sink implements GoalWork.Out
	{
		final List<String> boards = Collections.synchronizedList(new ArrayList<>());
		final List<String> finished = Collections.synchronizedList(new ArrayList<>());
		volatile FocusOverlay.View view;
		volatile int views;

		@Override
		public void board(String profile, String json)
		{
			boards.add(profile + "=" + json);
		}

		@Override
		public void finished(List<Plan> plans)
		{
			for (Plan plan : plans)
			{
				finished.add(plan.getTitle());
			}
		}

		@Override
		public void overlay(FocusOverlay.View view)
		{
			this.view = view;
			views++;
		}
	}

	private static final GoalWork.Settings ON = new GoalWork.Settings()
	{
		@Override
		public boolean showOverlay()
		{
			return true;
		}

		@Override
		public boolean includeMiniquests()
		{
			return true;
		}

		@Override
		public boolean suggestWithoutFocus()
		{
			return true;
		}
	};

	/** Jobs handed to the executor, run only when the test says. */
	private static final class Held implements java.util.concurrent.Executor
	{
		final List<Runnable> jobs = new ArrayList<>();

		@Override
		public void execute(Runnable job)
		{
			jobs.add(job);
		}

		void runAll()
		{
			while (!jobs.isEmpty())
			{
				jobs.remove(0).run();
			}
		}
	}

	private static GoalStore everything()
	{
		List<String> keys = new ArrayList<>();
		keys.add(Goal.QUEST_CAPE.key());
		for (QuestDef q : DATA.quests())
		{
			keys.add(Goal.quest(q.name).key());
		}
		for (DiaryDef d : DATA.diaries())
		{
			keys.add(Goal.diary(d.name).key());
		}
		keys.add(Goal.level(Skill.AGILITY, 99).key());
		GoalStore store = new GoalStore();
		store.load(keys);
		return store;
	}

	@Test
	public void aRefreshCostsTheClientThreadUnderTwoMillisecondsWithEveryGoalPinned()
	{
		GoalStore store = everything();
		assertTrue(store.goals().size() > 250);
		for (AccountSnapshot account : new AccountSnapshot[]{TestAccounts.maxed(DATA), TestAccounts.fresh(DATA),
			TestAccounts.mid(DATA).build()})
		{
			PublishGate gate = new PublishGate(10);
			// Warm the code paths, as a running client has. Each run gets a fresh lane, so none waits behind another.
			for (int i = 0; i < 200; i++)
			{
				clientSide(gate, new GoalWork(PLANNER, RECOMMENDER, GSON, new Held(), ON, new Sink()), store, account, i);
			}
			int runs = 200;
			long[] took = new long[runs];
			for (int i = 0; i < runs; i++)
			{
				GoalWork lane = new GoalWork(PLANNER, RECOMMENDER, GSON, new Held(), ON, new Sink());
				long start = System.nanoTime();
				clientSide(gate, lane, store, account, 1000 + i * 20);
				took[i] = System.nanoTime() - start;
			}
			java.util.Arrays.sort(took);
			double median = took[runs / 2] / 1e6;
			double p95 = took[runs * 95 / 100] / 1e6;
			System.out.println(account.getPlayerName() + ": client-thread part of a refresh with " + store.goals().size()
				+ " goals: median " + String.format("%.3f", median) + " ms, 95th " + String.format("%.3f", p95)
				+ " ms, worst " + String.format("%.3f", took[runs - 1] / 1e6) + " ms (a shared test machine; pauses included)");
			assertTrue(median + " ms", median < 2);
			assertTrue(p95 + " ms", p95 < 2);

			// The planning itself, now on the lane, still produces the board the client thread used to write.
			Held executor = new Held();
			Sink sink = new Sink();
			GoalWork work = new GoalWork(PLANNER, RECOMMENDER, GSON, executor, ON, sink);
			work.published(account, store, "p", true);
			work.published(account, store, "p", true);
			long start = System.nanoTime();
			executor.runAll();
			System.out.println("  the lane's share: " + (System.nanoTime() - start) / 1_000_000 + " ms");
			String expected = GSON.toJson(GoalBoard.of(PLANNER, store.goals(), account));
			assertEquals("p=" + expected, sink.boards.get(0));
			// An unchanged board is not written again.
			assertEquals(1, sink.boards.size());
			assertNotNull(sink.view);
		}
	}

	/** What the plugin's refresh does on the client thread, short of reading the client itself. */
	private static void clientSide(PublishGate gate, GoalWork work, GoalStore store, AccountSnapshot account, int tick)
	{
		AccountSnapshot.Builder b = new AccountSnapshot.Builder().loggedIn(true).playerName(account.getPlayerName())
			.combatLevel(account.getCombatLevel()).members(true);
		for (Skill skill : Skill.values())
		{
			b.skill(skill, account.level(skill), account.xp(skill));
		}
		for (QuestDef q : DATA.quests())
		{
			b.quest(q.name, account.quest(q.name));
		}
		b.questPoints(account.getQuestPoints());
		AccountSnapshot next = b.build();
		gate.offer(next, tick, true);
		// Handed over on every run: the case that used to cost the client tens of milliseconds.
		work.published(next, store, "p", true);
	}

	@Test
	public void handingOverNeverWaitsForARunningJob() throws Exception
	{
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try
		{
			Sink sink = new Sink();
			GoalWork work = new GoalWork(PLANNER, RECOMMENDER, GSON, pool, ON, sink);
			CountDownLatch running = new CountDownLatch(1);
			CountDownLatch release = new CountDownLatch(1);
			work.run(() ->
			{
				running.countDown();
				try
				{
					release.await(5, TimeUnit.SECONDS);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
			});
			assertTrue(running.await(5, TimeUnit.SECONDS));
			AccountSnapshot account = TestAccounts.mid(DATA).build();
			GoalStore store = everything();
			// The running job holds on until released (up to five seconds): had the hand-over waited for it, this
			// call would take that long.
			long start = System.nanoTime();
			work.published(account, store, "p", true);
			long ms = (System.nanoTime() - start) / 1_000_000;
			assertTrue(ms + " ms", ms < 1000);
			assertTrue(sink.boards.isEmpty());
			release.countDown();
			CountDownLatch done = new CountDownLatch(1);
			work.run(done::countDown);
			assertTrue(done.await(5, TimeUnit.SECONDS));
			assertEquals(1, sink.boards.size());
		}
		finally
		{
			pool.shutdownNow();
		}
	}

	@Test
	public void aFinishedGoalIsAnnouncedOnceAndNotDuringWarmUp()
	{
		Held executor = new Held();
		Sink sink = new Sink();
		GoalWork work = new GoalWork(PLANNER, RECOMMENDER, GSON, executor, ON, sink);
		GoalStore store = new GoalStore();
		store.load(Collections.singletonList("quest:Cook's Assistant"));
		AccountSnapshot before = TestAccounts.fresh(DATA);
		AccountSnapshot.Builder after = new AccountSnapshot.Builder().loggedIn(true).playerName("Fresh Start");
		for (QuestDef q : DATA.quests())
		{
			after.quest(q.name, QuestState.NOT_STARTED);
		}
		after.quest("Cook's Assistant", QuestState.FINISHED).questPoints(1);
		AccountSnapshot done = after.build();

		work.published(before, store, "p", true);
		work.published(done, store, "p", true);
		work.published(done, store, "p", true);
		executor.runAll();
		assertEquals(Collections.singletonList("Cook's Assistant"), sink.finished);

		// After logging out, a goal seen finished at the first read is not news; nor is one seen while warming up.
		sink.finished.clear();
		work.forget();
		work.published(done, store, "p", true);
		work.published(before, store, "p", false);
		work.published(done, store, "p", false);
		executor.runAll();
		assertTrue(sink.finished.isEmpty());
	}

	@Test
	public void eachProfileGetsItsOwnBoardAndHeldBoardsAreNotWritten()
	{
		Held executor = new Held();
		Sink sink = new Sink();
		GoalWork work = new GoalWork(PLANNER, RECOMMENDER, GSON, executor, ON, sink);
		GoalStore store = everything();
		AccountSnapshot account = TestAccounts.mid(DATA).build();
		work.published(account, store, null, true);
		work.board(AccountSnapshot.empty(), store, "a");
		work.published(account, store, "a", true);
		work.board(account, store, "b");
		work.board(account, store, "b");
		executor.runAll();
		assertEquals(2, sink.boards.size());
		assertTrue(sink.boards.get(0).startsWith("a={"));
		assertTrue(sink.boards.get(1).startsWith("b={"));
	}

	@Test
	public void nothingGoesOutOnceClosed()
	{
		Held executor = new Held();
		Sink sink = new Sink();
		GoalWork work = new GoalWork(PLANNER, RECOMMENDER, GSON, executor, ON, sink);
		GoalStore store = everything();
		work.published(TestAccounts.mid(DATA).build(), store, "p", true);
		work.close();
		work.published(TestAccounts.mid(DATA).build(), store, "p", true);
		work.overlay(TestAccounts.mid(DATA).build(), store);
		executor.runAll();
		assertTrue(work.isClosed());
		assertTrue(sink.boards.isEmpty());
		assertEquals(0, sink.views);
	}

	@Test
	public void oneFailingJobDoesNotStopTheLane()
	{
		Held executor = new Held();
		Sink sink = new Sink();
		GoalWork work = new GoalWork(PLANNER, RECOMMENDER, GSON, executor, ON, sink);
		work.run(() ->
		{
			throw new IllegalStateException("bad");
		});
		work.overlay(AccountSnapshot.empty(), new GoalStore());
		executor.runAll();
		assertEquals(1, sink.views);
		assertNull(sink.view);
	}
}
