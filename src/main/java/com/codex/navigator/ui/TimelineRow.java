package com.codex.navigator.ui;

import com.codex.navigator.core.Step;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** One stop on a goal's path, threaded to its neighbours by a fine vertical line. */
class TimelineRow extends JPanel
{
	private static final int RAIL_X = 8;
	private static final int GLYPH = 14;
	private static final int GLYPH_TOP = 4;

	private final Step.Status status;
	private final boolean first;
	private final boolean last;
	private boolean hover;

	TimelineRow(Step step, boolean first, boolean last, Runnable onClick)
	{
		super(new BorderLayout(6, 0));
		this.status = step.getStatus();
		this.first = first;
		this.last = last;
		setOpaque(false);
		setBorder(new EmptyBorder(0, 0, 0, 2));

		JPanel rail = new JPanel(new BorderLayout());
		rail.setOpaque(false);
		rail.setBorder(new EmptyBorder(GLYPH_TOP, RAIL_X - GLYPH / 2 + 1, 0, 0));
		rail.add(new Glyph(status, GLYPH), BorderLayout.NORTH);
		add(rail, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBorder(new EmptyBorder(3, 0, 7, 0));
		JLabel title = Ui.wrapped(step.getTitle(), Theme.body(), status == Step.Status.DONE ? Theme.TEXT_MUTED : Theme.TEXT, 158);
		text.add(title);
		if (step.getDetail() != null && !step.getDetail().isEmpty() && status != Step.Status.DONE)
		{
			text.add(Ui.wrapped(step.getDetail(), Theme.small(), Glyph.color(status), 158));
		}
		add(text, BorderLayout.CENTER);

		if (onClick != null)
		{
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			MouseAdapter mouse = new MouseAdapter()
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
					if (!contains(SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), TimelineRow.this)))
					{
						hover = false;
						repaint();
					}
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					if (SwingUtilities.isLeftMouseButton(e)
						&& contains(SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), TimelineRow.this)))
					{
						onClick.run();
					}
				}
			};
			addMouseListener(mouse);
			Ui.forwardMouse(this, mouse);
		}
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		if (hover)
		{
			g.setColor(Theme.alpha(Theme.GOLD, 18));
			g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
		}
		int x = RAIL_X + 1;
		int glyphTop = GLYPH_TOP;
		int glyphBottom = GLYPH_TOP + GLYPH;
		g.setStroke(new BasicStroke(1.2f));
		g.setColor(Theme.alpha(Theme.GOLD_DIM, 140));
		if (!first)
		{
			g.drawLine(x, 0, x, glyphTop - 1);
		}
		if (!last)
		{
			g.drawLine(x, glyphBottom + 1, x, getHeight());
		}
		g.dispose();
		super.paintComponent(graphics);
	}
}
