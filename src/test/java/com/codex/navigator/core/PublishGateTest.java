package com.codex.navigator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.api.Skill;
import org.junit.Test;

public class PublishGateTest
{
	private static AccountSnapshot account(int level, int xp)
	{
		return new AccountSnapshot.Builder().loggedIn(true).skill(Skill.WOODCUTTING, level, xp).build();
	}

	@Test
	public void experienceOnlyChangesAreThrottled()
	{
		PublishGate gate = new PublishGate(10);
		assertEquals(PublishGate.Decision.PUBLISH, gate.offer(account(60, 273_742), 0, false));
		assertEquals(PublishGate.Decision.SKIP, gate.offer(account(60, 273_742), 1, false));
		assertEquals(PublishGate.Decision.HOLD, gate.offer(account(60, 273_800), 2, false));
		assertFalse(gate.due(9));
		assertTrue(gate.due(10));
		assertEquals(PublishGate.Decision.PUBLISH, gate.offer(account(60, 273_900), 10, false));
	}

	@Test
	public void levelsAndForcedSnapshotsGoOutAtOnce()
	{
		PublishGate gate = new PublishGate(10);
		gate.offer(account(60, 273_742), 0, false);
		assertEquals(PublishGate.Decision.PUBLISH, gate.offer(account(61, 302_288), 1, false));
		assertEquals(PublishGate.Decision.PUBLISH, gate.offer(account(61, 302_300), 2, true));
	}

	@Test
	public void resetPublishesTheNextSnapshotEvenIfUnchanged()
	{
		PublishGate gate = new PublishGate(10);
		gate.offer(AccountSnapshot.empty(), 0, false);
		gate.reset();
		assertEquals(PublishGate.Decision.PUBLISH, gate.offer(AccountSnapshot.empty(), 1, false));
	}
}
