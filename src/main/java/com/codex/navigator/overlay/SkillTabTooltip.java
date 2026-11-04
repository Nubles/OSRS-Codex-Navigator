package com.codex.navigator.overlay;

import com.codex.navigator.core.SkillNeeds;
import com.codex.navigator.ui.Theme;
import com.codex.navigator.ui.Ui;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A gilt note beside the skills tab while the pointer rests on a skill a pinned goal needs: which goals need it, to
 * what level, how much experience is left, and whether it is what holds a goal up today. It sits just outside the
 * tab so the game's own tooltip, inside it, stays readable.
 */
public class SkillTabTooltip extends Overlay
{
	static final int WIDTH = 196;
	private static final int PAD = 8;
	private static final int GAP = 6;
	private static final int MAX_ROWS = 4;

	private final Client client;
	private final Supplier<SkillNeeds> needs;
	private final BooleanSupplier enabled;

	public SkillTabTooltip(Client client, Supplier<SkillNeeds> needs, BooleanSupplier enabled)
	{
		this.client = client;
		this.needs = needs;
		this.enabled = enabled;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	/** Everything one note says, worked out from the live experience when the pointer arrives. */
	public static final class Tip
	{
		final String skill;
		final int level;
		final int target;
		final double fraction;
		final int xpLeft;
		final List<Integer> waypoints;
		final List<String> holdsUp;
		final List<Row> rows;
		final int more;

		Tip(String skill, int level, int target, double fraction, int xpLeft, List<Integer> waypoints,
			List<String> holdsUp, List<Row> rows, int more)
		{
			this.skill = skill;
			this.level = level;
			this.target = target;
			this.fraction = fraction;
			this.xpLeft = xpLeft;
			this.waypoints = waypoints;
			this.holdsUp = holdsUp;
			this.rows = rows;
			this.more = more;
		}
	}

	/** One goal in the note. */
	static final class Row
	{
		final String title;
		final int level;
		final int xpLeft;
		final boolean boost;

		Row(String title, int level, int xpLeft, boolean boost)
		{
			this.title = title;
			this.level = level;
			this.xpLeft = xpLeft;
			this.boost = boost;
		}
	}

	/** The note for a skill at {@code xp}, or null when no goal needs more of it. */
	public static Tip tip(Skill skill, int xp, SkillNeeds.Need need)
	{
		int level = SkillBars.level(xp);
		if (need == null || need.getTarget() <= level)
		{
			return null;
		}
		List<Row> rows = new ArrayList<>();
		int more = 0;
		for (SkillNeeds.Use use : need.getUses())
		{
			if (use.getLevel() <= level)
			{
				continue;
			}
			if (rows.size() >= MAX_ROWS)
			{
				more++;
				continue;
			}
			rows.add(new Row(use.getTitle(), use.getLevel(), SkillBars.xpLeft(xp, use.getLevel()), use.isBoostable()));
		}
		return new Tip(skill.getName(), level, need.getTarget(), SkillBars.towardLevel(xp, need.getTarget()),
			SkillBars.xpLeft(xp, need.getTarget()), need.getWaypoints(), need.getHoldsUp(), rows, more);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!enabled.getAsBoolean())
		{
			return null;
		}
		SkillNeeds all = needs.get();
		if (all == null || all.isEmpty())
		{
			return null;
		}
		Widget tab = client.getWidget(InterfaceID.Stats.UNIVERSE);
		if (tab == null || tab.isHidden())
		{
			return null;
		}
		Point mouse = client.getMouseCanvasPosition();
		for (Skill skill : all.all().keySet())
		{
			Widget tile = client.getWidget(SkillBars.tileId(skill));
			if (tile == null || tile.isHidden())
			{
				continue;
			}
			Rectangle bounds = tile.getBounds();
			if (!bounds.contains(mouse.getX(), mouse.getY()))
			{
				continue;
			}
			Tip tip = tip(skill, client.getSkillExperience(skill), all.get(skill));
			if (tip == null)
			{
				return null;
			}
			Rectangle tabBounds = tab.getBounds();
			Dimension size = size(g, tip);
			int x = tabBounds.x - size.width - GAP;
			if (x < GAP)
			{
				x = tabBounds.x + tabBounds.width + GAP;
			}
			int y = bounds.y + bounds.height / 2 - size.height / 2;
			y = Math.max(GAP, Math.min(client.getCanvasHeight() - size.height - GAP, y));
			g.translate(x, y);
			paint(g, tip);
			g.translate(-x, -y);
			return null;
		}
		return null;
	}

	/** The note's size with the fonts {@code g} draws in. */
	static Dimension size(Graphics2D g, Tip tip)
	{
		FontMetrics small = g.getFontMetrics(Theme.small());
		FontMetrics bold = g.getFontMetrics(Theme.title());
		// Follows paint() line by line.
		int line = small.getHeight() + 2;
		int h = PAD + small.getAscent()
			+ bold.getAscent() + 2
			+ bold.getDescent() + 4
			+ 4 + 6 + small.getAscent()
			+ (tip.holdsUp.isEmpty() ? 0 : line)
			+ small.getDescent() + 4
			+ 5 + small.getAscent()
			+ tip.rows.size() * line
			+ (tip.more > 0 ? line : 0)
			+ small.getDescent() + PAD - 2;
		return new Dimension(WIDTH, h);
	}

	private static final BasicStroke RIM_STROKE = new BasicStroke(1f);
	private static final Color RIM = Theme.alpha(Theme.GOLD, 150);
	private static final Color FILL_TOP = Theme.alpha(Theme.CARD_TOP, 238);
	private static final Color FILL_BOTTOM = Theme.alpha(Theme.INK_DEEP, 238);

	/** Draws the note with its top-left corner at the origin; returns its size. */
	public static Dimension paint(Graphics2D g, Tip tip)
	{
		Theme.smooth(g);
		Dimension size = size(g, tip);
		FontMetrics small = g.getFontMetrics(Theme.small());
		FontMetrics bold = g.getFontMetrics(Theme.title());
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, size.width - 1, size.height - 1, 10, 10);
		g.setPaint(new GradientPaint(0, 0, FILL_TOP, 0, size.height, FILL_BOTTOM));
		g.fill(shape);
		g.setStroke(RIM_STROKE);
		g.setColor(RIM);
		g.draw(shape);

		int inner = size.width - PAD * 2;
		int y = PAD + small.getAscent();
		g.setFont(Theme.small());
		shadow(g, "CODEX  ·  GOAL SKILL", PAD, y, Theme.GOLD);
		String pct = Math.min(99, (int) Math.floor(tip.fraction * 100)) + "%";
		shadow(g, pct, size.width - PAD - small.stringWidth(pct), y, Theme.progressColor(tip.fraction));

		y += bold.getAscent() + 2;
		g.setFont(Theme.title());
		String head = tip.skill + " " + tip.level;
		shadow(g, head, PAD, y, Theme.GOLD_BRIGHT);
		int hx = PAD + bold.stringWidth(head);
		g.setFont(Theme.small());
		String to = "  to  ";
		shadow(g, to, hx, y, Theme.TEXT_MUTED);
		g.setFont(Theme.title());
		shadow(g, String.valueOf(tip.target), hx + small.stringWidth(to), y, Theme.GOLD_BRIGHT);

		// The same bar as on the tile, drawn larger: notched where lower goals fall.
		y += bold.getDescent() + 4;
		int barH = 4;
		g.setColor(Theme.INK_DEEP);
		g.fillRect(PAD, y, inner, barH);
		int fill = SkillBars.fillWidth(inner, tip.fraction);
		if (fill > 0)
		{
			g.setColor(SkillBars.goldFor(tip.fraction));
			g.fillRect(PAD, y + 1, fill, barH - 1);
			g.setColor(Theme.GOLD_BRIGHT);
			g.fillRect(PAD, y, fill, 1);
		}
		for (int level : tip.waypoints)
		{
			if (level <= tip.level)
			{
				continue;
			}
			int at = (int) Math.round(inner * SkillBars.towardLevel(Experience.getXpForLevel(level), tip.target));
			g.setColor(at < fill ? Theme.INK_DEEP : Theme.GOLD_DIM);
			g.fillRect(PAD + at, y - 1, 1, barH + 2);
		}
		y += barH + 6 + small.getAscent();

		g.setFont(Theme.small());
		shadow(g, Ui.amount(tip.xpLeft) + " xp to level " + tip.target, PAD, y, Theme.TEXT);

		if (!tip.holdsUp.isEmpty())
		{
			y += small.getHeight() + 2;
			int dx = PAD;
			int dy = y - small.getAscent() + (small.getAscent() - 5) / 2;
			diamond(g, dx + 1, dy + 1, Color.BLACK);
			diamond(g, dx, dy, Theme.GOLD_BRIGHT);
			String who = tip.holdsUp.get(0) + (tip.holdsUp.size() > 1 ? " +" + (tip.holdsUp.size() - 1) : "");
			shadow(g, clip("Next step for " + who, small, inner - 9), PAD + 9, y, Theme.GOLD_BRIGHT);
		}

		y += small.getDescent() + 4;
		g.setColor(Theme.STROKE);
		g.fillRect(PAD, y, inner, 1);
		y += 5 + small.getAscent();
		shadow(g, "NEEDED FOR", PAD, y, Theme.GOLD_DIM);

		for (Row row : tip.rows)
		{
			y += small.getHeight() + 2;
			String right = row.level + "  ·  " + (row.boost ? "boost" : Ui.amount(row.xpLeft));
			int rw = small.stringWidth(right);
			shadow(g, clip(row.title, small, inner - rw - 8), PAD, y, Theme.TEXT);
			String lvl = row.level + "  ·  ";
			int rx = size.width - PAD - rw;
			shadow(g, lvl, rx, y, Theme.GOLD);
			shadow(g, row.boost ? "boost" : Ui.amount(row.xpLeft), rx + small.stringWidth(lvl), y,
				row.boost ? Theme.TEAL : Theme.TEXT_MUTED);
		}
		if (tip.more > 0)
		{
			y += small.getHeight() + 2;
			shadow(g, "+" + tip.more + (tip.more == 1 ? " more goal" : " more goals"), PAD, y, Theme.TEXT_FAINT);
		}
		return size;
	}

	private static void diamond(Graphics2D g, int x, int y, Color color)
	{
		g.setColor(color);
		g.fillRect(x + 2, y, 1, 5);
		g.fillRect(x, y + 2, 5, 1);
		g.fillRect(x + 1, y + 1, 3, 3);
	}

	private static void shadow(Graphics2D g, String text, int x, int y, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	static String clip(String text, FontMetrics fm, int width)
	{
		if (fm.stringWidth(text) <= width)
		{
			return text;
		}
		String ellipsis = "...";
		int end = text.length();
		while (end > 0 && fm.stringWidth(text.substring(0, end) + ellipsis) > width)
		{
			end--;
		}
		return text.substring(0, end) + ellipsis;
	}
}
