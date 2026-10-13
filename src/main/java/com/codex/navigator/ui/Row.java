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

		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		JLabel name = Ui.label(title, Theme.body(), Theme.TEXT);
		name.setToolTipText(title);
		text.add(name);
		if (detail != null && !detail.isEmpty())
		{
			JLabel sub = Ui.label(detail, Theme.small(), Theme.TEXT_MUTED);
			sub.setToolTipText(detail);
			text.add(sub);
		}
		// Let long names clip with an ellipsis rather than push the tag off the card.
		text.setMinimumSize(new Dimension(10, 10));
		text.setPreferredSize(new Dimension(10, text.getPreferredSize().height));
		card.add(text, BorderLayout.CENTER);

		if (trailing != null)
		{
			JPanel holder = new JPanel(new BorderLayout());
			holder.setOpaque(false);
			holder.add(trailing, BorderLayout.NORTH);
			card.add(holder, BorderLayout.EAST);
		}
		if (onClick != null)
		{
			card.onClick(onClick);
		}
		return card;
	}
}
