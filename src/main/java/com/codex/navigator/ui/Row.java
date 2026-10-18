package com.codex.navigator.ui;

import com.codex.navigator.core.Step;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** A compact clickable line: status mark, name, a quiet second line and an optional tag. */
public final class Row
{
	private Row()
	{
	}

	public static Card of(Step.Status status, String title, String detail, JComponent trailing, Color accent, Runnable onClick)
	{
		Card card = new Card(new BorderLayout(8, 0));
		card.setBorder(new EmptyBorder(6, 8, 6, 8));
		if (accent != null)
		{
			card.accent(accent);
		}

		if (status != null)
		{
			JPanel glyphHolder = new JPanel(new BorderLayout());
			glyphHolder.setOpaque(false);
			glyphHolder.add(new Glyph(status, 14), BorderLayout.NORTH);
			glyphHolder.setBorder(new EmptyBorder(1, 0, 0, 0));
			card.add(glyphHolder, BorderLayout.WEST);
		}

		// The name gets the full width; the tag sits on the quieter second line so it never squeezes the name.
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		JLabel name = Ui.label(title, Theme.body(), Theme.TEXT);
		name.setToolTipText(title);
		boolean hasDetail = detail != null && !detail.isEmpty();
		text.add(line(name, hasDetail ? null : trailing));
		if (hasDetail)
		{
			JLabel sub = Ui.label(detail, Theme.small(), Theme.TEXT_MUTED);
			sub.setToolTipText(detail);
			text.add(line(sub, trailing));
		}
		// Let long names clip with an ellipsis rather than widen the card.
		text.setMinimumSize(new Dimension(10, 10));
		text.setPreferredSize(new Dimension(10, text.getPreferredSize().height));
		card.add(text, BorderLayout.CENTER);

		if (onClick != null)
		{
			card.onClick(onClick);
		}
		return card;
	}

	private static JPanel line(JLabel label, JComponent trailing)
	{
		JPanel line = new JPanel(new BorderLayout(6, 0));
		line.setOpaque(false);
		line.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		label.setMinimumSize(new Dimension(10, label.getPreferredSize().height));
		line.add(label, BorderLayout.CENTER);
		if (trailing != null)
		{
			JPanel holder = new JPanel(new BorderLayout());
			holder.setOpaque(false);
			holder.add(trailing, BorderLayout.CENTER);
			line.add(holder, BorderLayout.EAST);
		}
		Dimension pref = line.getPreferredSize();
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
		return line;
	}
}
