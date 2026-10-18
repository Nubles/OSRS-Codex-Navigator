package com.codex.navigator.ui;

import com.codex.navigator.core.Step;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/** A diary tier as a small seal: filled when done, lit when ready, dim when locked. */
class TierChip extends JComponent
{
	private final String label;
	private final Step.Status status;
	private final double progress;
	private boolean hover;

	TierChip(String label, Step.Status status, double progress, Runnable onClick)
	{
		this.label = label;
		this.status = status;
		this.progress = progress;
		setFont(Theme.small());
		setOpaque(false);
		setPreferredSize(new Dimension(40, 30));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				hover = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hover = false;
				repaint();
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e) && contains(e.getPoint()))
				{
					onClick.run();
				}
			}
		});
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		Color c = status == Step.Status.DONE ? Theme.GREEN : status == Step.Status.BLOCKED ? Theme.progressColor(progress) : Glyph.color(status);
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 7, 7);
		g.setColor(status == Step.Status.DONE ? Theme.alpha(c, 50) : Theme.INK_DEEP);
		g.fill(shape);
		if (status == Step.Status.BLOCKED && progress > 0)
		{
			// A hairline gauge along the foot of the seal: how much of the requirements are met.
			int inset = 5;
			int barW = w - inset * 2;
			g.setColor(Theme.alpha(Theme.STROKE, 200));
			g.fillRect(inset, h - 4, barW, 1);
			g.setColor(Theme.alpha(c, 200));
			g.fillRect(inset, h - 4, (int) Math.round(barW * Math.min(1, progress)), 1);
		}
		else if (status == Step.Status.READY)
		{
			g.setColor(Theme.alpha(c, 26));
			g.fill(shape);
		}
		g.setColor(Theme.alpha(c, hover ? 230 : 140));
		g.draw(shape);
		g.setFont(getFont());
		FontMetrics fm = g.getFontMetrics();
		g.setColor(status == Step.Status.DONE ? Theme.GREEN : hover ? Theme.TEXT : Theme.TEXT_MUTED);
		g.drawString(label, (w - fm.stringWidth(label)) / 2, h / 2 + fm.getAscent() / 2 - 4);
		if (status == Step.Status.DONE)
		{
			ProgressRing.drawCheck(g, w / 2f, h / 2f + 5, 3.5f);
		}
		else
		{
			// Ready means every listed requirement is met and only the tasks remain, which no percentage can measure.
			String pct = status == Step.Status.READY ? "Tasks" : Math.round(progress * 100) + "%";
			g.setColor(c);
			g.drawString(pct, (w - fm.stringWidth(pct)) / 2, h / 2 + fm.getAscent() / 2 + 6);
		}
		g.dispose();
	}
}
