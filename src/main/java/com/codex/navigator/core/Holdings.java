package com.codex.navigator.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * What the account has, by canonical item id: what it carries (pack and worn, read live) and its bank. The bank is
 * the one seen this session when the player opened it, else the copy Codex Bank Steward shares under its RS-profile
 * key {@code codexbanksteward/bankV1}; with neither, the bank is unknown and nothing is claimed about it.
 */
public final class Holdings
{
	/** Codex Bank Steward's config group and the key it shares the bank under (its BankSnapshot contract). */
	public static final String STEWARD_GROUP = "codexbanksteward";
	public static final String STEWARD_BANK_KEY = "bankV1";

	/** Where the bank figures came from. */
	public enum BankSource
	{
		/** Not seen: no bank opened this session and no shared copy. */
		NONE,
		/** The bank as the player last opened it this session. */
		LIVE,
		/** Codex Bank Steward's shared copy. */
		STEWARD
	}

	private static final Holdings NONE = new Holdings(Collections.emptyMap(), null, BankSource.NONE, 0);

	private final Map<Integer, Long> carried;
	private final Map<Integer, Long> bank;
	private final BankSource source;
	private final long bankSeenAt;

	public Holdings(Map<Integer, Long> carried, Map<Integer, Long> bank, BankSource source, long bankSeenAt)
	{
		this.carried = carried == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(carried));
		this.bank = bank == null ? null : Collections.unmodifiableMap(new HashMap<>(bank));
		this.source = bank == null ? BankSource.NONE : source;
		this.bankSeenAt = bank == null ? 0 : bankSeenAt;
	}

	public static Holdings none()
	{
		return NONE;
	}

	/** True when the bank is known, from this session or Bank Steward's copy. */
	public boolean bankKnown()
	{
		return bank != null;
	}

	public BankSource getBankSource()
	{
		return source;
	}

	/** When the bank was last seen, in epoch milliseconds; 0 when unknown. */
	public long getBankSeenAt()
	{
		return bankSeenAt;
	}

	/** How many of any of these items the account carries or wears. */
	public long carried(Collection<Integer> ids)
	{
		return count(carried, ids);
	}

	/** How many of any of these items the bank holds; 0 when the bank is unknown. */
	public long banked(Collection<Integer> ids)
	{
		return bank == null ? 0 : count(bank, ids);
	}

	/** Same carried items and bank as {@code other}, wherever they came from. */
	public boolean sameAs(Holdings other)
	{
		return other != null && carried.equals(other.carried) && java.util.Objects.equals(bank, other.bank)
			&& source == other.source;
	}

	private static long count(Map<Integer, Long> items, Collection<Integer> ids)
	{
		if (ids == null)
		{
			return 0;
		}
		long total = 0;
		for (Integer id : new java.util.HashSet<>(ids))
		{
			Long n = id == null ? null : items.get(id);
			if (n != null && n > 0)
			{
				// Added without wrapping round: a damaged count must not turn the total negative.
				total = total > Long.MAX_VALUE - n ? Long.MAX_VALUE : total + n;
			}
		}
		return total;
	}

	/** Bank Steward's shared bank: its items with their counts, and when it saw them. */
	public static final class SharedBank
	{
		private final Map<Integer, Long> items;
		private final long savedAt;

		SharedBank(Map<Integer, Long> items, long savedAt)
		{
			this.items = items;
			this.savedAt = savedAt;
		}

		public Map<Integer, Long> getItems()
		{
			return items;
		}

		public long getSavedAt()
		{
			return savedAt;
		}
	}

	/**
	 * Reads Bank Steward's {@code bankV1}:
	 * <pre>{"version":1,"savedAt":1760000000000,"items":{"995":1234567,"26950":1},"truncated":false}</pre>
	 * Null when it is missing, damaged, or marked truncated (part of the bank left out, so "none banked" could be
	 * wrong). One unreadable entry is skipped on its own.
	 */
	public static SharedBank readShared(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			JsonElement root = gson.fromJson(json, JsonElement.class);
			if (root == null || !root.isJsonObject())
			{
				return null;
			}
			JsonObject obj = root.getAsJsonObject();
			JsonElement truncated = obj.get("truncated");
			if (truncated != null && truncated.isJsonPrimitive() && truncated.getAsJsonPrimitive().isBoolean()
				&& truncated.getAsBoolean())
			{
				return null;
			}
			JsonElement items = obj.get("items");
			if (items == null || !items.isJsonObject())
			{
				return null;
			}
			Map<Integer, Long> out = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : items.getAsJsonObject().entrySet())
			{
				try
				{
					int id = Integer.parseInt(e.getKey().trim());
					long qty = e.getValue().getAsJsonPrimitive().getAsLong();
					if (id > 0 && qty > 0)
					{
						out.merge(id, qty, (a, b) -> a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b);
					}
				}
				catch (RuntimeException skip)
				{
					// One odd entry does not spoil the rest of the bank.
				}
			}
			long savedAt = 0;
			JsonElement at = obj.get("savedAt");
			if (at != null && at.isJsonPrimitive() && ((JsonPrimitive) at).isNumber())
			{
				savedAt = Math.max(0, at.getAsLong());
			}
			return new SharedBank(out, savedAt);
		}
		catch (RuntimeException | StackOverflowError e)
		{
			return null;
		}
	}
}
