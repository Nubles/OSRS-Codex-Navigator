package com.codex.navigator.ui;

import com.codex.navigator.core.Step;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
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

		// The name gets the full width; the tag sits on the quieter second line so it never squeezes the name,
		// unless the second line is the longer one, when it moves up beside a short name instead.
		JLabel name = Ui.label(title, Theme.body(), Theme.TEXT);
		name.setToolTipText(title);
		JLabel sub = null;
		if (detail != null && !detail.isEmpty())
		{
			sub = Ui.label(detail, Theme.small(), Theme.TEXT_MUTED);
			sub.setToolTipText(detail);
		}
		card.add(new Lines(name, sub, trailing), BorderLayout.CENTER);

		if (onClick != null)
		{
			card.onClick(onClick);
		}
		return card;
	}

	/**
	 * The name over its detail line, with the tag at the right of whichever line has room for it: the detail line
	 * first, the name's line when the detail would be cut and the name would not. Text that still does not fit is
	 * cut with an ellipsis rather than widening the card.
	 */
	static final class Lines extends JPanel
	{
		private final JLabel name;
		private final JLabel sub;
		private final JComponent tag;

		Lines(JLabel name, JLabel sub, JComponent tag)
		{
			super(null);
			setOpaque(false);
			this.name = name;
			this.sub = sub;
			this.tag = tag;
			add(name);
			if (sub != null)
			{
				add(sub);
			}
			if (tag != null)
			{
				add(tag);
			}
		}

		/** True when the tag goes on the name's line at this width. */
		boolean tagOnTop(int width)
		{
			if (tag == null)
			{
				return false;
			}
			if (sub == null)
			{
				return true;
			}
			int room = width - tag.getPreferredSize().width - GAP;
			return sub.getPreferredSize().width > room && name.getPreferredSize().width <= room;
		}

		@Override
		public Dimension getPreferredSize()
		{
			int h = name.getPreferredSize().height + (sub == null ? 0 : sub.getPreferredSize().height);
			if (tag != null && sub == null)
			{
				h = Math.max(h, tag.getPreferredSize().height);
			}
			// Width is the card's to give: never ask for more than the smallest so long names clip, not widen.
			return new Dimension(10, h);
		}

		@Override
		public Dimension getMinimumSize()
		{
			return new Dimension(10, getPreferredSize().height);
		}

		@Override
		public void doLayout()
		{
			int w = getWidth();
			boolean top = tagOnTop(w);
			Dimension tp = tag == null ? new Dimension(0, 0) : tag.getPreferredSize();
			int nameH = name.getPreferredSize().height;
			int nameRoom = top ? w - tp.width - GAP : w;
			name.setBounds(0, 0, Math.max(0, nameRoom), nameH);
			if (sub != null)
			{
				int subH = sub.getPreferredSize().height;
				int subRoom = tag != null && !top ? w - tp.width - GAP : w;
				sub.setBounds(0, nameH, Math.max(0, subRoom), subH);
				if (tag != null && !top)
				{
					tag.setBounds(w - tp.width, nameH + (subH - tp.height) / 2, tp.width, tp.height);
				}
			}
			if (tag != null && top)
			{
				tag.setBounds(w - tp.width, (nameH - tp.height) / 2, tp.width, tp.height);
			}
		}

		private static final int GAP = 6;
	}
}
