package com.codex.navigator.ui;

import com.codex.navigator.core.CombatTasks;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The six combat achievement tiers as seals on one gilded track: reached tiers sealed in green, the next one lit, the
 * track gilded as far as the points go between them. A seal opens that tier's goal.
 */
final class TierLadder extends JComponent
{
	private static final String[] SHORT = {"Easy", "Med", "Hard", "Elite", "Master", "GM"};
	private static final int SEAL = 7;
	private static final int TRACK_Y = 12;

	private final int points;
	private final int[] thresholds = new int[CombatTasks.TIERS.length];
	private final int highlight;
	private int hover = -1;

	/**
	 * @param highlight a tier to ring in gold (1 to 6), such as the goal's own, or 0 for the next one
	 * @param open      called with the tier (1 to 6) of a seal clicked, or null when the seals are not links
	 */
	TierLadder(CombatTasks tasks, int points, int highlight, IntConsumer open)
	{
		this.points = points;
		for (int i = 0; i < thresholds.length; i++)
		{
			thresholds[i] = tasks.threshold(i + 1);
		}
		this.highlight = highlight > 0 ? highlight : tasks.nextTier(points);
		setOpaque(false);
		setFont(Theme.small());
		setPreferredSize(new Dimension(190, 40));
		if (open != null)
		{
			MouseAdapter mouse = new MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					int seal = sealAt(e.getX());
					if (seal != hover)
					{
						hover = seal;
						setCursor(Cursor.getPredefinedCursor(seal >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
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
					int seal = sealAt(e.getX());
					if (seal >= 0 && SwingUtilities.isLeftMouseButton(e))
					{
						open.accept(seal + 1);
					}
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}
	}

	private float x(int i)
	{
		float left = SEAL + 5;
		float right = getWidth() - SEAL - 5;
		return left + (right - left) * i / (thresholds.length - 1f);
	}

	private int sealAt(int px)
	{
		for (int i = 0; i < thresholds.length; i++)
		{
			if (Math.abs(px - x(i)) <= 15)
			{
				return i;
			}
		}
		return -1;
	}

	/** Where the points reach along the track, in seal steps: 2.5 is half way from Hard to Elite. */
	double reach()
	{
		double before = 0;
		for (int i = 0; i < thresholds.length; i++)
		{
			if (points < thresholds[i])
			{
				double span = Math.max(1, thresholds[i] - before);
				return i - 1 + Math.max(0, (points - before) / span);
			}
			before = thresholds[i];
		}
		return thresholds.length - 1;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		double reach = reach();
		// The track: a hairline throughout, gilded as far as the points reach.
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Theme.STROKE);
		g.drawLine(Math.round(x(0)), TRACK_Y, Math.round(x(thresholds.length - 1)), TRACK_Y);
		if (reach > 0)
		{
			int whole = (int) Math.floor(reach);
			float end = whole >= thresholds.length - 1 ? x(thresholds.length - 1)
				: x(whole) + (x(whole + 1) - x(whole)) * (float) (reach - whole);
			g.setPaint(new java.awt.GradientPaint(x(0), 0, Theme.GOLD_DIM, end, 0, Theme.GOLD_BRIGHT));
			g.drawLine(Math.round(x(0)), TRACK_Y, Math.round(end), TRACK_Y);
		}
		g.setStroke(new BasicStroke(1.2f));
		FontMetrics fm = g.getFontMetrics(getFont());
		g.setFont(getFont());
		for (int i = 0; i < thresholds.length; i++)
		{
			boolean reached = points >= thresholds[i];
			boolean lit = i + 1 == highlight;
			float cx = x(i);
			Ellipse2D seal = new Ellipse2D.Float(cx - SEAL, TRACK_Y - SEAL, SEAL * 2, SEAL * 2);
			if (lit)
			{
				g.setColor(Theme.alpha(Theme.GOLD, 40));
				g.fill(new Ellipse2D.Float(cx - SEAL - 4, TRACK_Y - SEAL - 4, SEAL * 2 + 8, SEAL * 2 + 8));
			}
			g.setColor(reached ? Theme.mix(Theme.INK_DEEP, Theme.GREEN, 0.35) : Theme.INK_DEEP);
			g.fill(seal);
			Color ring = reached ? Theme.GREEN : lit ? Theme.GOLD_BRIGHT : hover == i ? Theme.GOLD : Theme.GOLD_DIM;
			g.setColor(ring);
			g.draw(seal);
			if (reached)
			{
				ProgressRing.drawCheck(g, cx, TRACK_Y, 3f);
			}
			else
			{
				// A small diamond, the Codex's mark for what is still to come.
				Path2D d = new Path2D.Float();
				d.moveTo(cx, TRACK_Y - 2.5f);
				d.lineTo(cx + 2.5f, TRACK_Y);
				d.lineTo(cx, TRACK_Y + 2.5f);
				d.lineTo(cx - 2.5f, TRACK_Y);
				d.closePath();
				g.setColor(lit ? Theme.GOLD_BRIGHT : Theme.alpha(Theme.GOLD_DIM, 200));
				g.fill(d);
			}
			String label = SHORT[i];
			g.setColor(reached ? Theme.GREEN : lit ? Theme.GOLD_BRIGHT : hover == i ? Theme.TEXT : Theme.TEXT_MUTED);
			g.drawString(label, Math.round(cx - fm.stringWidth(label) / 2f), TRACK_Y + SEAL + 4 + fm.getAscent());
		}
		g.dispose();
	}
}
