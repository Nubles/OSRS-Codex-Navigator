package com.codex.navigator.overlay;

import com.codex.navigator.core.SkillNeeds;
import com.codex.navigator.ui.Theme;
import java.awt.Color;
import java.util.List;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * The maths and the drawing plan of the bars laid along the bottom of each skills tab tile. Everything here is pure:
 * {@link SkillTabWidgets} turns a plan into game widgets, and tests paint the same plan to check how it looks, so the
 * picture in the tests is the picture in the game.
 *
 * <p>Experience comes from RuneLite's {@link Experience} table, never a copy of it.
 */
public final class SkillBars
{
	/** Kept clear of the tile's carved rim on each side, as the skills tab insets its own tiles. */
	static final int INSET = 4;
	/** Height of the bar above the tile's bottom edge. */
	static final int LIFT = 2;
	static final int GOAL_HEIGHT = 3;
	static final int PLAIN_HEIGHT = 2;
	/** At most this many lower levels are notched on one bar. */
	static final int NOTCHES = 3;
	/** The mark's top-right corner offset from the tile's top-right corner. */
	static final int MARK_X = 4;
	static final int MARK_Y = 3;

	/** Widget slots per tile, drawn in this order. */
	static final int TRACK = 0;
	static final int FILL = 1;
	static final int SHINE = 2;
	static final int NOTCH = 3;
	static final int SHADOW = NOTCH + NOTCHES;
	static final int MARK = SHADOW + 3;
	static final int SLOTS = MARK + 3;

	static final Color TRACK_COLOR = Theme.INK_DEEP;
	/** Widget opacity runs the other way round: 0 is solid, 255 invisible. */
	static final int TRACK_SEE_THROUGH = 70;
	static final int PLAIN_SEE_THROUGH = 60;

	private SkillBars()
	{
	}

	/** One rectangle of a bar. {@code y} counts from the tile's bottom edge, or from its top when {@link #fromTop}. */
	public static final class Rect
	{
		final int x;
		final int y;
		final int width;
		final int height;
		final boolean fromTop;
		final Color color;
		/** As a widget takes it: 0 solid, 255 invisible. */
		final int seeThrough;

		Rect(int x, int y, int width, int height, boolean fromTop, Color color, int seeThrough)
		{
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.fromTop = fromTop;
			this.color = color;
			this.seeThrough = seeThrough;
		}

		/** Top edge inside a tile of this height. */
		int top(int tileHeight)
		{
			return fromTop ? y : tileHeight - y - height;
		}
	}

	/**
	 * How far a skill is toward a level a goal needs, in experience from zero, so it matches the panel's own
	 * measure ({@link com.codex.navigator.core.SkillGap#fraction()}): 98 of 99 reads as about 90%, not 0%.
	 */
	public static double towardLevel(int xp, int level)
	{
		int needed = Experience.getXpForLevel(Math.max(1, Math.min(Experience.MAX_VIRT_LEVEL, level)));
		if (needed <= 0)
		{
			return 1;
		}
		return Math.max(0, Math.min(1, xp / (double) needed));
	}

	/** How far through the current level a skill is, or -1 at 99 and above where there is no next real level. */
	public static double towardNextLevel(int xp)
	{
		int level = Experience.getLevelForXp(Math.max(0, xp));
		if (level >= Experience.MAX_REAL_LEVEL)
		{
			return -1;
		}
		int from = Experience.getXpForLevel(level);
		int to = Experience.getXpForLevel(level + 1);
		return Math.max(0, Math.min(1, (xp - from) / (double) (to - from)));
	}

	/** Experience still to earn before reaching {@code level}; 0 once there. */
	public static int xpLeft(int xp, int level)
	{
		return Math.max(0, Experience.getXpForLevel(Math.max(1, Math.min(Experience.MAX_VIRT_LEVEL, level))) - xp);
	}

	/** The skill's level from its experience, capped at 99 as the tab's own numbers are. */
	public static int level(int xp)
	{
		return Math.min(Experience.MAX_REAL_LEVEL, Experience.getLevelForXp(Math.max(0, xp)));
	}

	/** Pixel width of the filled part of a bar {@code track} wide; at least one pixel once anything is earned. */
	static int fillWidth(int track, double fraction)
	{
		if (fraction <= 0 || track <= 0)
		{
			return 0;
		}
		return Math.max(1, Math.min(track, (int) Math.floor(track * fraction)));
	}

	/**
	 * What one tile draws: a slot per {@link #SLOTS}, null where nothing is drawn. With a need, the bar runs to the
	 * highest level the goals ask for, notched at the lower ones, and a small gilt diamond sits in the corner when
	 * the skill holds a goal up. Without one, and with {@code plainBars}, a quieter bar shows the way to the next level.
	 *
	 * @return null when the tile draws nothing at all
	 */
	public static Rect[] plan(int tileWidth, int xp, SkillNeeds.Need need, boolean plainBars)
	{
		int track = tileWidth - INSET * 2;
		if (track <= 0)
		{
			return null;
		}
		Rect[] out = new Rect[SLOTS];
		int currentLevel = level(xp);
		if (need != null && need.getTarget() > currentLevel)
		{
			double fraction = towardLevel(xp, need.getTarget());
			int fill = fillWidth(track, fraction);
			out[TRACK] = new Rect(INSET, LIFT, track, GOAL_HEIGHT, false, TRACK_COLOR, TRACK_SEE_THROUGH);
			if (fill > 0)
			{
				out[FILL] = new Rect(INSET, LIFT, fill, GOAL_HEIGHT - 1, false, goldFor(fraction), 0);
				out[SHINE] = new Rect(INSET, LIFT + GOAL_HEIGHT - 1, fill, 1, false, Theme.GOLD_BRIGHT, 0);
			}
			List<Integer> waypoints = need.getWaypoints();
			int slot = 0;
			for (int level : waypoints)
			{
				if (slot >= NOTCHES || level <= currentLevel)
				{
					continue;
				}
				int at = (int) Math.round(track * towardLevel(Experience.getXpForLevel(level), need.getTarget()));
				if (at <= 0 || at >= track)
				{
					continue;
				}
				// Cut dark into gold where the bar has passed it; a dim gold tick on the empty track ahead.
				boolean passed = at < fill;
				out[NOTCH + slot++] = new Rect(INSET + at, LIFT, 1, GOAL_HEIGHT, false,
					passed ? Theme.INK_DEEP : Theme.GOLD_DIM, 0);
			}
			if (need.isBlocking())
			{
				// A five-pixel diamond in three strokes, with its shadow one pixel down and right.
				int right = tileWidth - MARK_X;
				int left = right - 5;
				diamond(out, SHADOW, left + 1, MARK_Y + 1, Color.BLACK);
				diamond(out, MARK, left, MARK_Y, Theme.GOLD_BRIGHT);
			}
			return out;
		}
		if (!plainBars)
		{
			return null;
		}
		double fraction = towardNextLevel(xp);
		if (fraction < 0)
		{
			return null;
		}
		out[TRACK] = new Rect(INSET, LIFT, track, PLAIN_HEIGHT, false, TRACK_COLOR, TRACK_SEE_THROUGH);
		int fill = fillWidth(track, fraction);
		if (fill > 0)
		{
			out[FILL] = new Rect(INSET, LIFT, fill, PLAIN_HEIGHT, false, Theme.TEXT_MUTED, PLAIN_SEE_THROUGH);
		}
		return out;
	}

	private static void diamond(Rect[] out, int slot, int x, int y, Color color)
	{
		out[slot] = new Rect(x + 2, y, 1, 5, true, color, 0);
		out[slot + 1] = new Rect(x, y + 2, 5, 1, true, color, 0);
		out[slot + 2] = new Rect(x + 1, y + 1, 3, 3, true, color, 0);
	}

	/** Aged gold that warms as the bar fills: dim at the start, full gold near the level. */
	static Color goldFor(double fraction)
	{
		return Theme.mix(Theme.GOLD_DIM, Theme.GOLD, 0.35 + 0.65 * Math.max(0, Math.min(1, fraction)));
	}

	/** The skills tab tile for a skill, from RuneLite's gameval {@code InterfaceID.Stats}; -1 for Overall. */
	public static int tileId(Skill skill)
	{
		switch (skill)
		{
			case ATTACK:
				return net.runelite.api.gameval.InterfaceID.Stats.ATTACK;
			case STRENGTH:
				return net.runelite.api.gameval.InterfaceID.Stats.STRENGTH;
			case DEFENCE:
				return net.runelite.api.gameval.InterfaceID.Stats.DEFENCE;
			case RANGED:
				return net.runelite.api.gameval.InterfaceID.Stats.RANGED;
			case PRAYER:
				return net.runelite.api.gameval.InterfaceID.Stats.PRAYER;
			case MAGIC:
				return net.runelite.api.gameval.InterfaceID.Stats.MAGIC;
			case RUNECRAFT:
				return net.runelite.api.gameval.InterfaceID.Stats.RUNECRAFT;
			case CONSTRUCTION:
				return net.runelite.api.gameval.InterfaceID.Stats.CONSTRUCTION;
			case HITPOINTS:
				return net.runelite.api.gameval.InterfaceID.Stats.HITPOINTS;
			case AGILITY:
				return net.runelite.api.gameval.InterfaceID.Stats.AGILITY;
			case HERBLORE:
				return net.runelite.api.gameval.InterfaceID.Stats.HERBLORE;
			case THIEVING:
				return net.runelite.api.gameval.InterfaceID.Stats.THIEVING;
			case CRAFTING:
				return net.runelite.api.gameval.InterfaceID.Stats.CRAFTING;
			case FLETCHING:
				return net.runelite.api.gameval.InterfaceID.Stats.FLETCHING;
			case SLAYER:
				return net.runelite.api.gameval.InterfaceID.Stats.SLAYER;
			case HUNTER:
				return net.runelite.api.gameval.InterfaceID.Stats.HUNTER;
			case MINING:
				return net.runelite.api.gameval.InterfaceID.Stats.MINING;
			case SMITHING:
				return net.runelite.api.gameval.InterfaceID.Stats.SMITHING;
			case FISHING:
				return net.runelite.api.gameval.InterfaceID.Stats.FISHING;
			case COOKING:
				return net.runelite.api.gameval.InterfaceID.Stats.COOKING;
			case FIREMAKING:
				return net.runelite.api.gameval.InterfaceID.Stats.FIREMAKING;
			case WOODCUTTING:
				return net.runelite.api.gameval.InterfaceID.Stats.WOODCUTTING;
			case FARMING:
				return net.runelite.api.gameval.InterfaceID.Stats.FARMING;
			case SAILING:
				return net.runelite.api.gameval.InterfaceID.Stats.SAILING;
			default:
				return -1;
		}
	}
}
