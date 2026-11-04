package com.codex.navigator.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Kill counts for the monsters combat achievement tasks belong to, read from what is already saved on this computer,
 * never asked of the game or the network:
 * <ul>
 * <li>Codex Boss Pilot's logbook ({@code codexbosspilot.logbookV1} per RuneScape profile), the highest kill count the
 * game has told it for each boss it plans for. Read only, and only its {@code bosses.<key>.kc} numbers.</li>
 * <li>RuneLite's own Chat Commands plugin, which keeps the count from every "Your ... kill count is" message the game
 * sends under the profile group {@code killcount}, keyed by the boss's name in lower case with colons left out.</li>
 * </ul>
 * The higher of the two is kept for each monster.
 */
public final class KillCounts
{
	public static final String BOSS_PILOT_GROUP = "codexbosspilot";
	public static final String BOSS_PILOT_KEY = "logbookV1";
	/** RuneLite's Chat Commands plugin's profile group for kill counts. */
	public static final String RUNELITE_GROUP = "killcount";

	/**
	 * Boss Pilot's boss keys (its {@code Bosses} list) for the game's combat achievement monster names. Only bosses whose
	 * kill count is that one monster's: Boss Pilot counts the three Dagannoth Kings together, so they are left out.
	 */
	static final Map<String, String> BOSS_PILOT_KEYS;

	/**
	 * Where RuneLite saves a kill count under another name than the monster's: its Chat Commands plugin renames
	 * "Barrows chest" to "Barrows Chests" ({@code KILLCOUNT_RENAMES}).
	 */
	static final Map<String, String> RUNELITE_NAMES = Collections.singletonMap("Barrows", "barrows chests");

	static
	{
		Map<String, String> m = new LinkedHashMap<>();
		m.put("Obor", "obor");
		m.put("Bryophyta", "bryophyta");
		m.put("Scurrius", "scurrius");
		m.put("Giant Mole", "mole");
		m.put("Barrows", "barrows");
		m.put("Sarachnis", "sarachnis");
		m.put("Kalphite Queen", "kq");
		m.put("King Black Dragon", "kbd");
		m.put("Kraken", "kraken");
		m.put("Thermonuclear Smoke Devil", "thermy");
		m.put("Grotesque Guardians", "gg");
		m.put("Zulrah", "zulrah");
		m.put("Vorkath", "vorkath");
		m.put("General Graardor", "graardor");
		m.put("Commander Zilyana", "zilyana");
		m.put("Kree'arra", "kreearra");
		m.put("K'ril Tsutsaroth", "kril");
		BOSS_PILOT_KEYS = Collections.unmodifiableMap(m);
	}

	private KillCounts()
	{
	}

	/** RuneLite's key for a monster's kill count: its name in lower case, colons left out. */
	static String runeliteKey(String monster)
	{
		String renamed = RUNELITE_NAMES.get(monster);
		return renamed != null ? renamed : monster.replace(":", "").toLowerCase(java.util.Locale.ROOT);
	}

	/** Boss Pilot's kill counts by its boss key; empty for a missing, damaged or other-version logbook. */
	static Map<String, Integer> bossPilot(Gson gson, String json)
	{
		Map<String, Integer> out = new HashMap<>();
		if (gson == null || json == null || json.trim().isEmpty())
		{
			return out;
		}
		try
		{
			JsonElement root = gson.fromJson(json, JsonElement.class);
			if (root == null || !root.isJsonObject())
			{
				return out;
			}
			JsonObject book = root.getAsJsonObject();
			if (!book.has("version") || !book.get("version").isJsonPrimitive() || book.get("version").getAsInt() != 1
				|| !book.has("bosses") || !book.get("bosses").isJsonObject())
			{
				return out;
			}
			for (Map.Entry<String, JsonElement> e : book.getAsJsonObject("bosses").entrySet())
			{
				JsonElement entry = e.getValue();
				if (entry == null || !entry.isJsonObject())
				{
					continue;
				}
				JsonElement kc = entry.getAsJsonObject().get("kc");
				if (kc != null && kc.isJsonPrimitive() && kc.getAsJsonPrimitive().isNumber())
				{
					int value = kc.getAsInt();
					if (value > 0)
					{
						out.put(e.getKey(), value);
					}
				}
			}
		}
		catch (JsonParseException | IllegalStateException | NumberFormatException | UnsupportedOperationException e)
		{
			out.clear();
		}
		return out;
	}

	/** The result of a read: counts by the game's monster name, and which sources gave any. */
	public static final class Read
	{
		private final Map<String, Integer> kills;
		private final String source;

		Read(Map<String, Integer> kills, String source)
		{
			this.kills = kills;
			this.source = source;
		}

		public Map<String, Integer> getKills()
		{
			return kills;
		}

		/** "Boss Pilot", "RuneLite", "Boss Pilot and RuneLite", or null when neither knew any. */
		public String getSource()
		{
			return source;
		}
	}

	/**
	 * @param monsters      the game's monster names to look up
	 * @param bossPilotJson Boss Pilot's saved logbook, or null
	 * @param runelite      RuneLite's saved kill count for a key (see {@link #runeliteKey}), or null when none
	 */
	public static Read read(Collection<String> monsters, Gson gson, String bossPilotJson, Function<String, Integer> runelite)
	{
		Map<String, Integer> pilot = bossPilot(gson, bossPilotJson);
		Map<String, Integer> out = new HashMap<>();
		boolean fromPilot = false;
		boolean fromRunelite = false;
		for (String monster : monsters)
		{
			if (monster == null || monster.isEmpty() || CombatTasks.OTHER.equals(monster))
			{
				continue;
			}
			int best = -1;
			String key = BOSS_PILOT_KEYS.get(monster);
			Integer p = key == null ? null : pilot.get(key);
			if (p != null && p > 0)
			{
				best = p;
				fromPilot = true;
			}
			Integer r = runelite == null ? null : runelite.apply(runeliteKey(monster));
			if (r != null && r > 0)
			{
				if (r > best)
				{
					best = r;
				}
				fromRunelite = true;
			}
			if (best > 0)
			{
				out.put(monster, best);
			}
		}
		String source = fromPilot && fromRunelite ? "Boss Pilot and RuneLite" : fromPilot ? "Boss Pilot"
			: fromRunelite ? "RuneLite" : null;
		return new Read(out, source);
	}
}
