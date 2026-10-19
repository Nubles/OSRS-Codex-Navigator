package com.codex.navigator.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads saved entries back as trees, keeping every good part: a damaged, cut-off or hand-edited value gives up
 * only the pieces that cannot be read, and never throws. Nothing here writes: when {@link #keptAll} says part of a
 * value was passed over, the plugin keeps the whole value aside under its key plus {@link #UNREADABLE} before it
 * next saves, so a damaged or newer-version value is never lost.
 */
public final class Saved
{
	/** Added to a key's name to keep aside a saved value that could not be read in full, as Codex Guardian does. */
	public static final String UNREADABLE = "Unreadable";
	/** Longer than any real goal key; a longer string is damage, not a goal. */
	static final int MAX_KEY_LENGTH = 200;
	/** A number written with more characters than this is not a time, a count or an amount. */
	private static final int MAX_NUMBER_LENGTH = 40;

	private Saved()
	{
	}

	/** Parses saved JSON into a tree, or null when it is empty or not JSON at all. */
	public static JsonElement tree(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			return gson.fromJson(json, JsonElement.class);
		}
		catch (JsonParseException | IllegalStateException | NumberFormatException | StackOverflowError e)
		{
			// StackOverflowError: a value nested thousands deep is damage, and Gson reads trees recursively.
			return null;
		}
	}

	/**
	 * The pinned goal keys, focus first: every string in the saved list, anything else in it skipped, so one odd
	 * entry never loses the rest. A bare key is a list of one. Null when nothing can be read.
	 */
	public static List<String> goalKeys(Gson gson, String json)
	{
		JsonElement root = tree(gson, json);
		if (root == null)
		{
			return null;
		}
		List<String> keys = new ArrayList<>();
		if (isString(root))
		{
			add(keys, root.getAsString());
			return keys;
		}
		if (!root.isJsonArray())
		{
			return null;
		}
		for (JsonElement e : root.getAsJsonArray())
		{
			if (isString(e))
			{
				add(keys, e.getAsString());
			}
		}
		return keys;
	}

	private static void add(List<String> keys, String key)
	{
		if (key.length() <= MAX_KEY_LENGTH)
		{
			keys.add(key);
		}
	}

	/**
	 * What has been learned about each skill's pace: skill name to {xp, expected, seconds}. A row of the wrong
	 * shape is skipped and the others kept; {@link Pace#load} then drops rows whose figures make no sense.
	 */
	public static Map<String, double[]> pace(Gson gson, String json)
	{
		JsonElement root = tree(gson, json);
		if (root == null || !root.isJsonObject())
		{
			return null;
		}
		Map<String, double[]> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet())
		{
			JsonElement row = e.getValue();
			if (row == null || !row.isJsonArray() || row.getAsJsonArray().size() != 3)
			{
				continue;
			}
			double[] figures = new double[3];
			boolean ok = true;
			for (int i = 0; i < 3 && ok; i++)
			{
				figures[i] = decimal(row.getAsJsonArray().get(i));
				ok = !Double.isNaN(figures[i]);
			}
			if (ok)
			{
				out.put(e.getKey(), figures);
			}
		}
		return out;
	}

	/** A field of an object as a whole number, or -1 when it is missing, not a number, or beyond a long. */
	public static long number(JsonObject o, String key)
	{
		return o == null ? -1 : number(o.get(key));
	}

	/**
	 * A saved whole number, or -1 when it is missing, not a number, or beyond a long. Read from its text: Gson's own
	 * conversion of a number like {@code 1e100000000} expands every digit on older Java, which can freeze the client.
	 */
	public static long number(JsonElement e)
	{
		double d = decimal(e);
		if (Double.isNaN(d) || Double.isInfinite(d) || d < Long.MIN_VALUE || d >= Long.MAX_VALUE)
		{
			return -1;
		}
		String text = e.getAsString();
		try
		{
			return Long.parseLong(text);
		}
		catch (NumberFormatException ex)
		{
			return (long) d;
		}
	}

	/** A saved number as a double, or NaN when it is missing or not a number. Infinity for one too big to hold. */
	static double decimal(JsonElement e)
	{
		if (e == null || !e.isJsonPrimitive() || !((JsonPrimitive) e).isNumber())
		{
			return Double.NaN;
		}
		String text = e.getAsString();
		if (text.length() > MAX_NUMBER_LENGTH)
		{
			return Double.NaN;
		}
		try
		{
			return Double.parseDouble(text);
		}
		catch (NumberFormatException ex)
		{
			return Double.NaN;
		}
	}

	/**
	 * True when reading {@code json} kept all of it: {@code kept} of its entries were used, and it holds no more
	 * (a list's items, an object's fields, or one bare key). False for anything passed over, including a value that
	 * could not be read at all or one of a shape this version does not know.
	 */
	public static boolean keptAll(Gson gson, String json, int kept)
	{
		if (json == null || json.trim().isEmpty() || "null".equals(json.trim()))
		{
			return true;
		}
		JsonElement root = tree(gson, json);
		int entries;
		if (root == null)
		{
			entries = -1;
		}
		else if (root.isJsonNull())
		{
			entries = 0;
		}
		else if (root.isJsonArray())
		{
			entries = root.getAsJsonArray().size();
		}
		else if (root.isJsonObject())
		{
			entries = root.getAsJsonObject().size();
		}
		else
		{
			entries = isString(root) ? 1 : -1;
		}
		return entries >= 0 && kept >= entries;
	}

	private static boolean isString(JsonElement e)
	{
		return e != null && e.isJsonPrimitive() && ((JsonPrimitive) e).isString();
	}
}
