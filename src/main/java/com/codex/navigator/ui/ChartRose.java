package com.codex.navigator.ui;

import com.codex.navigator.core.SeaCharting;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import javax.swing.JComponent;

/**
 * The six kinds of chart as a compass rose: one point for each kind, inked in gold from the centre outward as far as
 * its charts are done (green once all are), with the kinds and their counts as a legend beside it. A kind whose tool
 * the player's Sailing level does not reach yet is drawn faint.
 */
final class ChartRose extends JComponent
{
	private static final int ROSE = 74;
	private static final int ROW = 13;

	private final SeaCharting.View view;
	private final int level;
	private int hover = -1;

	ChartRose(SeaCharting.View view, int level)
	{
		this.view = view;
		this.level = level;
		setOpaque(false);
		setFont(Theme.small());
		setPreferredSize(new Dimension(190, Math.max(ROSE, ROW * SeaCharting.Kind.values().length) + 2));
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				int at = rowAt(e.getX(), e.getY());
				if (at != hover)
				{
					hover = at;
					setToolTipText(at < 0 ? null : tip(SeaCharting.Kind.values()[at]));
					repaint();
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hover = -1;
				repaint();
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	private String tip(SeaCharting.Kind kind)
	{
		return "<html>" + Ui.escape(kind.getLabel()) + " charts: " + view.doneOf(kind) + " of " + SeaCharting.total(kind)
			+ "<br>" + Ui.escape(kind.getTool()) + ", from " + kind.getLevel() + " Sailing</html>";
	}

	private int legendTop()
	{
		return Math.max(0, (getHeight() - ROW * SeaCharting.Kind.values().length) / 2);
	}

	private int rowAt(int x, int y)
	{
		if (x < ROSE)
		{
			return -1;
		}
		int i = (y - legendTop()) / ROW;
		return y >= legendTop() && i >= 0 && i < SeaCharting.Kind.values().length ? i : -1;
	}

	/** The colour of a kind's point: green when every chart is done, gold while some are, faint before its tool's level. */
	private Color colour(SeaCharting.Kind kind)
	{
		int done = view.doneOf(kind);
		int total = SeaCharting.total(kind);
		if (total > 0 && done >= total)
		{
			return Theme.GREEN;
		}
		return level >= kind.getLevel() ? Theme.GOLD_BRIGHT : Theme.GOLD_DIM;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		SeaCharting.Kind[] kinds = SeaCharting.Kind.values();
		float cx = ROSE / 2f;
		float cy = getHeight() / 2f;
		float outer = ROSE / 2f - 3;
		float inner = 7;

		// The compass ring, with a tick between each point.
		g.setStroke(new BasicStroke(1f));
		g.setColor(Theme.STROKE_SOFT);
		g.draw(new Ellipse2D.Float(cx - outer, cy - outer, outer * 2, outer * 2));
		g.setColor(Theme.alpha(Theme.GOLD_DIM, 90));
		for (int i = 0; i < kinds.length; i++)
		{
			double a = Math.toRadians(-90 + 60 * i + 30);
			g.drawLine(Math.round(cx + (float) Math.cos(a) * (outer - 4)), Math.round(cy + (float) Math.sin(a) * (outer - 4)),
				Math.round(cx + (float) Math.cos(a) * outer), Math.round(cy + (float) Math.sin(a) * outer));
		}

		for (int i = 0; i < kinds.length; i++)
		{
			SeaCharting.Kind kind = kinds[i];
			int total = SeaCharting.total(kind);
			double share = total == 0 ? 0 : view.doneOf(kind) / (double) total;
			double a = Math.toRadians(-90 + 60 * i);
			Path2D point = petal(cx, cy, a, inner, outer - 2);
			g.setColor(Theme.mix(Theme.INK_DEEP, Theme.CARD, 0.5));
			g.fill(point);
			if (share > 0)
			{
				// Ink the point from the middle out, as far as the kind is done.
				float reach = inner + (outer - 2 - inner) * (float) Math.min(1, share);
				Area filled = new Area(point);
				filled.intersect(new Area(new Ellipse2D.Float(cx - reach, cy - reach, reach * 2, reach * 2)));
				Color c = colour(kind);
				float ex = cx + (float) Math.cos(a) * reach;
				float ey = cy + (float) Math.sin(a) * reach;
				g.setPaint(new GradientPaint(cx, cy, Theme.mix(c, Theme.INK_DEEP, 0.35), ex, ey, c));
				g.fill(filled);
			}
			g.setStroke(new BasicStroke(hover == i ? 1.4f : 1f));
			g.setColor(hover == i ? Theme.GOLD_BRIGHT : Theme.alpha(colour(kind), 150));
			g.draw(point);
		}
		// The hub.
		g.setColor(Theme.INK_DEEP);
		g.fill(new Ellipse2D.Float(cx - inner + 1, cy - inner + 1, (inner - 1) * 2, (inner - 1) * 2));
		g.setColor(Theme.GOLD);
		g.draw(new Ellipse2D.Float(cx - inner + 1, cy - inner + 1, (inner - 1) * 2, (inner - 1) * 2));
		g.fill(new Ellipse2D.Float(cx - 1.6f, cy - 1.6f, 3.2f, 3.2f));

		// The legend: each kind with its done and total, in the same order as the points, clockwise from north.
		FontMetrics fm = g.getFontMetrics(getFont());
		g.setFont(getFont());
		int left = ROSE + 4;
		int right = getWidth();
		int top = legendTop();
		for (int i = 0; i < kinds.length; i++)
		{
			SeaCharting.Kind kind = kinds[i];
			int y = top + i * ROW;
			int base = y + (ROW + fm.getAscent() - fm.getDescent()) / 2;
			Color c = colour(kind);
			float dx = left + 3;
			float dy = y + ROW / 2f;
			Path2D d = new Path2D.Float();
			d.moveTo(dx, dy - 3);
			d.lineTo(dx + 3, dy);
			d.lineTo(dx, dy + 3);
			d.lineTo(dx - 3, dy);
			d.closePath();
			g.setColor(c);
			g.fill(d);
			g.setColor(hover == i ? Theme.GOLD_BRIGHT : level >= kind.getLevel() ? Theme.TEXT : Theme.TEXT_FAINT);
			g.drawString(kind.getLabel(), left + 9, base);
			String count = view.doneOf(kind) + "/" + SeaCharting.total(kind);
			g.setColor(c == Theme.GREEN ? Theme.GREEN : Theme.TEXT_MUTED);
			g.drawString(count, right - fm.stringWidth(count), base);
		}
		g.dispose();
	}

	/** A slim kite from {@code from} to {@code to} along angle {@code a}, widest a third of the way out. */
	private static Path2D petal(float cx, float cy, double a, float from, float to)
	{
		float waist = from + (to - from) * 0.35f;
		float wide = Math.max(3, (to - from) * 0.22f + 3);
		double side = a + Math.PI / 2;
		Path2D p = new Path2D.Float();
		p.moveTo(cx + Math.cos(a) * from, cy + Math.sin(a) * from);
		p.lineTo(cx + Math.cos(a) * waist + Math.cos(side) * wide, cy + Math.sin(a) * waist + Math.sin(side) * wide);
		p.lineTo(cx + Math.cos(a) * to, cy + Math.sin(a) * to);
		p.lineTo(cx + Math.cos(a) * waist - Math.cos(side) * wide, cy + Math.sin(a) * waist - Math.sin(side) * wide);
		p.closePath();
		return p;
	}
}
