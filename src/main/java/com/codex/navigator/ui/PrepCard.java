package com.codex.navigator.ui;

import com.codex.navigator.core.Boosts;
import com.codex.navigator.core.Effort;
import com.codex.navigator.core.Holdings;
import com.codex.navigator.core.QuestPrep;
import com.codex.navigator.core.SkillGap;
import com.codex.navigator.core.Step;
import com.codex.navigator.data.QuestItems;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.QuestState;

/**
 * Getting ready for one quest, on one card: what still blocks it and for how long, the levels a pie or potion covers,
 * and what to bring, each item marked with you, in the bank, to get or got in the quest. The lists are the OSRS
 * Wiki's; where an item is a kind of thing or the bank has not been seen, the card says so rather than guess.
 */
final class PrepCard
{
	/** Text width inside the card at the side panel's width. */
	static final int TEXT_W = 176;

	private PrepCard()
	{
	}

	/**
	 * @param title     true to name the quest at the top (on the Compass); false on the quest's own page
	 * @param openQuest opens a quest's page when its name is clicked, or null
	 * @param now       the time, in epoch milliseconds, the bank's age is told from
	 */
	static Card build(QuestPrep prep, boolean title, Consumer<String> openQuest, long now)
	{
		Card card = new Card(new BorderLayout());
		card.setBorder(new EmptyBorder(9, 10, 9, 10));
		StackPanel body = new StackPanel(3, 0);

		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		JLabel kicker = Ui.label(title ? "NEXT QUEST" : "BEFORE YOU SET OFF", Theme.small(), Theme.GOLD);
		head.add(kicker, BorderLayout.WEST);
		head.add(statusPill(prep), BorderLayout.EAST);
		body.add(head);
		if (title)
		{
			JLabel name = Ui.wrapped(prep.getQuest(), Theme.body(), Theme.GOLD_BRIGHT, TEXT_W);
			if (openQuest != null)
			{
				clickable(name, () -> openQuest.accept(prep.getQuest()));
			}
			body.add(name);
		}
		String summary = summary(prep);
		if (summary != null)
		{
			body.add(Ui.wrapped(summary, Theme.small(), Theme.TEXT_MUTED, TEXT_W));
		}

		if (!prep.getBlockers().isEmpty())
		{
			body.add(label("STILL IN THE WAY", Theme.EMBER));
			for (QuestPrep.Blocker b : prep.getBlockers())
			{
				body.add(blockerRow(b, openQuest));
			}
		}
		if (!prep.getBoosts().isEmpty())
		{
			body.add(label("BOOST ON THE DAY", Theme.TEAL));
			for (SkillGap gap : prep.getBoosts())
			{
				body.add(boostRow(prep, gap));
			}
		}

		body.add(label("BRING", Theme.GOLD));
		if (!prep.isListed())
		{
			body.add(Ui.wrapped("Navigator does not carry this quest's item list yet. Its OSRS Wiki page has it.",
				Theme.small(), Theme.TEXT_FAINT, TEXT_W));
		}
		else if (prep.getItems().isEmpty())
		{
			body.add(Ui.wrapped("Nothing: the wiki lists no items for this quest.", Theme.small(), Theme.TEXT_MUTED, TEXT_W));
		}
		else
		{
			for (QuestPrep.Line line : prep.getItems())
			{
				body.add(itemRow(line, true));
			}
		}
		if (!prep.getRecommended().isEmpty())
		{
			body.add(label("WORTH BRINGING", Theme.TEXT_MUTED));
			for (QuestPrep.Line line : prep.getRecommended())
			{
				body.add(itemRow(line, false));
			}
		}
		if (prep.isListed() && !(prep.getItems().isEmpty() && prep.getRecommended().isEmpty()))
		{
			JLabel foot = Ui.wrapped(bankLine(prep.getHoldings(), now), Theme.small(), Theme.TEXT_FAINT, TEXT_W);
			foot.setBorder(new EmptyBorder(4, 0, 0, 0));
			foot.setToolTipText("Item lists from each quest's OSRS Wiki page");
			body.add(foot);
		}
		card.add(body, BorderLayout.CENTER);
		return card;
	}

	/** "Ready", "Boost first", "~3 h to go" or "In progress". */
	static Pill statusPill(QuestPrep prep)
	{
		if (prep.getState() == QuestState.IN_PROGRESS)
		{
			return new Pill("In progress", Theme.GOLD_BRIGHT);
		}
		if (!prep.isReady())
		{
			double hours = prep.getHoursToReady();
			return new Pill(hours > 0 ? Effort.format(hours) + " to go" : "Blocked", Theme.EMBER);
		}
		return new Pill(prep.getBoosts().isEmpty() ? "Ready" : "Boost and go", Theme.TEAL);
	}

	/**
	 * One line over the lists: "3 of 5 to bring are ready · 2 to get". Null when there is nothing to count. Items
	 * got in the quest and kinds of thing count as ready: nothing has to be fetched for them.
	 */
	static String summary(QuestPrep prep)
	{
		if (prep.getState() == QuestState.IN_PROGRESS)
		{
			return "Started: some of these may already be handed in.";
		}
		int total = prep.getItems().size();
		if (!prep.isListed() || total == 0)
		{
			return null;
		}
		int get = prep.toGet();
		if (get == 0)
		{
			return total == 1 ? "The one item to bring is ready." : "All " + total + " items to bring are ready.";
		}
		int unseen = prep.count(QuestPrep.Have.UNSEEN);
		if (unseen == get)
		{
			return get + " of " + total + " not with you; the bank is unseen.";
		}
		return (total - get) + " of " + total + " ready  ·  " + get + " to get";
	}

	/** Where the bank figures came from and how old they are. */
	static String bankLine(Holdings holdings, long now)
	{
		switch (holdings.getBankSource())
		{
			case LIVE:
				return "Bank as you last opened it" + ago(holdings.getBankSeenAt(), now) + ".";
			case STEWARD:
				return "Bank as Codex Bank Steward last saw it" + ago(holdings.getBankSeenAt(), now) + ".";
			default:
				return "Bank not seen yet: open it once, or share it from Codex Bank Steward, and the list checks it too.";
		}
	}

	/** ", 3 h ago"; empty when the time is unknown or in the future. */
	static String ago(long at, long now)
	{
		if (at <= 0 || now < at)
		{
			return "";
		}
		long minutes = (now - at) / 60_000L;
		if (minutes < 1)
		{
			return ", just now";
		}
		if (minutes < 60)
		{
			return ", " + minutes + " min ago";
		}
		long hours = minutes / 60;
		if (hours < 48)
		{
			return ", " + hours + " h ago";
		}
		return ", " + (hours / 24) + " days ago";
	}

	/** The words beside an item: "with you", "in bank", "3 of 10", "missing", "in quest". */
	static String haveText(QuestPrep.Line line)
	{
		long qty = line.getItem().qty;
		switch (line.getHave())
		{
			case CARRIED:
				return "with you";
			case BANKED:
				return line.getCarried() > 0 ? "part banked" : "in bank";
			case SHORT:
				return amount(line.getCarried() + line.getBanked()) + " of " + amount(qty);
			case MISSING:
				return "to get";
			case IN_QUEST:
				return "in quest";
			case UNSEEN:
				return line.getCarried() > 0 ? amount(line.getCarried()) + " of " + amount(qty) : "not on you";
			default:
				return "";
		}
	}

	static Color haveColor(QuestPrep.Have have)
	{
		switch (have)
		{
			case CARRIED:
				return Theme.GREEN;
			case BANKED:
				return Theme.GOLD;
			case SHORT:
			case MISSING:
				return Theme.EMBER;
			case IN_QUEST:
				return Theme.TEAL;
			default:
				return Theme.TEXT_FAINT;
		}
	}

	/** "Bones ×25": the first name only; alternatives go on the line under it. */
	static String itemText(QuestItems.Item item)
	{
		return item.name + (item.qty > 1 ? " \u00d7" + amount(item.qty) : "");
	}

	/** The quiet line under an item: "or Pure essence", then the wiki's note. Null when there is neither. */
	static String detailText(QuestItems.Item item)
	{
		StringBuilder sb = new StringBuilder();
		if (item.or != null && !item.or.isEmpty())
		{
			sb.append("or ").append(String.join(" or ", item.or));
		}
		if (item.note != null)
		{
			sb.append(sb.length() > 0 ? "; " : "").append(item.note);
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	private static String amount(long n)
	{
		return String.format("%,d", n);
	}

	private static JLabel label(String text, Color color)
	{
		JLabel label = Ui.label(text, Theme.small(), color);
		label.setBorder(new EmptyBorder(6, 0, 1, 0));
		return label;
	}

	private static JComponent blockerRow(QuestPrep.Blocker b, Consumer<String> openQuest)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.setBorder(new EmptyBorder(2, 0, 0, 0));
		holder.add(new Glyph(b.getKind() == QuestPrep.Blocker.Kind.QUEST && "in progress".equals(b.getDetail())
			? Step.Status.IN_PROGRESS : Step.Status.BLOCKED, 12), BorderLayout.NORTH);
		row.add(holder, BorderLayout.WEST);
		JPanel text = new JPanel(new BorderLayout());
		text.setOpaque(false);
		JPanel line = new JPanel(new BorderLayout(6, 0));
		line.setOpaque(false);
		JLabel name = Ui.label(b.getTitle(), Theme.small(), Theme.TEXT);
		name.setMinimumSize(new Dimension(10, name.getPreferredSize().height));
		line.add(name, BorderLayout.CENTER);
		if (b.getHours() > 0)
		{
			line.add(Ui.label(Effort.format(b.getHours()), Theme.small(), Theme.GOLD), BorderLayout.EAST);
		}
		text.add(line, BorderLayout.NORTH);
		if (b.getDetail() != null)
		{
			text.add(Ui.label(b.getDetail(), Theme.small(), Theme.TEXT_FAINT), BorderLayout.CENTER);
		}
		row.add(text, BorderLayout.CENTER);
		if (b.getQuest() != null && openQuest != null)
		{
			clickable(name, () -> openQuest.accept(b.getQuest()));
		}
		return row;
	}

	private static JComponent boostRow(QuestPrep prep, SkillGap gap)
	{
		Boosts.Boost boost = Boosts.of(gap.getSkill());
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.add(mark(QuestPrep.Have.IN_QUEST), BorderLayout.WEST);
		JPanel text = new JPanel(new BorderLayout());
		text.setOpaque(false);
		JPanel line = new JPanel(new BorderLayout(6, 0));
		line.setOpaque(false);
		line.add(Ui.label(gap.getSkill().getName() + " " + gap.getCurrent() + " → " + gap.getTarget(), Theme.small(),
			Theme.TEXT), BorderLayout.CENTER);
		Holdings holdings = prep.getHoldings();
		long have = prep.boostItems(gap);
		String state = have > 0 ? (holdings.carried(boost.getIds()) > 0 ? "with you" : "in bank")
			: holdings.bankKnown() ? "to get" : "";
		if (!state.isEmpty())
		{
			line.add(Ui.label(state, Theme.small(), have > 0 ? (state.equals("with you") ? Theme.GREEN : Theme.GOLD)
				: Theme.EMBER), BorderLayout.EAST);
		}
		text.add(line, BorderLayout.NORTH);
		String item = boost.getItem().replaceFirst("^an? ", "");
		text.add(Ui.label(Character.toUpperCase(item.charAt(0)) + item.substring(1) + " (+" + boost.amount(gap.getCurrent())
			+ ")", Theme.small(), Theme.TEAL), BorderLayout.CENTER);
		row.add(text, BorderLayout.CENTER);
		return row;
	}

	private static JComponent itemRow(QuestPrep.Line line, boolean notes)
	{
		QuestItems.Item item = line.getItem();
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.add(mark(line.getHave()), BorderLayout.WEST);
		JPanel text = new JPanel(new BorderLayout());
		text.setOpaque(false);
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		boolean quiet = line.getHave() == QuestPrep.Have.UNCHECKED || !notes;
		JLabel name = Ui.label(itemText(item), Theme.small(), quiet ? Theme.TEXT_MUTED : Theme.TEXT);
		name.setMinimumSize(new Dimension(10, name.getPreferredSize().height));
		name.setToolTipText(tooltip(item));
		top.add(name, BorderLayout.CENTER);
		String have = haveText(line);
		if (!have.isEmpty())
		{
			top.add(Ui.label(have, Theme.small(), haveColor(line.getHave())), BorderLayout.EAST);
		}
		text.add(top, BorderLayout.NORTH);
		String detail = detailText(item);
		if (notes && detail != null)
		{
			text.add(Ui.wrapped(detail, Theme.small(), Theme.TEXT_FAINT, TEXT_W - 22), BorderLayout.CENTER);
		}
		row.add(text, BorderLayout.CENTER);
		return row;
	}

	private static String tooltip(QuestItems.Item item)
	{
		StringBuilder tip = new StringBuilder(item.name);
		if (item.or != null)
		{
			for (String alt : item.or)
			{
				tip.append(" or ").append(alt);
			}
		}
		if (item.note != null)
		{
			tip.append(": ").append(item.note);
		}
		if (item.duringQuest)
		{
			tip.append(" (can be got during the quest)");
		}
		if (!item.checkable())
		{
			tip.append(" (your choice: not checked)");
		}
		return tip.toString();
	}

	private static void clickable(JLabel label, Runnable action)
	{
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (label.contains(e.getPoint()))
				{
					action.run();
				}
			}
		});
	}

	private static JComponent mark(QuestPrep.Have have)
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.setBorder(new EmptyBorder(2, 0, 0, 0));
		holder.add(new Mark(have), BorderLayout.NORTH);
		return holder;
	}

	/**
	 * The item's mark: a sealed check with you, a gold diamond in the bank (the Codex's bank mark), a half-filled ring
	 * when short, an open ring to get, a lit ring for the quest to hand out, a dotted ring for an unseen bank and a
	 * small dot for a kind of thing.
	 */
	static final class Mark extends JComponent
	{
		private static final int SIZE = 12;
		private final QuestPrep.Have have;

		Mark(QuestPrep.Have have)
		{
			this.have = have;
			Dimension d = new Dimension(SIZE, SIZE);
			setPreferredSize(d);
			setMinimumSize(d);
			setMaximumSize(d);
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			float s = SIZE - 2;
			float o = 1;
			Color c = haveColor(have);
			Ellipse2D circle = new Ellipse2D.Float(o, o, s, s);
			g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			switch (have)
			{
				case CARRIED:
				{
					g.setColor(Theme.alpha(c, 60));
					g.fill(circle);
					g.setColor(c);
					g.draw(circle);
					Path2D check = new Path2D.Float();
					check.moveTo(o + s * 0.28, o + s * 0.52);
					check.lineTo(o + s * 0.44, o + s * 0.68);
					check.lineTo(o + s * 0.74, o + s * 0.34);
					g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g.draw(check);
					break;
				}
				case BANKED:
				{
					Path2D diamond = new Path2D.Float();
					diamond.moveTo(o + s / 2, o);
					diamond.lineTo(o + s, o + s / 2);
					diamond.lineTo(o + s / 2, o + s);
					diamond.lineTo(o, o + s / 2);
					diamond.closePath();
					g.setColor(Theme.alpha(c, 70));
					g.fill(diamond);
					g.setColor(c);
					g.draw(diamond);
					break;
				}
				case SHORT:
				{
					g.setColor(c);
					g.fill(new java.awt.geom.Arc2D.Float(o, o, s, s, 270, 180, java.awt.geom.Arc2D.PIE));
					g.draw(circle);
					break;
				}
				case MISSING:
				{
					g.setColor(c);
					g.draw(circle);
					g.draw(new Line2D.Float(o + s * 0.3f, o + s * 0.5f, o + s * 0.7f, o + s * 0.5f));
					break;
				}
				case IN_QUEST:
				{
					g.setColor(Theme.alpha(c, 50));
					g.fill(circle);
					g.setColor(c);
					g.draw(circle);
					float dot = s * 0.36f;
					g.fill(new Ellipse2D.Float(o + (s - dot) / 2, o + (s - dot) / 2, dot, dot));
					break;
				}
				case UNSEEN:
				{
					g.setColor(c);
					g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
						new float[]{1.5f, 2.2f}, 0));
					g.draw(circle);
					break;
				}
				default:
				{
					g.setColor(Theme.alpha(c, 200));
					float dot = s * 0.3f;
					g.fill(new Ellipse2D.Float(o + (s - dot) / 2, o + (s - dot) / 2, dot, dot));
					break;
				}
			}
			g.dispose();
		}
	}
}
