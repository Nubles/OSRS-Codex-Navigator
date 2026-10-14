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

		String title = clip(v.title, bold, WIDTH - PAD * 2);
		String step = v.step == null ? null : clip(v.step, small, WIDTH - PAD * 2);
		int height = PAD + small.getHeight() + bold.getHeight() + 8 + (step != null ? small.getHeight() + 2 : 0) + PAD - 2;

		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, WIDTH - 1, height - 1, 10, 10);
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 230), 0, height, Theme.alpha(Theme.INK_DEEP, 230)));
		g.fill(shape);
		g.setStroke(new BasicStroke(1f));
		g.setColor(Theme.alpha(Theme.GOLD, 150));
		g.draw(shape);

		int y = PAD + small.getAscent();
		g.setFont(Theme.small());
		shadow(g, v.label, PAD, y, Theme.GOLD);
		if (v.progress >= 0)
		{
			String pct = Math.round(v.progress * 100) + "%";
			shadow(g, pct, WIDTH - PAD - small.stringWidth(pct), y, Theme.progressColor(v.progress));
		}

		y += bold.getAscent() + 2;
		g.setFont(Theme.title());
		shadow(g, title, PAD, y, Theme.GOLD_BRIGHT);

		y += 5;
		int barW = WIDTH - PAD * 2;
		g.setColor(Theme.INK_DEEP);
		g.fillRoundRect(PAD, y, barW, 3, 3, 3);
		int fill = v.progress < 0 ? 0 : (int) Math.round(barW * Math.min(1, v.progress));
		if (fill > 0)
		{
			g.setPaint(new GradientPaint(PAD, 0, Theme.progressColor(0), PAD + barW, 0, Theme.progressColor(1)));
			g.fillRoundRect(PAD, y, fill, 3, 3, 3);
		}

		if (step != null)
		{
			y += 4 + small.getAscent() + 2;
			g.setFont(Theme.small());
			shadow(g, step, PAD, y, Theme.TEXT);
		}
		return new Dimension(WIDTH, height);
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
