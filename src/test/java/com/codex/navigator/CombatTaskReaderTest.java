package com.codex.navigator;

import com.codex.navigator.core.CombatTasks;
import java.util.BitSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombatTaskReaderTest
{
	@Test
	public void aTaskIsDoneByItsBitInItsVarp()
	{
		int[] varps = new int[CombatTaskReader.COMPLETED_VARPS.length];
		varps[0] = 1;
		varps[8] = 1 << 12;
		varps[20] = 1 << 31;
		BitSet done = CombatTaskReader.done(varps);
		assertTrue(done.get(0));
		// Vorkath Veteran is task 268: varp 268 / 32 = 8, bit 268 % 32 = 12.
		assertEquals(268, CaFixture.named("Vorkath Veteran").getId());
		assertTrue(done.get(268));
		assertTrue(done.get(20 * 32 + 31));
		assertFalse(done.get(1));
		assertEquals(3, done.cardinality());
	}

	@Test
	public void everyTaskTheGameListsHasACompletionVarp()
	{
		int capacity = CombatTaskReader.COMPLETED_VARPS.length * 32;
		for (CombatTasks.Task t : CaFixture.tasks())
		{
			assertTrue(t + " has id " + t.getId() + "; add the next CA_TASK_COMPLETED varp", t.getId() < capacity);
		}
		assertEquals(6, CombatTaskReader.TIER_ENUMS.length);
		assertEquals(6, CombatTaskReader.THRESHOLD_VARBITS.length);
	}
}
