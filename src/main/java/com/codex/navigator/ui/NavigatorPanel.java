package com.codex.navigator.ui;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.GoalStore;
import com.codex.navigator.core.Planner;
import com.codex.navigator.core.Recommender;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The Codex side panel: the account crest, three tabs (Compass, Goals, Atlas)
 * and a detail page that opens over them for any goal.
 */
public class NavigatorPanel extends PluginPanel implements Navigation
{
	private static final String[] TABS = {"compass", "goals", "atlas"};
	private static final String DETAIL = "detail";

	private final Planner planner;
	private final Recommender recommender;
	private final GoalStore goals;
	private final SkillIconManager skillIcons;
	private final BooleanSupplier includeMiniquests;
	private final Map<Skill, BufferedImage> iconCache = new EnumMap<>(Skill.class);

	private final CrestHeader crest = new CrestHeader();
	private final Segmented tabs = new Segmented(true, "Compass", "Goals", "Atlas");
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JScrollPane compass = scroller(new StackPanel(6, 8));
	private final JScrollPane goalsView = scroller(new StackPanel(6, 8));
	private final AtlasView atlas;
	private final JScrollPane detail = scroller(new StackPanel(6, 8));

	private final Deque<Goal> history = new ArrayDeque<>();
	private AccountSnapshot account = AccountSnapshot.empty();
	private Goal detailGoal;
	private boolean showCompleted;

	public NavigatorPanel(Planner planner, Recommender recommender, GoalStore goals, SkillIconManager skillIcons,
		BooleanSupplier includeMiniquests)
	{
		super(false);
		this.planner = planner;
		this.recommender = recommender;
		this.goals = goals;
		this.skillIcons = skillIcons;
		this.includeMiniquests = includeMiniquests;
		this.atlas = new AtlasView(this);

		setLayout(new BorderLayout());
		setBackground(Theme.INK);

		JPanel north = new JPanel(new BorderLayout());
		north.setOpaque(false);
		north.add(crest, BorderLayout.NORTH);
		JPanel tabHolder = new JPanel(new BorderLayout());
		tabHolder.setOpaque(false);
		tabHolder.setBorder(new EmptyBorder(8, 8, 2, 8));
		tabHolder.add(tabs, BorderLayout.CENTER);
		north.add(tabHolder, BorderLayout.SOUTH);
		add(north, BorderLayout.NORTH);

		content.setOpaque(false);
		content.add(compass, TABS[0]);
		content.add(goalsView, TABS[1]);
		content.add(atlas, TABS[2]);
		content.add(detail, DETAIL);
		add(content, BorderLayout.CENTER);

		tabs.onChange(this::showTab);
		goals.addListener(() -> SwingUtilities.invokeLater(this::rebuildAll));
		rebuildAll();
	}

	static JScrollPane scroller(JComponent view)
	{
		JScrollPane scroll = new JScrollPane(view);
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getVerticalScrollBar().setPreferredSize(new java.awt.Dimension(8, 0));
		return scroll;
	}

	/** Called on the Swing thread whenever the account changes. */
	public void update(AccountSnapshot snapshot)
	{
		account = snapshot;
		crest.update(snapshot, planner.getData());
		rebuildAll();
	}

	private void rebuildAll()
	{
		replace(compass, CompassView.build(this));
		replace(goalsView, GoalsView.build(this));
		atlas.rebuild();
		if (detailGoal != null)
		{
			replace(detail, DetailView.build(this, detailGoal, showCompleted, this::back, this::toggleCompleted));
		}
		revalidate();
		repaint();
	}

	private static void replace(JScrollPane scroll, JComponent view)
	{
		int position = scroll.getVerticalScrollBar().getValue();
		scroll.setViewportView(view);
		SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(position));
	}

	@Override
	public void showTab(int tab)
	{
		detailGoal = null;
		history.clear();
		tabs.setSelected(tab, false);
		cards.show(content, TABS[tab]);
	}

	@Override
	public void open(Goal goal)
	{
		if (detailGoal != null)
		{
			history.push(detailGoal);
		}
		detailGoal = goal;
		showCompleted = false;
		detail.setViewportView(DetailView.build(this, goal, false, this::back, this::toggleCompleted));
		detail.getVerticalScrollBar().setValue(0);
		cards.show(content, DETAIL);
	}

	private void back()
	{
		if (history.isEmpty())
		{
			showTab(tabs.getSelected());
			return;
		}
		detailGoal = history.pop();
		showCompleted = false;
		detail.setViewportView(DetailView.build(this, detailGoal, false, this::back, this::toggleCompleted));
		detail.getVerticalScrollBar().setValue(0);
	}

	private void toggleCompleted()
	{
		showCompleted = !showCompleted;
		replace(detail, DetailView.build(this, detailGoal, showCompleted, this::back, this::toggleCompleted));
	}

	@Override
	public AccountSnapshot account()
	{
		return account;
	}

	@Override
	public Planner planner()
	{
		return planner;
	}

	@Override
	public Recommender recommender()
	{
		return recommender;
	}

	@Override
	public GoalStore goals()
	{
		return goals;
	}

	@Override
	public boolean includeMiniquests()
	{
		return includeMiniquests.getAsBoolean();
	}

	@Override
	public BufferedImage skillIcon(Skill skill)
	{
		return iconCache.computeIfAbsent(skill, s -> skillIcons.getSkillImage(s, true));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		g.setColor(Theme.INK);
		g.fillRect(0, 0, getWidth(), getHeight());
	}
}
