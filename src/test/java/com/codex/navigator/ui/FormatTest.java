package com.codex.navigator.ui;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** Experience figures follow the suite's one rule, as every Codex plugin writes coins and experience. */
public class FormatTest
{
	@Test
	public void amountsFollowTheSuiteRule()
	{
		// In full below 10,000; whole k below a million; M to one decimal; B to two.
		assertEquals("0", PaceCard.perHour(0));
		assertEquals("8,240", PaceCard.perHour(8_240));
		assertEquals("10k", PaceCard.perHour(10_000));
		assertEquals("57k", PaceCard.perHour(57_400));
		assertEquals("96k", PaceCard.perHour(95_700));
		assertEquals("1.3M", PaceCard.perHour(1_290_000));
		assertEquals("2.10B", PaceCard.perHour(2_100_000_000L));
		// Rounding that reaches the next unit is written in it, never "1000k".
		assertEquals("100k", PaceCard.perHour(99_960));
		assertEquals("999k", PaceCard.perHour(999_499));
		assertEquals("1.0M", PaceCard.perHour(999_500));
		assertEquals("1.00B", PaceCard.perHour(999_960_000));
	}
}
