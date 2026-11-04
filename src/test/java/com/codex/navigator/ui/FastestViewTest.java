package com.codex.navigator.ui;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** The fastest path's numbers follow the suite's rule: in full under 10,000, whole thousands, then millions. */
public class FastestViewTest
{
	@Test
	public void experienceReadsBySizes()
	{
		assertEquals("3,875", FastestView.xp(3875));
		assertEquals("15k", FastestView.xp(15_300));
		assertEquals("1.1M", FastestView.xp(1_074_489));
	}
}
