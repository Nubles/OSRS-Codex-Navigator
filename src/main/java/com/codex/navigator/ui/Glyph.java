package com.codex.navigator.ui;

import com.codex.navigator.core.Step;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/** A drawn status mark: a sealed check, a half moon, a lit ring, a lock or a question. */
public class Glyph extends JComponent
{
	private final int size;
	private Step.Status status;

	public Glyph(Step.Status status, int size)
	{
		this.status = status;
		this.size = size;
		Dimension d = new Dimension(size, size);
		setPreferredSize(d);
		setMinimumSize(d);
		setMaximumSize(d);
		setOpaque(false);
	}

	public void setStatus(Step.Status status)
	{
		this.status = status;
		repaint();
	}

	public static Color color(Step.Status status)
	{
		switch (status)
		{
			case DONE:
				return Theme.GREEN;
			case IN_PROGRESS:
				return Theme.GOLD_BRIGHT;
			case READY:
				return Theme.TEAL;
			case BLOCKED:
				return Theme.BLOCKED;
			default:
				return Theme.TEXT_FAINT;
		}
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		float s = size - 2;
		float o = 1;
		Color c = color(status);
		g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		Ellipse2D circle = new Ellipse2D.Float(o, o, s, s);
		switch (status)
		{
			case DONE:
			{
				g.setColor(Theme.alpha(c, 60));
				g.fill(circle);
				g.setColor(c);
				g.draw(circle);
				Path2D check = new Path2D.Float();
				check.moveTo(o + s * 0.28, o + s * 0.52);
				check.lineTo(o + s * 0.44, o + s * 0.68);
				check.lineTo(o + s * 0.74, o + s * 0.34);
				g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.draw(check);
				break;
			}
			case IN_PROGRESS:
			{
				g.setColor(Theme.alpha(c, 40));
				g.fill(circle);
				g.setColor(c);
				g.fill(new Arc2D.Float(o, o, s, s, 90, 180, Arc2D.PIE));
				g.draw(circle);
				break;
			}
			case READY:
			{
				g.setColor(Theme.alpha(c, 50));
				g.fill(new Ellipse2D.Float(o - 0.5f, o - 0.5f, s + 1, s + 1));
				g.setColor(c);
				g.draw(circle);
				float dot = s * 0.36f;
				g.fill(new Ellipse2D.Float(o + (s - dot) / 2, o + (s - dot) / 2, dot, dot));
				break;
			}
			case BLOCKED:
			{
				g.setColor(Theme.alpha(c, 170));
				g.draw(circle);
				float bw = s * 0.42f;
				float bh = s * 0.30f;
				float bx = o + (s - bw) / 2;
				float by = o + s * 0.46f;
				g.fill(new RoundRectangle2D.Float(bx, by, bw, bh, 2, 2));
				g.draw(new Arc2D.Float(bx + bw * 0.18f, by - bh * 0.85f, bw * 0.64f, bh * 1.5f, 0, 180, Arc2D.OPEN));
				break;
			}
			default:
			{
				g.setColor(c);
				g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{2f, 2f}, 0));
				g.draw(circle);
				g.setFont(Theme.small());
				String q = "?";
				int w = g.getFontMetrics().stringWidth(q);
				g.drawString(q, Math.round(o + (s - w) / 2f), Math.round(o + s / 2f + 4));
			}
		}
		g.dispose();
	}
}
