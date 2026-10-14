package com.codex.navigator.ui;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/** Draws the Codex compass rose used for the sidebar button. */
public final class Icons
{
	private Icons()
	{
	}

	public static BufferedImage compass(int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		Theme.smooth(g);
		float c = size / 2f;
		float r = size / 2f - 1;
		g.setStroke(new BasicStroke(Math.max(1f, size / 14f)));
		g.setColor(Theme.GOLD_DIM);
		g.draw(new Ellipse2D.Float(c - r, c - r, r * 2, r * 2));

		// Four long points in gold, the north one lit teal.
		float l = r * 0.92f;
		float w = r * 0.26f;
		Path2D north = diamondPoint(c, c, 0, -l, w);
		Path2D south = diamondPoint(c, c, 0, l, w);
		Path2D east = diamondPoint(c, c, l, 0, w);
		Path2D west = diamondPoint(c, c, -l, 0, w);
		g.setColor(Theme.GOLD);
		g.fill(south);
		g.fill(east);
		g.fill(west);
		g.setColor(Theme.TEAL);
		g.fill(north);
		g.setColor(Theme.GOLD_BRIGHT);
		float dot = Math.max(2f, size / 8f);
		g.fill(new Ellipse2D.Float(c - dot / 2, c - dot / 2, dot, dot));
		g.dispose();
		return image;
	}

	private static Path2D diamondPoint(float cx, float cy, float dx, float dy, float width)
	{
		float len = (float) Math.hypot(dx, dy);
		float nx = -dy / len * width;
		float ny = dx / len * width;
		Path2D p = new Path2D.Float();
		p.moveTo(cx + dx, cy + dy);
		p.lineTo(cx + nx, cy + ny);
		p.lineTo(cx, cy);
		p.lineTo(cx - nx, cy - ny);
		p.closePath();
		return p;
	}
}
