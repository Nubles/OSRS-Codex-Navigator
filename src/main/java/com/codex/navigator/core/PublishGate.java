package com.codex.navigator.core;

/**
 * Decides when a fresh account snapshot is worth handing to the panel. Anything the player would notice
 * (a level, a quest, a diary) goes out at once; a change in raw experience alone is held back and sent at
 * most every {@code xpOnlyTicks}, so skilling does not rebuild the panel on every tick.
 */
public final class PublishGate
{
	public enum Decision
	{
		/** Nothing changed since the last publish. */
		SKIP,
		/** Only experience moved and it is too soon; keep the snapshot and try again later. */
		HOLD,
		PUBLISH
	}

	private final int xpOnlyTicks;
	private int lastSignature;
	private int lastStructure;
	private int lastTick;
	private boolean any;

	public PublishGate(int xpOnlyTicks)
	{
		this.xpOnlyTicks = xpOnlyTicks;
	}

	public Decision offer(AccountSnapshot next, int tick, boolean force)
	{
		int signature = next.signature();
		if (any && signature == lastSignature)
		{
			return Decision.SKIP;
		}
		int structure = next.structureSignature();
		if (any && !force && structure == lastStructure && tick - lastTick < xpOnlyTicks)
		{
			return Decision.HOLD;
		}
		any = true;
		lastSignature = signature;
		lastStructure = structure;
		lastTick = tick;
		return Decision.PUBLISH;
	}

	/** True once the last publish is old enough for a held experience-only snapshot to go out. */
	public boolean due(int tick)
	{
		return tick - lastTick >= xpOnlyTicks;
	}

	public void reset()
	{
		any = false;
	}
}
