package com.codex.navigator.ui;

import com.codex.navigator.core.ChompyHunt;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
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
 * The eighteen chompy hats as notches scratched along a bow: every hat reached a gilded notch, the hat in view a lit
 * seal, the track gilded as far as the kills go between them. Under it, the kills of the hat in view, with the first and
 * last hats' kills at the ends. A notch opens that hat's goal.
 */
final class HatTrack extends JComponent
{
	private static final int TRACK_Y = 11;
	private static final int SEAL = 6;

	private final int kills;
	private final int highlight;
	private int hover = -1;

	/**
	 * @param kills     the account's kills, or -1 when not known
	 * @param highlight the hat to light (1 to 18), or 0 for the next one
	 * @param open      called with the hat (1 to 18) of a notch clicked, or null when the notches are not links
	 */
	HatTrack(int kills, int highlight, IntConsumer open)
	{
		this.kills = kills;
		ChompyHunt.Hat next = ChompyHunt.nextHat(Math.max(0, kills));
		this.highlight = highlight > 0 ? highlight : next == null ? 0 : ChompyHunt.rank(next);
		setOpaque(false);
		setFont(Theme.small());
		setPreferredSize(new Dimension(190, 36));
		if (open != null)
		{
			MouseAdapter mouse = new MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					int at = notchAt(e.getX());
					if (at != hover)
					{
						hover = at;
						setCursor(Cursor.getPredefinedCursor(at >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
						setToolTipText(at >= 0 ? ChompyHunt.HATS[at].getName() + " hat, "
							+ CombatView.format(ChompyHunt.HATS[at].getKills()) + " kills" : null);
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
					int at = notchAt(e.getX());
					if (at >= 0 && SwingUtilities.isLeftMouseButton(e))
					{
						open.accept(at + 1);
					}
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}
	}

	private float x(int i)
	{
		float left = SEAL + 3;
		float right = getWidth() - SEAL - 3;
		return left + (right - left) * i / (ChompyHunt.HATS.length - 1f);
	}

	private int notchAt(int px)
	{
		int best = -1;
		float bestGap = 6;
		for (int i = 0; i < ChompyHunt.HATS.length; i++)
		{
			float gap = Math.abs(px - x(i));
			if (gap <= bestGap)
			{
				best = i;
				bestGap = gap;
			}
		}
		return best;
	}

	/** Where the kills reach along the track, in notch steps: 2.5 is half way from the third hat to the fourth. */
	double reach()
	{
		if (kills <= 0)
		{
			return -1;
		}
		double before = 0;
		for (int i = 0; i < ChompyHunt.HATS.length; i++)
		{
			int need = ChompyHunt.HATS[i].getKills();
			if (kills < need)
			{
				return i - 1 + (kills - before) / Math.max(1, need - before);
			}
			before = need;
		}
		return ChompyHunt.HATS.length - 1;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int last = ChompyHunt.HATS.length - 1;
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Theme.STROKE);
		g.drawLine(Math.round(x(0)), TRACK_Y, Math.round(x(last)), TRACK_Y);
		double reach = reach();
		if (reach > -1)
		{
			float start = reach < 0 ? x(0) - (x(1) - x(0)) * (float) -reach : x(0);
			int whole = (int) Math.floor(Math.max(0, reach));
			float end = reach < 0 ? x(0) : whole >= last ? x(last) : x(whole) + (x(whole + 1) - x(whole)) * (float) (reach - whole);
			if (end > start)
			{
				g.setPaint(new GradientPaint(x(0), 0, Theme.GOLD_DIM, Math.max(end, x(0) + 1), 0, Theme.GOLD_BRIGHT));
				g.drawLine(Math.round(Math.max(start, x(0))), TRACK_Y, Math.round(end), TRACK_Y);
			}
		}
		for (int i = 0; i <= last; i++)
		{
			boolean reached = kills >= ChompyHunt.HATS[i].getKills();
			boolean lit = i + 1 == highlight;
			float cx = x(i);
			if (lit)
			{
				g.setColor(Theme.alpha(Theme.GOLD, 40));
				g.fill(new Ellipse2D.Float(cx - SEAL - 3, TRACK_Y - SEAL - 3, SEAL * 2 + 6, SEAL * 2 + 6));
				Ellipse2D seal = new Ellipse2D.Float(cx - SEAL, TRACK_Y - SEAL, SEAL * 2, SEAL * 2);
				g.setColor(reached ? Theme.mix(Theme.INK_DEEP, Theme.GREEN, 0.35) : Theme.INK_DEEP);
				g.fill(seal);
				g.setStroke(new BasicStroke(1.2f));
				g.setColor(reached ? Theme.GREEN : Theme.GOLD_BRIGHT);
				g.draw(seal);
				if (reached)
				{
					ProgressRing.drawCheck(g, cx, TRACK_Y, 2.5f);
				}
				else
				{
					Path2D d = new Path2D.Float();
					d.moveTo(cx, TRACK_Y - 2.5f);
					d.lineTo(cx + 2.5f, TRACK_Y);
					d.lineTo(cx, TRACK_Y + 2.5f);
					d.lineTo(cx - 2.5f, TRACK_Y);
					d.closePath();
					g.setColor(Theme.GOLD_BRIGHT);
					g.fill(d);
				}
				continue;
			}
			// A notch: tall and gilded once scratched, short and dim while still to come.
			g.setStroke(new BasicStroke(reached ? 2f : 1.4f, BasicStroke.CAP_ROUND, BasicStroke.CAP_ROUND));
			g.setColor(hover == i ? Theme.GOLD_BRIGHT : reached ? Theme.GOLD : Theme.GOLD_DIM);
			int h = reached ? 5 : 3;
			g.drawLine(Math.round(cx), TRACK_Y - h, Math.round(cx), TRACK_Y + h);
		}
		FontMetrics fm = g.getFontMetrics(getFont());
		g.setFont(getFont());
		int baseline = TRACK_Y + SEAL + 5 + fm.getAscent();
		String first = CombatView.format(ChompyHunt.HATS[0].getKills());
		String end = CombatView.format(ChompyHunt.HATS[last].getKills());
		int litLeft = Integer.MAX_VALUE;
		int litRight = Integer.MIN_VALUE;
		if (highlight > 0)
		{
			String label = CombatView.format(ChompyHunt.HATS[highlight - 1].getKills());
			int w = fm.stringWidth(label);
			int lx = Math.round(x(highlight - 1) - w / 2f);
			lx = Math.max(0, Math.min(getWidth() - w, lx));
			litLeft = lx;
			litRight = lx + w;
			g.setColor(kills >= ChompyHunt.HATS[highlight - 1].getKills() ? Theme.GREEN : Theme.GOLD_BRIGHT);
			g.drawString(label, lx, baseline);
		}
		g.setColor(Theme.TEXT_FAINT);
		if (fm.stringWidth(first) + 6 < litLeft)
		{
			g.drawString(first, 0, baseline);
		}
		if (getWidth() - fm.stringWidth(end) - 6 > litRight)
		{
			g.drawString(end, getWidth() - fm.stringWidth(end), baseline);
		}
		g.dispose();
	}
}
