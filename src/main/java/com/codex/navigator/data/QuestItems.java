package com.codex.navigator.data;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a quest asks you to bring, from the "Items required" and "Recommended" rows of each quest's page on the
 * OSRS Wiki, bundled as quest-items.json (built by tools/build-quest-items.py). Only some quests are listed so far;
 * {@link #of} is null for the rest, and the panel says so rather than guessing.
 */
public final class QuestItems
{
	/** One thing to bring. */
	public static final class Item
	{
		public String name;
		public int qty = 1;
		/** Other items that do the same job ("Pure essence" for "Rune essence"); may be null. */
		public List<String> or;
		/** Every item id that counts, the alternatives' included; null for a kind of thing ("Food", "Any bow"). */
		public List<Integer> ids;
		/** True when the wiki says the item can be got during the quest itself. */
		public boolean duringQuest;
		/** A short note from the wiki, such as "unnoted"; may be null. */
		public String note;

		public Item()
		{
		}

		public Item(String name, int qty, List<Integer> ids, boolean duringQuest, String note)
		{
			this.name = name;
			this.qty = qty;
			this.ids = ids;
			this.duringQuest = duringQuest;
			this.note = note;
		}

		/** True when the item has ids to look for: a kind of thing such as "Food" cannot be checked. */
		public boolean checkable()
		{
			return ids != null && !ids.isEmpty();
		}

		/** "Rune essence or Pure essence", or just the name. */
		public String title()
		{
			if (or == null || or.isEmpty())
			{
				return name;
			}
			return name + " or " + (or.size() == 1 ? or.get(0) : or.get(0) + " +" + (or.size() - 1));
		}
	}

	/** One quest's lists. */
	public static final class Entry
	{
		public List<Item> items = Collections.emptyList();
		public List<Item> recommended = Collections.emptyList();
	}

	private static final class Raw
	{
		Map<String, Entry> quests;
	}

	private static final QuestItems NONE = new QuestItems(Collections.emptyMap());

	private final Map<String, Entry> quests;

	public QuestItems(Map<String, Entry> quests)
	{
		Map<String, Entry> clean = new LinkedHashMap<>();
		if (quests != null)
		{
			quests.forEach((name, entry) ->
			{
				if (name != null && entry != null)
				{
					entry.items = clean(entry.items);
					entry.recommended = clean(entry.recommended);
					clean.put(name, entry);
				}
			});
		}
		this.quests = clean;
	}

	public static QuestItems none()
	{
		return NONE;
	}

	/** The bundled lists; none at all if the file is missing or unreadable, which only costs the prep lists. */
	public static QuestItems load(Gson gson)
	{
		try (InputStream in = QuestItems.class.getResourceAsStream("/com/codex/navigator/quest-items.json"))
		{
			if (in == null)
			{
				return NONE;
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				Raw raw = gson.fromJson(reader, Raw.class);
				return raw == null ? NONE : new QuestItems(raw.quests);
			}
		}
		catch (IOException | RuntimeException e)
		{
			return NONE;
		}
	}

	/** The quest's lists, or null when this quest's items are not bundled yet. */
	public Entry of(String quest)
	{
		return quest == null ? null : quests.get(quest);
	}

	/** How many quests have their lists bundled. */
	public int size()
	{
		return quests.size();
	}

	public java.util.Set<String> quests()
	{
		return Collections.unmodifiableSet(quests.keySet());
	}

	private static List<Item> clean(List<Item> items)
	{
		if (items == null)
		{
			return Collections.emptyList();
		}
		List<Item> out = new ArrayList<>();
		for (Item item : items)
		{
			if (item != null && item.name != null && !item.name.isEmpty())
			{
				item.qty = Math.max(1, item.qty);
				if (item.ids != null)
				{
					item.ids.removeIf(id -> id == null || id <= 0);
				}
				out.add(item);
			}
		}
		return Collections.unmodifiableList(out);
	}
}
