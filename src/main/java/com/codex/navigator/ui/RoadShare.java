package com.codex.navigator.ui;

import com.codex.navigator.core.Effort;
import com.codex.navigator.core.Plan;
import com.codex.navigator.core.Road;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The road as a card to share: who is walking it, how far along they are, and the road itself, drawn as the Compass
 * draws it, ending at the goal's flag. Every figure appears once: the counts and the time sit in the tiles, the goal's
 * name only on its flag.
 */
public final class RoadShare
{
	static final int WIDTH = 300;
	static final int SCALE = 2;
	private static final int PAD = 16;
	private static final int TILE_H = 38;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

	private RoadShare()
	{
	}

	/** Everything the card shows, taken once so a saved card and a copied one read the same. */
	public static final class Spec
	{
		final String player;
		final String goal;
		final String subtitle;
		final double progress;
		final Road road;
		final List<String[]> tiles;
		final long now;
		final ZoneId zone;

		private Spec(String player, String goal, String subtitle, double progress, Road road, List<String[]> tiles, long now,
			ZoneId zone)
		{
			this.player = player;
			this.goal = goal;
			this.subtitle = subtitle;
			this.progress = progress;
			this.road = road;
			this.tiles = tiles;
			this.now = now;
			this.zone = zone;
		}

		/**
		 * @param player the name to print, or null to leave it off
		 * @param date the day the goal should be reached ("21 Apr"), or null without Chronicle
		 * @return the card, or null when the goal has no road to draw (no quests on the way, or already there)
		 */
		public static Spec of(Plan plan, Road road, String player, String date, long now, ZoneId zone)
		{
			if (plan == null || road == null || plan.isComplete())
			{
				return null;
			}
			List<String[]> tiles = new ArrayList<>();
			tiles.add(new String[]{plan.getQuestsDone() + " of " + plan.getQuestsTotal(), "quests done"});
			if (plan.getHoursLeft() > 0 && !plan.isAwaitingTasks())
			{
				tiles.add(new String[]{Effort.format(plan.getHoursLeft()), "of play left"});
			}
			if (date != null)
			{
				tiles.add(new String[]{"~" + date, "at your pace"});
			}
			String name = player == null || player.trim().isEmpty() ? null : player.trim();
			return new Spec(name, plan.getTitle(), plan.getSubtitle(), plan.getProgress(), road, tiles, now, zone);
		}

		/** The heading: the player's name, or "The road" when it is left off. */
		String heading()
		{
			return player != null ? player : "The road";
		}

		/** The tiles in order, value over label: quests done, the play left, then the day when it is known. */
		List<String[]> tiles()
		{
			return tiles;
		}
	}

	/** "codex-road-2026-10-19.png". */
	public static String fileName(long now, ZoneId zone)
	{
		return "codex-road-" + Instant.ofEpochMilli(now).atZone(zone).toLocalDate() + ".png";
	}

	public static BufferedImage render(Spec spec)
	{
		RoadCard road = new RoadCard(null, spec.road, spec.goal, null);
		int roadW = WIDTH - 2 * PAD;
		int roadH = road.getPreferredSize().height;
		road.setSize(roadW, roadH);

		int headTop = PAD + 16;
		int tilesTop = headTop + 58;
		int roadTop = tilesTop + TILE_H + 14;
		int footTop = roadTop + roadH + 12;
		int height = footTop + 26;

		BufferedImage image = new BufferedImage(WIDTH * SCALE, height * SCALE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.scale(SCALE, SCALE);
		Theme.smooth(g);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		// Vellum with a gilt double rule, as the Compass's cards are framed.
		RoundRectangle2D outer = new RoundRectangle2D.Float(0.5f, 0.5f, WIDTH - 1, height - 1, 14, 14);
		g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.INK, Theme.GOLD_DIM, 0.16), 0, height, Theme.INK_DEEP));
		g.fill(outer);
		g.setColor(Theme.GOLD_DIM);
		g.draw(outer);
		g.setColor(Theme.alpha(Theme.GOLD, 60));
		g.draw(new RoundRectangle2D.Float(4.5f, 4.5f, WIDTH - 9, height - 9, 10, 10));

		// The masthead: the plugin's mark on the left, today's date on the right.
		g.setFont(Theme.small());
		FontMetrics sm = g.getFontMetrics();
		diamond(g, PAD + 3, PAD + 4, 3, Theme.GOLD);
		g.setColor(Theme.GOLD);
		g.drawString("CODEX NAVIGATOR", PAD + 11, PAD + 4 + sm.getAscent() / 2 - 1);
		String today = Instant.ofEpochMilli(spec.now).atZone(spec.zone).format(DATE);
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(today, WIDTH - PAD - sm.stringWidth(today), PAD + 4 + sm.getAscent() / 2 - 1);

		// The heading, with a ring for how far along the goal is.
		int ring = 44;
		int ringX = WIDTH - PAD - ring;
		ring(g, ringX, headTop + 2, ring, spec.progress);
		int textW = ringX - 10 - PAD;
		String heading = spec.heading();
		if (g.getFontMetrics(Theme.title()).stringWidth(heading) * 2 <= textW)
		{
			large(g, heading, PAD, headTop);
		}
		else
		{
			g.setFont(Theme.title());
			g.setColor(Theme.GOLD_BRIGHT);
			g.drawString(clip(heading, g.getFontMetrics(), textW), PAD, headTop + g.getFontMetrics().getAscent() + 6);
		}
		g.setFont(Theme.small());
		g.setColor(Theme.TEXT_MUTED);
		// The goal's kind ("Grandmaster · 5 QP"); its name is on the flag, its share in the ring.
		if (spec.subtitle != null && !spec.subtitle.isEmpty())
		{
			g.drawString(clip(spec.subtitle, g.getFontMetrics(), textW), PAD, headTop + 44);
		}

		// The tiles: quests, the play left and the day, each once.
		int n = spec.tiles.size();
		int gap = 6;
		int tileW = (WIDTH - 2 * PAD - gap * (n - 1)) / n;
		for (int i = 0; i < n; i++)
		{
			int x = PAD + i * (tileW + gap);
			tile(g, x, tilesTop, tileW, spec.tiles.get(i)[0], spec.tiles.get(i)[1], i == 2 ? Theme.TEAL : Theme.GOLD_BRIGHT);
		}

		// The road, ending at the goal's flag.
		Graphics2D rg = (Graphics2D) g.create(PAD, roadTop, roadW, roadH);
		road.paint(rg);
		rg.dispose();

		// The foot: a gilt rule with a diamond, and where it was charted.
		int fy = footTop;
		g.setPaint(new GradientPaint(PAD, 0, Theme.alpha(Theme.GOLD, 0), WIDTH / 2f, 0, Theme.alpha(Theme.GOLD, 140), true));
		g.drawLine(PAD, fy, WIDTH - PAD, fy);
		diamond(g, WIDTH / 2, fy, 3, Theme.GOLD);
		g.setFont(Theme.small());
		String foot = "Charted by Codex Navigator";
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(foot, (WIDTH - g.getFontMetrics().stringWidth(foot)) / 2, fy + 16);
		g.dispose();
		return image;
	}

	static String percent(double progress)
	{
		long p = Math.round(Math.max(0, Math.min(1, progress)) * 100);
		// A goal not yet reached never reads 100%.
		return Math.min(p, 99) + "%";
	}

	private static void tile(Graphics2D g, int x, int y, int w, String value, String label, Color tint)
	{
		RoundRectangle2D box = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, TILE_H - 1, 8, 8);
		g.setPaint(new GradientPaint(0, y, Theme.CARD_TOP, 0, y + TILE_H, Theme.CARD));
		g.fill(box);
		g.setColor(Theme.STROKE);
		g.draw(box);
		g.setColor(Theme.alpha(tint, 150));
		g.drawLine(x + 8, y + 1, x + w - 8, y + 1);
		g.setFont(Theme.title());
		FontMetrics tm = g.getFontMetrics();
		g.setColor(tint);
		g.drawString(clip(value, tm, w - 12), x + 7, y + 6 + tm.getAscent());
		g.setFont(Theme.small());
		FontMetrics sm = g.getFontMetrics();
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(clip(label, sm, w - 12), x + 7, y + TILE_H - 7);
	}

	private static void ring(Graphics2D g, int x, int y, int size, double progress)
	{
		Graphics2D r = (Graphics2D) g.create();
		r.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		float stroke = 5f;
		float inset = stroke / 2 + 0.5f;
		r.setStroke(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
		r.setColor(Theme.alpha(Theme.STROKE, 220));
		r.draw(new Arc2D.Float(x + inset, y + inset, size - 2 * inset, size - 2 * inset, 0, 360, Arc2D.OPEN));
		double p = Math.max(0, Math.min(1, progress));
		if (p > 0)
		{
			r.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			r.setColor(Theme.progressColor(p));
			r.draw(new Arc2D.Float(x + inset, y + inset, size - 2 * inset, size - 2 * inset, 90, (float) (-360 * p), Arc2D.OPEN));
		}
		r.setFont(Theme.small());
		FontMetrics fm = r.getFontMetrics();
		String text = percent(progress);
		r.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		r.setColor(Theme.TEXT);
		r.drawString(text, x + (size - fm.stringWidth(text)) / 2, y + (size + fm.getAscent()) / 2 - 1);
		r.dispose();
	}

	/** The game's bold font doubled pixel for pixel, the way the game draws large lettering. */
	private static void large(Graphics2D g, String text, int x, int y)
	{
		Font font = Theme.title();
		FontMetrics fm = g.getFontMetrics(font);
		int w = Math.max(1, fm.stringWidth(text) + 1);
		int h = fm.getHeight() + 1;
		BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sg = small.createGraphics();
		sg.setFont(font);
		sg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		int base = fm.getAscent();
		sg.setColor(Color.BLACK);
		sg.drawString(text, 1, base + 1);
		sg.setPaint(new GradientPaint(0, 3, Theme.GOLD_BRIGHT, 0, base, Theme.GOLD));
		sg.drawString(text, 0, base);
		sg.dispose();
		Graphics2D lg = (Graphics2D) g.create();
		lg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		lg.drawImage(small, x, y, w * 2, h * 2, null);
		lg.dispose();
	}

	private static void diamond(Graphics2D g, int cx, int cy, int r, Color c)
	{
		Path2D d = new Path2D.Float();
		d.moveTo(cx, cy - r);
		d.lineTo(cx + r, cy);
		d.lineTo(cx, cy + r);
		d.lineTo(cx - r, cy);
		d.closePath();
		g.setColor(c);
		g.fill(d);
	}

	private static String clip(String s, FontMetrics fm, int width)
	{
		if (fm.stringWidth(s) <= width)
		{
			return s;
		}
		String out = s;
		while (out.length() > 1 && fm.stringWidth(out.trim() + "…") > width)
		{
			out = out.substring(0, out.length() - 1);
		}
		return out.trim() + "…";
	}
}
