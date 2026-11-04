package com.codex.navigator.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps what the account carries and banks as the plugin sees it: the pack and worn items live, the bank when the
 * player opens it, and Codex Bank Steward's shared copy for the times the bank has not been opened. Whichever bank
 * was seen last wins. Nothing here is saved: it lives only while the client runs.
 */
public final class HoldingsWatch
{
	private Map<Integer, Long> pack = Collections.emptyMap();
	private Map<Integer, Long> worn = Collections.emptyMap();
	private Map<Integer, Long> liveBank;
	private long liveSeenAt;
	private Map<Integer, Long> stewardBank;
	private long stewardSavedAt;

	public synchronized void pack(Map<Integer, Long> items)
	{
		pack = copy(items);
	}

	public synchronized void worn(Map<Integer, Long> items)
	{
		worn = copy(items);
	}

	/** The bank as the player has it open now. */
	public synchronized void bank(Map<Integer, Long> items, long now)
	{
		liveBank = copy(items);
		liveSeenAt = now;
	}

	/** Bank Steward's shared copy, or null when there is none. */
	public synchronized void steward(Holdings.SharedBank shared)
	{
		stewardBank = shared == null ? null : copy(shared.getItems());
		stewardSavedAt = shared == null ? 0 : shared.getSavedAt();
	}

	/** Forgets everything, as on a profile change: a pack or bank belongs to the account it was seen on. */
	public synchronized void clear()
	{
		pack = Collections.emptyMap();
		worn = Collections.emptyMap();
		liveBank = null;
		liveSeenAt = 0;
		stewardBank = null;
		stewardSavedAt = 0;
	}

	public synchronized Holdings holdings()
	{
		Map<Integer, Long> carried = new HashMap<>(pack);
		worn.forEach((id, n) -> carried.merge(id, n, (a, b) -> a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b));
		if (liveBank != null && (stewardBank == null || liveSeenAt >= stewardSavedAt))
		{
			return new Holdings(carried, liveBank, Holdings.BankSource.LIVE, liveSeenAt);
		}
		if (stewardBank != null)
		{
			return new Holdings(carried, stewardBank, Holdings.BankSource.STEWARD, stewardSavedAt);
		}
		return new Holdings(carried, null, Holdings.BankSource.NONE, 0);
	}

	private static Map<Integer, Long> copy(Map<Integer, Long> items)
	{
		Map<Integer, Long> out = new HashMap<>();
		if (items != null)
		{
			items.forEach((id, n) ->
			{
				if (id != null && id > 0 && n != null && n > 0)
				{
					out.merge(id, n, (a, b) -> a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b);
				}
			});
		}
		return out;
	}
}
