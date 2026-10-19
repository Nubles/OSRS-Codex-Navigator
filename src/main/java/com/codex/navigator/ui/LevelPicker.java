package com.codex.navigator.ui;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.Planner;
import com.codex.navigator.data.DiaryDef;
import com.codex.navigator.data.QuestDef;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Skill;

/**
 * Pins a level as a goal in two clicks: a skill from the grid, then one of the levels worth aiming for (the next
 * levels that open a quest or diary tier, the next round ten, and 99). Closed, it is a single line and a button.
 */
final class LevelPicker
{
	/** Level chips offered for a skill. */
	static final int CHOICES = 5;

	private LevelPicker()
	{
	}

	/** The picker card, or null when logged out: the levels it offers start from the account's own. */
	static Card build(Navigation nav)
	{
		AccountSnapshot account = nav.account();
		if (!account.isLoggedIn())
		{
			return null;
		}
		Card card = new Card(new BorderLayout(0, 6));
		card.setBorder(new EmptyBorder(8, 8, 8, 8));
		if (!nav.levelPickerOpen())
		{
			JPanel line = new JPanel(new BorderLayout(6, 0));
			line.setOpaque(false);
			line.add(Ui.label("Training toward a level?", Theme.small(), Theme.TEXT_MUTED), BorderLayout.CENTER);
			line.add(new CodexButton("Pin a level", false, () -> nav.levelPicker(true, null)), BorderLayout.EAST);
			card.add(line, BorderLayout.CENTER);
			return card;
		}

		JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(false);
		Skill chosen = nav.levelSkill();
		head.add(Ui.label(chosen == null ? "PICK A SKILL" : chosen.getName().toUpperCase() + "  ·  LEVEL "
			+ account.level(chosen), Theme.small(), Theme.GOLD), BorderLayout.WEST);
		head.add(link("Close", () -> nav.levelPicker(false, null)), BorderLayout.EAST);
		card.add(head, BorderLayout.NORTH);

		JPanel grid = new JPanel(new GridLayout(0, 6, 3, 3));
		grid.setOpaque(false);
		for (Skill skill : skills())
		{
			grid.add(new SkillTile(nav.skillIcon(skill), skill, skill == chosen, account.level(skill) >= 99,
				() -> nav.levelPicker(true, skill)));
		}
		card.add(grid, BorderLayout.CENTER);

		if (chosen != null)
		{
			Planner planner = nav.planner();
			StackPanel south = new StackPanel(4, 0);
			JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			chips.setOpaque(false);
			String firstOpens = null;
			for (int level : planner.suggestedLevels(chosen, account, nav.includeMiniquests(), CHOICES))
			{
				List<String> opened = openedAt(planner, chosen, level, account, nav.includeMiniquests());
				Goal goal = Goal.level(chosen, level);
				boolean pinned = nav.goals().isPinned(goal);
				chips.add(new LevelChip(level, opened, pinned, () ->
				{
					if (!nav.goals().isPinned(goal))
					{
						nav.goals().toggle(goal);
					}
					nav.levelPicker(false, null);
					nav.open(goal);
				}));
				if (firstOpens == null && !opened.isEmpty())
				{
					firstOpens = level + " opens " + opened.get(0) + (opened.size() > 1 ? " and " + (opened.size() - 1) + " more" : "");
				}
			}
			south.add(chips);
			south.add(Ui.wrapped(firstOpens != null ? "Teal levels open something: " + firstOpens + "."
				: "No quest or diary asks for more " + chosen.getName() + ": pick a round level.", Theme.small(),
				Theme.TEXT_FAINT, 170));
			card.add(south, BorderLayout.SOUTH);
		}
		return card;
	}

	/** Every skill with levels, in the order of the in-game skills tab's columns read across. */
	static List<Skill> skills()
	{
		List<Skill> out = new ArrayList<>();
		for (Skill skill : Skill.values())
		{
			if (skill != Skill.OVERALL)
			{
				out.add(skill);
			}
		}
		return out;
	}

	/** The quests and diary tiers that ask for exactly this level in the skill and are not done. */
	static List<String> openedAt(Planner planner, Skill skill, int level, AccountSnapshot account, boolean miniquests)
	{
		List<String> out = new ArrayList<>();
		for (QuestDef q : planner.opens(skill, level, account, miniquests))
		{
			if (Planner.need(q.skills, skill) == level)
			{
				out.add(q.name);
			}
		}
		for (DiaryDef d : planner.diariesOpened(skill, level, account))
		{
			if (Planner.need(d.skills, skill) == level)
			{
				out.add(d.name + " Diary");
			}
		}
		return out;
	}

	private static JComponent link(String text, Runnable action)
	{
		javax.swing.JLabel label = Ui.label(text, Theme.small(), Theme.TEXT_MUTED);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e) && label.contains(e.getPoint()))
				{
					action.run();
				}
			}
		});
		return label;
	}

	/** A hover-lit tile with a skill's icon; the chosen one wears a gold ring, a maxed one is dim and inert. */
	private static final class SkillTile extends JComponent
	{
		private final BufferedImage icon;
		private final boolean selected;
		private final boolean maxed;
		private boolean hover;

		SkillTile(BufferedImage icon, Skill skill, boolean selected, boolean maxed, Runnable onClick)
		{
			this.icon = icon;
			this.selected = selected;
			this.maxed = maxed;
			setOpaque(false);
			setPreferredSize(new Dimension(26, 26));
			setToolTipText(skill.getName() + (maxed ? ": already 99" : ""));
			if (!maxed)
			{
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}
			addMouseListener(new MouseAdapter()
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
					hover = false;
					repaint();
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					if (!maxed && SwingUtilities.isLeftMouseButton(e) && contains(e.getPoint()))
					{
						onClick.run();
					}
				}
			});
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			int h = getHeight();
			RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 7, 7);
			g.setColor(selected ? Theme.alpha(Theme.GOLD, 40) : hover && !maxed ? Theme.CARD_HOVER : Theme.INK_DEEP);
			g.fill(shape);
			g.setColor(selected ? Theme.GOLD : Theme.alpha(Theme.STROKE, hover && !maxed ? 255 : 160));
			g.draw(shape);
			if (icon != null)
			{
				if (maxed)
				{
					g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.3f));
				}
				g.drawImage(icon, (w - icon.getWidth()) / 2, (h - icon.getHeight()) / 2, null);
			}
			g.dispose();
		}
	}

	/**
	 * A level to aim for: the number, and under it "+2" in teal when two quests or diary tiers ask for exactly that
	 * level, or a tick when the level is already pinned.
	 */
	private static final class LevelChip extends JComponent
	{
		private final String level;
		private final String caption;
		private final boolean pinned;
		private final boolean opens;
		private boolean hover;

		LevelChip(int level, List<String> opened, boolean pinned, Runnable onClick)
		{
			this.level = String.valueOf(level);
			this.pinned = pinned;
			this.opens = !opened.isEmpty();
			this.caption = pinned ? "" : opens ? "+" + opened.size() : level == 99 ? "max" : "";
			setFont(Theme.small());
			setOpaque(false);
			setPreferredSize(new Dimension(32, 30));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			StringBuilder tip = new StringBuilder("<html>").append(pinned ? "Already pinned: open it" : "Pin level " + level);
			for (String name : opened)
			{
				tip.append("<br>Opens ").append(Ui.escape(name));
			}
			setToolTipText(tip.append("</html>").toString());
			addMouseListener(new MouseAdapter()
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
					hover = false;
					repaint();
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					if (SwingUtilities.isLeftMouseButton(e) && contains(e.getPoint()))
					{
						onClick.run();
					}
				}
			});
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			int h = getHeight();
			Color c = pinned ? Theme.GREEN : opens ? Theme.TEAL : Theme.GOLD;
			RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 7, 7);
			g.setColor(hover ? Theme.alpha(c, 40) : Theme.INK_DEEP);
			g.fill(shape);
			g.setColor(Theme.alpha(c, hover ? 230 : 140));
			g.draw(shape);
			g.setFont(Theme.title());
			FontMetrics bold = g.getFontMetrics();
			g.setColor(hover ? Theme.GOLD_BRIGHT : Theme.TEXT);
			boolean under = pinned || !caption.isEmpty();
			int top = under ? h / 2 + bold.getAscent() / 2 - 5 : (h + bold.getAscent()) / 2 - 2;
			g.drawString(level, (w - bold.stringWidth(level)) / 2, top);
			if (pinned)
			{
				g.setColor(c);
				ProgressRing.drawCheck(g, w / 2f, h - 7, 3f);
			}
			else if (!caption.isEmpty())
			{
				g.setFont(getFont());
				FontMetrics fm = g.getFontMetrics();
				g.setColor(c);
				g.drawString(caption, (w - fm.stringWidth(caption)) / 2, h - 4);
			}
			g.dispose();
		}
	}
}
