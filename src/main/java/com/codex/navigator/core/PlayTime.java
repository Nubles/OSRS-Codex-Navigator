package com.codex.navigator.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * How much this account plays a day, from the sessions Codex Session Chronicle files in its journal (group
 * {@code codexsessionchronicle}, key {@code journal}: a list of sessions, each with {@code startedAt} and
 * {@code playedMs} in milliseconds). It turns a goal's hours of play into a date on the calendar. The journal is
 * only read; without Chronicle, or with too little in it, there is no date and nothing changes.
 */
public final class PlayTime
{
	public static final String GROUP = "codexsessionchronicle";
	public static final String JOURNAL = "journal";

	/** Sessions are counted over at most this many days back. */
	static final int WINDOW_DAYS = 14;
	/** A journal younger than this is still counted over this many days, so one long first evening is not a habit. */
	static final int MIN_DAYS = 7;
	/** At least this many sessions in the window... */
	static final int MIN_SESSIONS = 2;
	/** ...and this much play, before a rate is trusted. */
	static final long MIN_PLAYED_MS = 60 * 60_000L;
	/** A date further off than this is not worth a calendar. */
	static final int MAX_DAYS = 365;

	private static final long DAY_MS = 24 * 3_600_000L;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

	private final double hoursPerDay;
	private final int sessions;

	private PlayTime(double hoursPerDay, int sessions)
	{
		this.hoursPerDay = hoursPerDay;
		this.sessions = sessions;
	}

	/**
	 * Reads Chronicle's journal as saved; null when it is missing, unreadable, or holds too little recent play
	 * to judge a habit from. Odd entries are skipped rather than failing the whole journal.
	 */
	public static PlayTime read(Gson gson, String json, long now)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		JsonElement root;
		try
		{
			root = gson.fromJson(json, JsonElement.class);
		}
		catch (JsonParseException | IllegalStateException | NumberFormatException e)
		{
			return null;
		}
		if (root == null || !root.isJsonArray())
		{
			return null;
		}
		JsonArray list = root.getAsJsonArray();
		long from = now - WINDOW_DAYS * DAY_MS;
		long earliest = Long.MAX_VALUE;
		long played = 0;
		int count = 0;
		for (JsonElement e : list)
		{
			if (e == null || !e.isJsonObject())
			{
				continue;
			}
			JsonObject s = e.getAsJsonObject();
			long started = number(s, "startedAt");
			long ms = number(s, "playedMs");
			// A session from the future (a wrong clock) or with no play is not evidence of anything.
			if (started < from || started > now || ms <= 0)
			{
				continue;
			}
			earliest = Math.min(earliest, started);
			// One session can not hold more play than the day has.
			played += Math.min(ms, DAY_MS);
			count++;
		}
		if (count < MIN_SESSIONS || played < MIN_PLAYED_MS)
		{
			return null;
		}
		double days = Math.max(MIN_DAYS, Math.min(WINDOW_DAYS, (now - earliest) / (double) DAY_MS));
		return new PlayTime(played / 3_600_000.0 / days, count);
	}

	private static long number(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
		{
			return -1;
		}
		try
		{
			return e.getAsLong();
		}
		catch (NumberFormatException ex)
		{
			return -1;
		}
	}

	public double getHoursPerDay()
	{
		return hoursPerDay;
	}

	public int getSessions()
	{
		return sessions;
	}

	/** Days of play at this rate for so many hours, at least 1; -1 when it is more than a year off or there is nothing left. */
	public int days(double hours)
	{
		if (hours <= 0 || hoursPerDay <= 0)
		{
			return -1;
		}
		double days = Math.ceil(hours / hoursPerDay);
		return days > MAX_DAYS ? -1 : (int) Math.max(1, days);
	}

	/** The day so many hours of play would land on, or null when {@link #days} has no answer. */
	public LocalDate date(double hours, long now, ZoneId zone)
	{
		int days = days(hours);
		return days < 0 ? null : Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(days);
	}

	/**
	 * "3 Nov", or "Oct 2027" for a date ten months or more off, which without its year would read like a day
	 * already gone (and that far ahead the day itself means little); null when there is no date.
	 */
	public String dateText(double hours, long now, ZoneId zone)
	{
		LocalDate date = date(hours, now, zone);
		if (date == null)
		{
			return null;
		}
		LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
		// Read without a year, a date is taken as the nearest one of that name; ten months on, that is the past.
		boolean farOff = !date.isBefore(today.plusMonths(10));
		return farOff ? MONTH.format(date) : DATE.format(date);
	}

	/** "40 min a day" or "1.5 h a day": the habit in a few words. */
	public String rateText()
	{
		if (hoursPerDay < 0.95)
		{
			return Math.max(5, Math.round(hoursPerDay * 60 / 5.0) * 5) + " min a day";
		}
		double rounded = Math.round(hoursPerDay * 2) / 2.0;
		return (rounded == Math.floor(rounded) ? String.valueOf((int) rounded) : String.valueOf(rounded)) + " h a day";
	}

	/**
	 * "Around 3 Nov at your 40 min a day", or "Over a year off at your 40 min a day" past {@link #MAX_DAYS};
	 * null with nothing left to play.
	 */
	public String describe(double hours, long now, ZoneId zone)
	{
		if (hours <= 0)
		{
			return null;
		}
		String date = dateText(hours, now, zone);
		return (date == null ? "Over a year off" : "Around " + date) + " at your " + rateText();
	}
}
