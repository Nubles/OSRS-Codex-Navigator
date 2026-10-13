package com.codex.navigator.ui;

import com.codex.navigator.core.Step;
import java.awt.BorderLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** The single most important line in the plugin: what to do next, set into a dark inlay. */
public class NextStepBox extends JPanel
{
	public NextStepBox(Step step, int textWidth)
	{
		super(new BorderLayout(8, 0));
		setOpaque(false);
		setBorder(new EmptyBorder(7, 8, 7, 8));

		JPanel glyph = new JPanel(new BorderLayout());
		glyph.setOpaque(false);
		glyph.setBorder(new EmptyBorder(2, 0, 0, 0));
		glyph.add(new Glyph(step == null ? Step.Status.DONE : step.getStatus(), 14), BorderLayout.NORTH);
		add(glyph, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.add(Ui.label(step == null ? "COMPLETE" : "NEXT STEP", Theme.small(), Theme.GOLD));
		String title = step == null ? "Nothing left to do here" : verb(step) + step.getTitle();
		JLabel titleLabel = Ui.wrapped(title, Theme.body(), Theme.TEXT, textWidth);
		text.add(titleLabel);
		if (step != null && step.getDetail() != null && !step.getDetail().isEmpty())
		{
			text.add(Ui.wrapped(step.getDetail(), Theme.small(), Theme.TEXT_MUTED, textWidth));
		}
		add(text, BorderLayout.CENTER);
	}

	private static String verb(Step step)
	{
		if (step.getKind() != Step.Kind.QUEST)
		{
			return "";
		}
		return step.getStatus() == Step.Status.IN_PROGRESS ? "Continue " : "Start ";
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 8, 8);
		g.setPaint(new GradientPaint(0, 0, Theme.INK_DEEP, 0, h, Theme.mix(Theme.INK_DEEP, Theme.TEAL, 0.06)));
		g.fill(shape);
		g.setColor(Theme.alpha(Theme.TEAL, 70));
		g.draw(shape);
		g.dispose();
		super.paintComponent(graphics);
	}
}
