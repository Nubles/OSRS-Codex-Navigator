package com.codex.navigator.overlay;

import com.codex.navigator.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A small gilt plaque in the game view: the focus goal, a progress rule and the next step.
 * It reads a snapshot the plugin prepares, so rendering never touches game state.
 */
public class FocusOverlay extends Overlay
{
	private static final int WIDTH = 176;
	private static final int PAD = 8;

	/** What the plaque shows; the plugin swaps in a new one when anything changes. */
	public static final class View
	{
		final String label;
		final String title;
		/** 0 to 1, or negative to hide the percentage for a suggestion. */
		final double progress;
		final String step;

		public View(String label, String title, double progress, String step)
		{
			this.label = label;
			this.title = title;
			this.progress = progress;
			this.step = step;
		}
	}

	private volatile View view;

	public FocusOverlay()
	{
		setPosition(OverlayPosition.TOP_LEFT);
	}

	public void setView(View view)
	{
		this.view = view;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		View v = view;
		if (v == null)
		{
			return null;
		}
		Theme.smooth(g);
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		Layout l = layout;
		if (l == null || l.view != v || !l.small.getFont().equals(small.getFont()) || !l.bold.getFont().equals(bold.getFont()))
		{
			// Clipping, the plaque's shape and its paints are worked out once per view, not every frame.
			l = new Layout(v, small, bold);
			layout = l;
		}

		g.setPaint(l.fill);
		g.fill(l.shape);
		g.setStroke(STROKE);
		g.setColor(RIM);
		g.draw(l.shape);

		int y = PAD + small.getAscent();
		g.setFont(Theme.small());
		shadow(g, v.label, PAD, y, Theme.GOLD);
		if (l.pct != null)
		{
			shadow(g, l.pct, WIDTH - PAD - l.pctWidth, y, l.pctColor);
		}

		y += bold.getAscent() + 2;
		g.setFont(Theme.title());
		shadow(g, l.title, PAD, y, Theme.GOLD_BRIGHT);

		y += 5;
		g.setColor(Theme.INK_DEEP);
		g.fillRoundRect(PAD, y, BAR_W, 3, 3, 3);
		if (l.fillW > 0)
		{
			g.setPaint(BAR);
			g.fillRoundRect(PAD, y, l.fillW, 3, 3, 3);
		}

		if (l.step != null)
		{
			y += 4 + small.getAscent() + 2;
			g.setFont(Theme.small());
			shadow(g, l.step, PAD, y, Theme.TEXT);
		}
		return l.size;
	}

	private static final int BAR_W = WIDTH - PAD * 2;
	private static final BasicStroke STROKE = new BasicStroke(1f);
	private static final Color RIM = Theme.alpha(Theme.GOLD, 150);
	private static final Color FILL_TOP = Theme.alpha(Theme.CARD_TOP, 230);
	private static final Color FILL_BOTTOM = Theme.alpha(Theme.INK_DEEP, 230);
	private static final GradientPaint BAR = new GradientPaint(PAD, 0, Theme.progressColor(0), PAD + BAR_W, 0, Theme.progressColor(1));

	private volatile Layout layout;

	/** Everything about the plaque that depends only on the view and the fonts. */
	private static final class Layout
	{
		final View view;
		final FontMetrics small;
		final FontMetrics bold;
		final String title;
		final String step;
		final String pct;
		final int pctWidth;
		final Color pctColor;
		final int fillW;
		final RoundRectangle2D shape;
		final GradientPaint fill;
		final Dimension size;

		Layout(View v, FontMetrics small, FontMetrics bold)
		{
			view = v;
			this.small = small;
			this.bold = bold;
			title = clip(v.title, bold, WIDTH - PAD * 2);
			step = v.step == null ? null : clip(v.step, small, WIDTH - PAD * 2);
			pct = v.progress >= 0 ? Math.round(v.progress * 100) + "%" : null;
			pctWidth = pct == null ? 0 : small.stringWidth(pct);
			pctColor = Theme.progressColor(Math.max(0, v.progress));
			fillW = v.progress < 0 ? 0 : (int) Math.round(BAR_W * Math.min(1, v.progress));
			int height = PAD + small.getHeight() + bold.getHeight() + 8 + (step != null ? small.getHeight() + 2 : 0) + PAD - 2;
			shape = new RoundRectangle2D.Float(0.5f, 0.5f, WIDTH - 1, height - 1, 10, 10);
			fill = new GradientPaint(0, 0, FILL_TOP, 0, height, FILL_BOTTOM);
			size = new Dimension(WIDTH, height);
		}
	}

	private static void shadow(Graphics2D g, String text, int x, int y, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	private static String clip(String text, FontMetrics fm, int width)
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
