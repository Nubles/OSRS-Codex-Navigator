package com.codex.navigator.ui;

import com.codex.navigator.core.Effort;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.PathSolver;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The fastest path to a big goal on its path page: how long the best order takes and what it saves, the critical
 * path as one spine of quests and training, the skills that gate it, the order itself, and a button that makes the
 * goal lead the goal board other plugins read.
 */
final class FastestView
{
	/** Steps listed before the rest fold behind a toggle. */
	static final int SHOWN = 10;
	private static final int GATES_SHOWN = 5;

	private FastestView()
	{
	}

	static void add(StackPanel stack, Navigation nav, PathSolver.Solution s, boolean showAll, Runnable toggleAll)
	{
		if (s == null || s.isEmpty())
		{
			return;
		}
		stack.add(new SectionHeader("Fastest path", Effort.format(s.getHours())));
		stack.add(summary(nav, s));
		if (!s.getGates().isEmpty())
		{
			stack.add(gates(nav, s));
		}
		stack.add(order(nav, s, showAll, toggleAll));
	}

	/** The total, the saving, the critical spine and the goal board button. */
	static Card summary(Navigation nav, PathSolver.Solution s)
	{
		Card card = new Card(new BorderLayout(0, 6));
		card.setBorder(new EmptyBorder(8, 9, 8, 9));
		card.highlighted(true);
		JPanel top = new JPanel();
		top.setOpaque(false);
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		JLabel total = Ui.label(Effort.format(s.getHours()) + " in all", Theme.title(), Theme.GOLD_BRIGHT);
		total.setAlignmentX(0f);
		top.add(total);
		JLabel split = Ui.label(Effort.format(s.getQuestHours()).substring(1) + " of quests  ·  "
			+ Effort.format(s.getTrainHours()).substring(1) + " of training", Theme.small(), Theme.TEXT_MUTED);
		split.setAlignmentX(0f);
		top.add(split);
		JLabel saving = Ui.wrapped(savingLine(s), Theme.small(), s.getSaved() >= 0.5 ? Theme.TEAL : Theme.TEXT_MUTED, 186);
		saving.setAlignmentX(0f);
		top.add(saving);
		card.add(top, BorderLayout.NORTH);

		JPanel middle = new JPanel(new BorderLayout(0, 3));
		middle.setOpaque(false);
		List<PathSolver.Leg> critical = s.critical();
		JPanel spineHead = new JPanel(new BorderLayout());
		spineHead.setOpaque(false);
		spineHead.add(Ui.label("CRITICAL PATH", Theme.small(), Theme.GOLD), BorderLayout.WEST);
		spineHead.add(Ui.label(critical.size() + (critical.size() == 1 ? " step  ·  " : " steps  ·  ")
			+ Effort.format(s.criticalHours()).substring(1), Theme.small(), Theme.TEXT_FAINT), BorderLayout.EAST);
		middle.add(spineHead, BorderLayout.NORTH);
		middle.add(new Spine(critical), BorderLayout.CENTER);
		middle.add(Ui.wrapped(spineLine(critical), Theme.small(), Theme.TEXT_MUTED, 186), BorderLayout.SOUTH);
		card.add(middle, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		buttons.setOpaque(false);
		Goal focus = nav.goals().focus();
		if (s.getGoal().equals(focus))
		{
			buttons.add(new Pill("Leads the goal board", Theme.TEAL));
		}
		else
		{
			buttons.add(new CodexButton("Set as goal board", true, () -> nav.setAsGoalBoard(s.getGoal())));
		}
		card.add(buttons, BorderLayout.SOUTH);
		card.setToolTipText("<html>Quests first whenever one can be started, since their experience only ever shortens"
			+ " training later.<br>When none can, the quest that takes least training to open is opened next.<br>"
			+ "The goal board leads with your focus goal, and other Codex plugins read its order.</html>");
		return card;
	}

	/** "Banks 182k quest experience first: ~9 h less than training every level up front." */
	static String savingLine(PathSolver.Solution s)
	{
		String banked = s.getXpBanked() >= 1 ? "Banks " + xp(s.getXpBanked()) + " quest experience first" : null;
		if (s.getSaved() >= 0.5)
		{
			return (banked == null ? "This order" : banked) + ": " + Effort.format(s.getSaved())
				+ " less than training every level up front.";
		}
		return banked != null ? banked + " toward the levels on the way." : "No quest on the way rewards a level it needs.";
	}

	/** "Longest wait: Train Agility to 70 (~31 h)." */
	static String spineLine(List<PathSolver.Leg> critical)
	{
		PathSolver.Leg longest = null;
		for (PathSolver.Leg leg : critical)
		{
			if (longest == null || leg.getHours() > longest.getHours())
			{
				longest = leg;
			}
		}
		if (longest == null)
		{
			return "";
		}
		return "Longest wait: " + longest.getTitle() + " (" + Effort.format(longest.getHours()) + ").";
	}

	/** The skills whose training gates the goal, most hours first, with what each opens. */
	private static Card gates(Navigation nav, PathSolver.Solution s)
	{
		Card card = new Card(new BorderLayout(0, 4));
		card.setBorder(new EmptyBorder(7, 9, 7, 9));
		JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(false);
		head.add(Ui.label("GATED BY", Theme.small(), Theme.GOLD), BorderLayout.WEST);
		head.add(Ui.label(s.getGates().size() + (s.getGates().size() == 1 ? " skill" : " skills"), Theme.small(),
			Theme.TEXT_FAINT), BorderLayout.EAST);
		card.add(head, BorderLayout.NORTH);
		StackPanel rows = new StackPanel(5, 0);
		double most = s.getGates().get(0).getHours();
		int shown = 0;
		for (PathSolver.Gate gate : s.getGates())
		{
			rows.add(gateRow(nav.skillIcon(gate.getSkill()), gate, most));
			if (++shown >= GATES_SHOWN)
			{
				break;
			}
		}
		card.add(rows, BorderLayout.CENTER);
		if (s.getGates().size() > GATES_SHOWN)
		{
			card.add(Ui.label("+" + (s.getGates().size() - GATES_SHOWN) + " more, shorter", Theme.small(), Theme.TEXT_FAINT),
				BorderLayout.SOUTH);
		}
		return card;
	}

	private static JPanel gateRow(BufferedImage icon, PathSolver.Gate gate, double most)
	{
		JPanel row = new JPanel(new BorderLayout(6, 1));
		row.setOpaque(false);
		JLabel badge = new JLabel(icon == null ? null : new ImageIcon(icon));
		badge.setPreferredSize(new Dimension(18, 18));
		row.add(badge, BorderLayout.WEST);
		JPanel text = new JPanel(new BorderLayout(4, 1));
		text.setOpaque(false);
		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setOpaque(false);
		top.add(Ui.label(gate.getSkill().getName() + "  " + gate.getFrom() + " to " + gate.getTo(), Theme.small(), Theme.TEXT),
			BorderLayout.WEST);
		top.add(Ui.label(Effort.format(gate.getHours()), Theme.small(), Theme.EMBER), BorderLayout.EAST);
		text.add(top, BorderLayout.NORTH);
		text.add(new Bar(most <= 0 ? 0 : gate.getHours() / most), BorderLayout.CENTER);
		int n = gate.getOpens().size();
		String opens = n == 0 ? "For the goal itself" : "Opens " + gate.getOpens().get(0) + (n > 1 ? " +" + (n - 1) : "");
		JLabel sub = Ui.label(opens, Theme.small(), Theme.TEXT_FAINT);
		sub.setToolTipText(n == 0 ? null : "<html>Opens " + Ui.escape(String.join(", ", gate.getOpens())) + "</html>");
		text.add(sub, BorderLayout.SOUTH);
		row.add(text, BorderLayout.CENTER);
		return row;
	}

	/** The order, numbered, critical steps marked; the first steps only until the rest are asked for. */
	private static Card order(Navigation nav, PathSolver.Solution s, boolean showAll, Runnable toggleAll)
	{
		Card card = new Card(new BorderLayout(0, 2));
		card.setBorder(new EmptyBorder(7, 9, 7, 9));
		JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(false);
		head.add(Ui.label("THE ORDER", Theme.small(), Theme.GOLD), BorderLayout.WEST);
		head.add(Ui.label("gold seal: critical", Theme.small(), Theme.TEXT_FAINT), BorderLayout.EAST);
		card.add(head, BorderLayout.NORTH);
		StackPanel rows = new StackPanel(2, 0);
		List<PathSolver.Leg> legs = s.getLegs();
		int limit = showAll ? legs.size() : Math.min(SHOWN, legs.size());
		for (int i = 0; i < limit; i++)
		{
			PathSolver.Leg leg = legs.get(i);
			Runnable open = leg.getQuest() != null && !leg.getQuest().equals(s.getGoal().getName())
				? () -> nav.open(Goal.quest(leg.getQuest())) : null;
			rows.add(legRow(i + 1, leg, open));
		}
		card.add(rows, BorderLayout.CENTER);
		if (legs.size() > SHOWN)
		{
			JLabel toggle = Ui.label(showAll ? "Show the first " + SHOWN : "Show all " + legs.size() + " steps",
				Theme.small(), Theme.GOLD);
			toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			toggle.setBorder(new EmptyBorder(4, 0, 0, 0));
			toggle.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseReleased(MouseEvent e)
				{
					toggleAll.run();
				}
			});
			card.add(toggle, BorderLayout.SOUTH);
		}
		return card;
	}

	static JPanel legRow(int number, PathSolver.Leg leg, Runnable open)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		row.add(new Number(number, leg), BorderLayout.WEST);
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		Color tone = leg.getKind() == PathSolver.Leg.Kind.TRAIN ? Theme.TEXT : Theme.GOLD_BRIGHT;
		JLabel title = Ui.label(leg.getTitle(), Theme.small(), tone);
		title.setToolTipText(leg.getTitle());
		text.add(title);
		String detail = detail(leg);
		if (detail != null)
		{
			JLabel sub = Ui.label(detail, Theme.small(), Theme.TEXT_FAINT);
			sub.setToolTipText(detail);
			text.add(sub);
		}
		text.setMinimumSize(new Dimension(10, 10));
		text.setPreferredSize(new Dimension(10, text.getPreferredSize().height));
		row.add(text, BorderLayout.CENTER);
		row.add(Ui.label(Effort.format(leg.getHours()), Theme.small(), Theme.TEXT_MUTED), BorderLayout.EAST);
		if (open != null)
		{
			row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			row.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseReleased(MouseEvent e)
				{
					open.run();
				}
			});
		}
		return row;
	}

	/** The quiet line under a step: what training opens, what a quest rewards, why a quest outside the goal is here. */
	static String detail(PathSolver.Leg leg)
	{
		switch (leg.getKind())
		{
			case TRAIN:
				return "From " + leg.getFrom() + (leg.getOpens() == null ? ", for the goal itself" : ", opens " + leg.getOpens());
			case POINTS:
				return "For quest points";
			case CHECK:
				return "Not in the Codex data: check in game";
			default:
			{
				StringBuilder out = new StringBuilder();
				if (leg.isBoosted())
				{
					out.append("Boost on the day");
				}
				leg.getXp().entrySet().stream()
					.sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
					.limit(2)
					.forEach(e -> out.append(out.length() == 0 ? "" : "  ·  ").append("+").append(xp(e.getValue()))
						.append(" ").append(e.getKey().getName()));
				return out.length() == 0 ? null : out.toString();
			}
		}
	}

	/** Experience by the suite's number rule: in full under 10,000, whole thousands under a million, then 1dp M. */
	static String xp(double value)
	{
		long v = Math.round(value);
		if (v < 10_000)
		{
			return String.format(Locale.ENGLISH, "%,d", v);
		}
		if (v < 1_000_000)
		{
			return Math.round(v / 1000.0) + "k";
		}
		return String.format(Locale.ENGLISH, "%.1fM", v / 1_000_000.0);
	}

	/** A step's number in a small seal: gold and filled on the critical path, quiet otherwise. */
	private static final class Number extends JComponent
	{
		private final int number;
		private final PathSolver.Leg leg;

		Number(int number, PathSolver.Leg leg)
		{
			this.number = number;
			this.leg = leg;
			setFont(Theme.small());
			setPreferredSize(new Dimension(22, 16));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			boolean train = leg.getKind() == PathSolver.Leg.Kind.TRAIN;
			RoundRectangle2D seal = new RoundRectangle2D.Float(0.5f, 1.5f, 20, 13, 6, 6);
			g.setColor(leg.isCritical() ? Theme.alpha(Theme.GOLD, 60) : Theme.INK_DEEP);
			g.fill(seal);
			g.setColor(leg.isCritical() ? Theme.GOLD : Theme.STROKE);
			g.draw(seal);
			g.setFont(getFont());
			FontMetrics fm = g.getFontMetrics();
			String text = String.valueOf(number);
			g.setColor(leg.isCritical() ? Theme.GOLD_BRIGHT : train ? Theme.TEXT_MUTED : Theme.TEXT);
			g.drawString(text, 11 - fm.stringWidth(text) / 2f, 12);
			g.dispose();
		}
	}

	/** A thin gauge of a skill's hours against the most any skill takes. */
	private static final class Bar extends JComponent
	{
		private final double share;

		Bar(double share)
		{
			this.share = Math.max(0, Math.min(1, share));
			setPreferredSize(new Dimension(10, 5));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			g.setColor(Theme.INK_DEEP);
			g.fill(new RoundRectangle2D.Float(0, 0, w - 1, 4, 4, 4));
			float fill = (float) Math.max(4, (w - 1) * share);
			g.setPaint(new GradientPaint(0, 0, Theme.GOLD_DIM, fill, 0, Theme.EMBER));
			g.fill(new RoundRectangle2D.Float(0, 0, fill, 4, 4, 4));
			g.dispose();
		}
	}

	/**
	 * The critical path as one spine: each step a segment as long as its share of the hours, training in ember and
	 * quests as gold seals, so the long waits stand out at a glance.
	 */
	static final class Spine extends JComponent
	{
		private static final int HEIGHT = 18;
		private final List<PathSolver.Leg> legs;

		Spine(List<PathSolver.Leg> legs)
		{
			this.legs = legs;
			setPreferredSize(new Dimension(10, HEIGHT));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			float left = 4;
			float span = w - 8;
			float mid = HEIGHT / 2f;
			double total = 0;
			for (PathSolver.Leg leg : legs)
			{
				total += leg.getHours();
			}
			// Each quest keeps a sliver even when short, so a chain of quick quests still reads as a chain.
			float sliver = legs.isEmpty() ? 0 : Math.min(6, span * 0.4f / Math.max(1, legs.size()));
			float rest = span - sliver * legs.size();
			g.setColor(Theme.INK_DEEP);
			g.fill(new RoundRectangle2D.Float(left - 2, mid - 4, span + 4, 8, 8, 8));
			float x = left;
			for (PathSolver.Leg leg : legs)
			{
				float len = sliver + (float) (total <= 0 ? 0 : rest * leg.getHours() / total);
				if (leg.getKind() == PathSolver.Leg.Kind.TRAIN)
				{
					g.setPaint(new GradientPaint(x, 0, Theme.mix(Theme.EMBER, Theme.GOLD_DIM, 0.4), x + len, 0, Theme.EMBER));
					g.fill(new RoundRectangle2D.Float(x, mid - 3, Math.max(1, len - 1), 6, 6, 6));
				}
				else
				{
					g.setColor(Theme.alpha(Theme.GOLD, 90));
					g.fill(new RoundRectangle2D.Float(x, mid - 1.5f, Math.max(1, len - 1), 3, 3, 3));
					float cx = x + len / 2;
					Path2D seal = new Path2D.Float();
					seal.moveTo(cx, mid - 4);
					seal.lineTo(cx + 3.5f, mid);
					seal.lineTo(cx, mid + 4);
					seal.lineTo(cx - 3.5f, mid);
					seal.closePath();
					g.setColor(Theme.INK_DEEP);
					g.fill(seal);
					g.setColor(Theme.GOLD_BRIGHT);
					g.setStroke(new BasicStroke(1f));
					g.draw(seal);
				}
				x += len;
			}
			// The flag at the goal.
			g.setColor(Theme.GOLD_BRIGHT);
			g.fillRect(Math.round(left + span) - 1, 1, 2, HEIGHT - 2);
			Path2D flag = new Path2D.Float();
			flag.moveTo(left + span - 1, 1);
			flag.lineTo(left + span - 8, 4);
			flag.lineTo(left + span - 1, 7);
			flag.closePath();
			g.fill(flag);
			g.dispose();
		}
	}
}
