package com.codex.navigator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Scanner;
import org.junit.Test;

/**
 * Codex Session Chronicle saves its journal under {@code codexsessionchronicle.journal}: a list of sessions, each
 * with {@code startedAt} and {@code playedMs}. The fixture is a byte copy of Chronicle's own
 * {@code chronicle-journal.json}, the file its JournalFixtureTest checks its writer against (two sessions: Vorkath for
 * 26 min 15 s, then an hour of Mining); copy it again whenever Chronicle's changes.
 */
public class PlayTimeTest
{
	private static final long DAY = 24 * 3_600_000L;
	private static final long T0 = 1_790_000_000_000L;

	private static String fixture()
	{
		InputStream in = PlayTimeTest.class.getResourceAsStream("chronicle-journal.json");
		assertNotNull(in);
		try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()))
		{
			return scanner.useDelimiter("\\A").next();
		}
	}

	@Test
	public void chroniclesJournalIsRead()
	{
		// Six days after the first session: 86.25 minutes counted over the seven-day floor.
		long now = T0 + 6 * DAY;
		PlayTime time = PlayTime.read(new Gson(), fixture(), now);
		assertNotNull(time);
		assertEquals(2, time.getSessions());
		assertEquals((1_575_000 + 3_600_000) / 3_600_000.0 / 7, time.getHoursPerDay(), 1e-9);
	}

	@Test
	public void threeEveningsGiveADailyHabitAndADate()
	{
		// Evenings of 90, 45 and 120 minutes; six days after the first, 255 minutes over seven days is about 36 a day.
		String json = "[{\"startedAt\":" + T0 + ",\"playedMs\":5400000},{\"startedAt\":" + (T0 + 2 * DAY)
			+ ",\"playedMs\":2700000},{\"startedAt\":" + (T0 + 5 * DAY) + ",\"playedMs\":7200000}]";
		long now = T0 + 6 * DAY;
		PlayTime time = PlayTime.read(new Gson(), json, now);
		assertNotNull(time);
		assertEquals(3, time.getSessions());
		assertEquals(255 / 60.0 / 7, time.getHoursPerDay(), 1e-9);
		assertEquals("35 min a day", time.rateText());
		// Ten hours of play at that rate is 17 days off.
		assertEquals(17, time.days(10));
		assertEquals(PlayTimeTest.dateOf(now, 17), time.dateText(10, now, ZoneOffset.UTC));
		assertEquals("Around " + dateOf(now, 17) + " at your 35 min a day", time.describe(10, now, ZoneOffset.UTC));
		// A few minutes left is still tomorrow at the soonest, never today.
		assertEquals(1, time.days(0.1));
		assertEquals(-1, time.days(0));
		assertEquals(-1, time.days(10_000));
		assertEquals("Over a year off at your 35 min a day", time.describe(10_000, now, ZoneOffset.UTC));
	}

	private static String dateOf(long now, int days)
	{
		return java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)
			.format(java.time.Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().plusDays(days));
	}

	@Test
	public void oldSessionsAndThinJournalsGiveNoDate()
	{
		Gson gson = new Gson();
		// A month later every session is out of the window.
		assertNull(PlayTime.read(gson, fixture(), T0 + 40 * DAY));
		// One session is not a habit.
		assertNull(PlayTime.read(gson, "[{\"startedAt\":" + T0 + ",\"playedMs\":7200000}]", T0 + DAY));
		// Two short ones are too little play to judge from.
		assertNull(PlayTime.read(gson, "[{\"startedAt\":" + T0 + ",\"playedMs\":600000},{\"startedAt\":" + (T0 + 1000)
			+ ",\"playedMs\":600000}]", T0 + DAY));
	}

	@Test
	public void aMissingOrBrokenJournalIsQuietlyIgnored()
	{
		Gson gson = new Gson();
		assertNull(PlayTime.read(gson, null, T0));
		assertNull(PlayTime.read(gson, "", T0));
		assertNull(PlayTime.read(gson, "{not json", T0));
		assertNull(PlayTime.read(gson, "{\"startedAt\":1}", T0));
		// Odd entries are skipped and the good ones still count; a session dated in the future counts for nothing.
		String mixed = "[null, 5, {\"startedAt\":\"soon\"}, {\"startedAt\":" + (T0 + 9 * DAY) + ",\"playedMs\":3600000},"
			+ "{\"startedAt\":" + T0 + ",\"playedMs\":3600000},{\"startedAt\":" + (T0 + DAY) + ",\"playedMs\":3600000}]";
		PlayTime time = PlayTime.read(gson, mixed, T0 + 2 * DAY);
		assertNotNull(time);
		assertEquals(2, time.getSessions());
		assertEquals(2 / 7.0, time.getHoursPerDay(), 1e-9);
	}

	@Test
	public void aDateNextYearThatLooksPastSaysItsYear()
	{
		// Two hours a day; 19 Oct 2026 noon UTC.
		long now = java.time.LocalDate.of(2026, 10, 19).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
		String json = "[{\"startedAt\":" + (now - 6 * DAY) + ",\"playedMs\":25200000},{\"startedAt\":" + (now - DAY)
			+ ",\"playedMs\":25200000}]";
		PlayTime time = PlayTime.read(new Gson(), json, now);
		assertEquals(2.0, time.getHoursPerDay(), 1e-9);
		// 360 days off: "14 Oct" alone would read as last week.
		assertEquals("Oct 2027", time.dateText(720, now, ZoneOffset.UTC));
		assertEquals("Around Oct 2027 at your 2 h a day", time.describe(720, now, ZoneOffset.UTC));
		// Into next year but plainly ahead: no year needed.
		assertEquals("3 Jan", time.dateText(2 * 76, now, ZoneOffset.UTC));
		assertEquals("29 Oct", time.dateText(20, now, ZoneOffset.UTC));
	}
}
