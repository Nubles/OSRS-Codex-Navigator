package com.codex.navigator.ui;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.GoalStore;
import com.codex.navigator.core.Planner;
import com.codex.navigator.core.PlayTime;
import com.codex.navigator.core.Recommender;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
	private final Segmented tabs = new Segmented(true, "Compass", "Goals", "Atlas").notifyOnReselect();
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JScrollPane compass = scroller(new StackPanel(6, 8));
	private final JScrollPane goalsView = scroller(new StackPanel(6, 8));
	private final AtlasView atlas;
	private final JScrollPane detail = scroller(new StackPanel(6, 8));

	private final Deque<Goal> history = new ArrayDeque<>();
	/** Views whose content is out of date; each is rebuilt only when it is next on screen. */
	private final Set<String> stale = new HashSet<>();
	private AccountSnapshot account = AccountSnapshot.empty();
	private Goal detailGoal;
	private boolean showCompleted;
	private boolean showMetLevels;
	private String visible = TABS[0];
	private boolean active;
	/** Two hours: an ordinary evening. */
	private int sessionChoice = 1;
	private boolean levelPickerOpen;
	private PlayTime playTime;
	private Skill levelSkill;
	private ShareActions share;
	private BooleanSupplier nameOnCards = () -> true;
	private String shareNote;

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
		goals.addListener(() -> SwingUtilities.invokeLater(this::refreshViews));
		crest.update(account, planner.getData(), planner.capeQuests());
		refreshViews();
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
		crest.update(snapshot, planner.getData(), planner.capeQuests());
		refreshViews();
	}

	/** Marks every view out of date and rebuilds the one on screen, if the panel is open. */
	public void refreshViews()
	{
		stale.add(TABS[0]);
		stale.add(TABS[1]);
		stale.add(TABS[2]);
		stale.add(DETAIL);
		rebuildVisible();
	}

	@Override
	public void onActivate()
	{
		active = true;
		rebuildVisible();
	}

	@Override
	public void onDeactivate()
	{
		active = false;
	}

	/** Rebuilds only what is on screen, so a closed panel or a hidden tab costs nothing per update. */
	private void rebuildVisible()
	{
		if (!active || !stale.remove(visible))
		{
			return;
		}
		switch (visible)
		{
			case "compass":
				replace(compass, CompassView.build(this));
				break;
			case "goals":
				replace(goalsView, GoalsView.build(this));
				break;
			case "atlas":
				atlas.rebuild();
				break;
			default:
				if (detailGoal != null)
				{
					replace(detail, buildDetail());
				}
		}
		revalidate();
		repaint();
	}

	private JComponent buildDetail()
	{
		return DetailView.build(this, detailGoal, showCompleted, showMetLevels, this::back, this::toggleCompleted,
			this::toggleMetLevels);
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
		show(TABS[tab]);
	}

	/** Shows the Atlas on one of its lists: 0 quests, 1 diaries, 2 combat achievements. */
	@Override
	public void showAtlas(int list)
	{
		atlas.showList(list);
		showTab(2);
	}

	private void show(String card)
	{
		visible = card;
		cards.show(content, card);
		rebuildVisible();
	}

	@Override
	public void open(Goal goal)
	{
		if (detailGoal != null)
		{
			history.push(detailGoal);
		}
		showDetail(goal);
	}

	private void showDetail(Goal goal)
	{
		detailGoal = goal;
		showCompleted = false;
		showMetLevels = false;
		visible = DETAIL;
		stale.remove(DETAIL);
		detail.setViewportView(buildDetail());
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
		showDetail(history.pop());
	}

	private void toggleCompleted()
	{
		showCompleted = !showCompleted;
		replace(detail, buildDetail());
	}

	private void toggleMetLevels()
	{
		showMetLevels = !showMetLevels;
		replace(detail, buildDetail());
	}

	@Override
	public int sessionChoice()
	{
		return sessionChoice;
	}

	@Override
	public void chooseSession(int choice)
	{
		if (choice == sessionChoice)
		{
			return;
		}
		sessionChoice = choice;
		redrawGoals();
	}

	@Override
	public PlayTime playTime()
	{
		return playTime;
	}

	/** Called on the Swing thread when Codex Session Chronicle's journal has been read; null when it says nothing. */
	public void setPlayTime(PlayTime time)
	{
		if (time == playTime)
		{
			return;
		}
		playTime = time;
		refreshViews();
	}

	@Override
	public boolean levelPickerOpen()
	{
		return levelPickerOpen;
	}

	@Override
	public Skill levelSkill()
	{
		return levelSkill;
	}

	@Override
	public void levelPicker(boolean open, Skill skill)
	{
		levelPickerOpen = open;
		levelSkill = open ? skill : null;
		redrawGoals();
	}

	private void redrawGoals()
	{
		if (TABS[1].equals(visible))
		{
			replace(goalsView, GoalsView.build(this));
			revalidate();
			repaint();
		}
		else
		{
			stale.add(TABS[1]);
		}
	}

	/** Lets the Compass save and copy the road card; names go on it while the option is on. */
	public void setShare(ShareActions share, BooleanSupplier nameOnCards)
	{
		this.share = share;
		this.nameOnCards = nameOnCards;
	}

	/** Shows what became of a save or copy under the road's buttons. Swing thread only. */
	public void shareNote(String note)
	{
		shareNote = note;
		stale.add(TABS[0]);
		rebuildVisible();
	}

	@Override
	public ShareActions share()
	{
		return share;
	}

	@Override
	public String cardName()
	{
		return nameOnCards.getAsBoolean() && account.isLoggedIn() ? account.getPlayerName() : null;
	}

	@Override
	public String shareNote()
	{
		return shareNote;
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
