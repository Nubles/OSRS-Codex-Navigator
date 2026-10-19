package com.codex.navigator.ui;

import com.codex.navigator.core.Goal;
import com.codex.navigator.core.Road;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.JComponent;

/**
 * The road to the focus goal, drawn like a route on an old map: a winding track with a stone for every quest on the
 * way, gold behind you, a glowing stone for the next one, and a flag at the goal with the day you should reach it.
 * It is drawn from a {@link Road} made once per Compass rebuild, so painting allocates nothing per stop beyond shapes.
 */
final class RoadCard extends JComponent
{
	private static final int TOP = 10;
	private static final int ROW = 28;
	private static final int NOTE = 20;
	private static final int FLAG = ROW + 2;
	private static final int BOTTOM = 6;
	/** The track wanders between these two columns. */
	private static final int LEFT_X = 20;
	private static final int RIGHT_X = 36;
	private static final int TEXT_X = 54;

	private final Road road;
	private final String goal;
	private final String when;
	private final Navigation nav;
	private int hover = -1;

	/**
	 * @param when the flag's second line: a day ("Around 3 Nov") or the play left, or null
	 */
	RoadCard(Navigation nav, Road road, String goal, String when)
	{
		this.nav = nav;
		this.road = road;
		this.goal = goal;
		this.when = when;
		setOpaque(false);
		setToolTipText(goal);
		int h = height(road);
		setPreferredSize(new Dimension(10, h));
		setMinimumSize(new Dimension(10, h));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				int at = stopAt(e.getY());
				if (at != hover)
				{
					hover = at;
					setCursor(Cursor.getPredefinedCursor(at >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
					setToolTipText(at >= 0 ? "Open " + road.getStops().get(at).getQuest() : goal);
					repaint();
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hover = -1;
				repaint();
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				int at = stopAt(e.getY());
				if (at >= 0 && nav != null)
				{
					nav.open(Goal.quest(road.getStops().get(at).getQuest()));
				}
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	static int height(Road road)
	{
		return TOP + (road.getDoneBefore() > 0 ? NOTE : 0) + road.getStops().size() * ROW
			+ (road.getMoreAfter() > 0 ? NOTE : 0) + FLAG + BOTTOM;
	}

	private int firstStopY()
	{
		return TOP + (road.getDoneBefore() > 0 ? NOTE : 0);
	}

	private int stopAt(int y)
	{
		int i = Math.floorDiv(y - firstStopY(), ROW);
		return y >= firstStopY() && i >= 0 && i < road.getStops().size() ? i : -1;
	}

	/** Where the track passes at a row: it swings from side to side, one column per stop. */
	private static int trackX(int row)
	{
		return Math.floorMod(row, 2) == 0 ? LEFT_X : RIGHT_X;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();

		// The map: vellum with a faint wash of gold toward the goal.
		RoundRectangle2D card = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
		g.setPaint(new GradientPaint(0, 0, Theme.CARD_TOP, 0, h, Theme.mix(Theme.CARD, Theme.GOLD_DIM, 0.10)));
		g.fill(card);
		g.setColor(Theme.STROKE_SOFT);
		g.draw(card);
		g.setColor(Theme.alpha(Color.WHITE, 12));
		g.drawLine(Theme.RADIUS / 2, 1, w - Theme.RADIUS / 2, 1);

		List<Road.Stop> stops = road.getStops();
		int n = stops.size();
		int y0 = firstStopY() + ROW / 2;
		// The flag sits one row past the last stop (and the count of stops not drawn), like a final stop.
		int flagY = y0 + n * ROW + (road.getMoreAfter() > 0 ? NOTE : 0) + 2;
		int nextRow = -1;
		for (int i = 0; i < n; i++)
		{
			if (stops.get(i).getState() == Road.State.NEXT)
			{
				nextRow = i;
			}
		}

		// The track, from the road behind (off the top) to the flag. Travelled ground is gilt, the rest dashed.
		int points = n + 1 + (road.getDoneBefore() > 0 ? 1 : 0);
		int[] xs = new int[points];
		int[] ys = new int[points];
		int k = 0;
		if (road.getDoneBefore() > 0)
		{
			xs[k] = trackX(-1);
			ys[k++] = TOP + 2;
		}
		int firstStop = k;
		for (int i = 0; i < n; i++)
		{
			xs[k] = trackX(i);
			ys[k++] = y0 + i * ROW;
		}
		xs[k] = trackX(n);
		ys[k] = flagY;
		// Gilt runs up to the next stop, or the whole way when every stop shown is done.
		int travelledTo = nextRow >= 0 ? firstStop + nextRow : points - 1;
		Path2D track = new Path2D.Float();
		Path2D travelled = new Path2D.Float();
		track.moveTo(xs[0], ys[0]);
		travelled.moveTo(xs[0], ys[0]);
		for (int i = 1; i < points; i++)
		{
			float mid = (ys[i - 1] + ys[i]) / 2f;
			track.curveTo(xs[i - 1], mid, xs[i], mid, xs[i], ys[i]);
			if (i <= travelledTo)
			{
				travelled.curveTo(xs[i - 1], mid, xs[i], mid, xs[i], ys[i]);
			}
		}
		g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Theme.alpha(Theme.GOLD_DIM, 70));
		g.draw(track);
		g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Theme.mix(Theme.INK_DEEP, Theme.GOLD_DIM, 0.30));
		g.draw(track);
		g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{3f, 3f}, 0f));
		g.setColor(Theme.alpha(Theme.GOLD, 80));
		g.draw(track);
		if (road.getDone() > 0)
		{
			g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(Theme.alpha(Theme.GOLD_BRIGHT, 200));
			g.draw(travelled);
		}
		g.setStroke(new BasicStroke(1f));

		Font body = Theme.body();
		Font small = Theme.small();
		Font bold = Theme.title();
		FontMetrics bm = g.getFontMetrics(body);
		FontMetrics sm = g.getFontMetrics(small);
		FontMetrics tm = g.getFontMetrics(bold);

		if (road.getDoneBefore() > 0)
		{
			String behind = road.getDoneBefore() + (road.getDoneBefore() == 1 ? " quest" : " quests") + " behind you";
			text(g, small, behind, TEXT_X, TOP + 3 + sm.getAscent(), Theme.TEXT_FAINT);
		}

		for (int i = 0; i < n; i++)
		{
			Road.Stop stop = stops.get(i);
			int cx = trackX(i);
			int cy = y0 + i * ROW;
			if (i == hover)
			{
				g.setColor(Theme.alpha(Theme.GOLD, 22));
				g.fillRoundRect(4, cy - ROW / 2 + 1, w - 8, ROW - 2, 8, 8);
			}
			stone(g, stop.getState(), cx, cy);

			String sub = stop.getState() == Road.State.NEXT && stop.isStarted() ? "Under way, next on the road" : sub(stop.getState());
			int right = w - 8;
			if (stop.getHazard() != null)
			{
				int pw = sm.stringWidth(stop.getHazard()) + 10;
				int pillX = right - pw;
				int pillY = cy - bm.getAscent() + 1;
				g.setColor(Theme.alpha(Theme.EMBER, 40));
				g.fillRoundRect(pillX, pillY, pw, sm.getHeight(), 8, 8);
				g.setColor(Theme.alpha(Theme.EMBER, 170));
				g.drawRoundRect(pillX, pillY, pw - 1, sm.getHeight() - 1, 8, 8);
				text(g, small, stop.getHazard(), pillX + 5, pillY + sm.getAscent(), Theme.EMBER);
				right = pillX - 4;
			}
			Color nameTint = stop.getState() == Road.State.DONE ? Theme.TEXT_MUTED
				: stop.getState() == Road.State.NEXT ? Theme.GOLD_BRIGHT : Theme.TEXT;
			String name = clip(stop.getQuest(), stop.getState() == Road.State.NEXT ? tm : bm, right - TEXT_X);
			text(g, stop.getState() == Road.State.NEXT ? bold : body, name, TEXT_X, cy - 1, nameTint);
			text(g, small, sub, TEXT_X, cy + sm.getAscent() - 1, stop.getState() == Road.State.NEXT ? Theme.TEAL
				: stop.getState() == Road.State.BLOCKED ? Theme.BLOCKED : Theme.TEXT_FAINT);
		}

		if (road.getMoreAfter() > 0)
		{
			String more = "+" + road.getMoreAfter() + (road.getMoreAfter() == 1 ? " more quest" : " more quests");
			int my = y0 + n * ROW - ROW / 2 + 3 + sm.getAscent();
			text(g, small, more, TEXT_X, my, Theme.TEXT_FAINT);
		}

		// The flag at the goal.
		int fx = trackX(n);
		flag(g, fx, flagY);
		// A long goal name steps down to the plain font before it is cut.
		boolean fits = tm.stringWidth(goal) <= w - 8 - TEXT_X;
		String title = fits ? goal : clip(goal, bm, w - 8 - TEXT_X);
		text(g, fits ? bold : body, title, TEXT_X, flagY - 1, Theme.GOLD_BRIGHT);
		if (when != null)
		{
			text(g, small, clip(when, sm, w - 8 - TEXT_X), TEXT_X, flagY + sm.getAscent() - 1, Theme.TEAL);
		}
		g.dispose();
	}

	private static String sub(Road.State state)
	{
		switch (state)
		{
			case DONE:
				return "Done";
			case NEXT:
				return "Next on the road";
			case BLOCKED:
				return "Needs more first";
			default:
				return "Ready to start";
		}
	}

	/** A waymark: a gilt coin behind you, a glowing stone for the next, plain stones ahead. */
	private static void stone(Graphics2D g, Road.State state, int cx, int cy)
	{
		switch (state)
		{
			case DONE:
				g.setPaint(new GradientPaint(cx - 5, cy - 5, Theme.GOLD_BRIGHT, cx + 5, cy + 5, Theme.GOLD_DIM));
				g.fill(new Ellipse2D.Float(cx - 5.5f, cy - 5.5f, 11, 11));
				g.setColor(Theme.INK_DEEP);
				g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				Path2D tick = new Path2D.Float();
				tick.moveTo(cx - 2.6, cy + 0.2);
				tick.lineTo(cx - 0.6, cy + 2.2);
				tick.lineTo(cx + 2.8, cy - 2);
				g.draw(tick);
				g.setStroke(new BasicStroke(1f));
				break;
			case NEXT:
				g.setPaint(new RadialGradientPaint(cx, cy, 14, new float[]{0f, 1f},
					new Color[]{Theme.alpha(Theme.TEAL, 110), Theme.alpha(Theme.TEAL, 0)}));
				g.fill(new Ellipse2D.Float(cx - 14, cy - 14, 28, 28));
				g.setColor(Theme.INK_DEEP);
				g.fill(new Ellipse2D.Float(cx - 6.5f, cy - 6.5f, 13, 13));
				g.setColor(Theme.TEAL);
				g.setStroke(new BasicStroke(2f));
				g.draw(new Ellipse2D.Float(cx - 6, cy - 6, 12, 12));
				g.setStroke(new BasicStroke(1f));
				g.fill(new Ellipse2D.Float(cx - 2.5f, cy - 2.5f, 5, 5));
				break;
			default:
				Color rim = state == Road.State.BLOCKED ? Theme.BLOCKED : Theme.TEXT_MUTED;
				g.setColor(Theme.CARD);
				g.fill(new Ellipse2D.Float(cx - 4.5f, cy - 4.5f, 9, 9));
				g.setColor(rim);
				g.setStroke(new BasicStroke(1.4f));
				g.draw(new Ellipse2D.Float(cx - 4.5f, cy - 4.5f, 9, 9));
				g.setStroke(new BasicStroke(1f));
		}
	}

	/** A pennant on a pole, planted where the road ends. */
	private static void flag(Graphics2D g, int x, int y)
	{
		g.setColor(Theme.alpha(Theme.INK_DEEP, 160));
		g.fill(new Ellipse2D.Float(x - 6, y + 4, 12, 5));
		g.setColor(Theme.GOLD_DIM);
		g.setStroke(new BasicStroke(1.6f));
		g.drawLine(x, y + 6, x, y - 13);
		g.setStroke(new BasicStroke(1f));
		Path2D pennant = new Path2D.Float();
		pennant.moveTo(x + 1, y - 13);
		pennant.curveTo(x + 6, y - 15, x + 9, y - 10, x + 14, y - 11);
		pennant.lineTo(x + 11, y - 7);
		pennant.lineTo(x + 14, y - 3);
		pennant.curveTo(x + 9, y - 2, x + 6, y - 7, x + 1, y - 5);
		pennant.closePath();
		g.setPaint(new GradientPaint(x, y - 13, Theme.GOLD_BRIGHT, x + 14, y - 3, Theme.GOLD_DIM));
		g.fill(pennant);
		g.setColor(Theme.alpha(Theme.INK_DEEP, 140));
		g.draw(pennant);
		g.setColor(Theme.GOLD_BRIGHT);
		g.fill(new Ellipse2D.Float(x - 1.8f, y - 15.5f, 3.6f, 3.6f));
	}

	private static void text(Graphics2D g, Font font, String s, int x, int y, Color c)
	{
		g.setFont(font);
		g.setColor(Theme.alpha(Color.BLACK, 160));
		g.drawString(s, x + 1, y + 1);
		g.setColor(c);
		g.drawString(s, x, y);
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
