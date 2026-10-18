package com.codex.navigator;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.GoalStore;
import com.codex.navigator.core.Plan;
import com.codex.navigator.core.Planner;
import com.codex.navigator.core.PublishGate;
import com.codex.navigator.core.Recommender;
import com.codex.navigator.core.Step;
import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.QuestDef;
import com.codex.navigator.overlay.FocusOverlay;
import com.codex.navigator.ui.Icons;
import com.codex.navigator.ui.NavigatorPanel;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Codex Navigator",
	description = "An account compass: every quest, diary and combat achievement tier, with the path to whatever you want next",
	tags = {"quest", "diary", "goal", "planner", "progress", "achievement", "codex"}
)
public class CodexNavigatorPlugin extends Plugin
{
	private static final String GOALS_KEY = "goals";
	private static final Type GOAL_LIST = new TypeToken<List<String>>()
	{
	}.getType();
	/** Varbits can change many times a second; refresh at most this often while they do. */
	private static final int REFRESH_TICKS = 2;
	/**
	 * Reading every quest runs a client script per quest, so while varbits churn they are re-read at most
	 * this often. A change in quest points (a quest finished) triggers a read straight away.
	 */
	private static final int QUEST_TICKS = 25;
	/** Experience changes with every action while skilling; republish for it alone at most this often. */
	private static final int XP_ONLY_TICKS = 10;
	/** Varbits settle over the first ticks after login; completions seen then are not announced. */
	private static final int WARMUP_TICKS = 8;

	static final String[] DIARY_AREAS = {"Ardougne", "Desert", "Falador", "Fremennik", "Kandarin", "Karamja",
		"Kourend", "Lumbridge", "Morytania", "Varrock", "Western", "Wilderness"};
	static final String[] DIARY_TIERS = {"Easy", "Medium", "Hard", "Elite"};
	// Complete flags per area, in DIARY_TIERS order. Karamja's first three tiers have no
	// complete flag, so their reward-claimed flags stand in, as Quest Helper does.
	private static final int[][] DIARY_VARBITS = {
		{VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE, VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE, VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE},
		{VarbitID.DESERT_DIARY_EASY_COMPLETE, VarbitID.DESERT_DIARY_MEDIUM_COMPLETE, VarbitID.DESERT_DIARY_HARD_COMPLETE, VarbitID.DESERT_DIARY_ELITE_COMPLETE},
		{VarbitID.FALADOR_DIARY_EASY_COMPLETE, VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE, VarbitID.FALADOR_DIARY_HARD_COMPLETE, VarbitID.FALADOR_DIARY_ELITE_COMPLETE},
		{VarbitID.FREMENNIK_DIARY_EASY_COMPLETE, VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE, VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE},
		{VarbitID.KANDARIN_DIARY_EASY_COMPLETE, VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE, VarbitID.KANDARIN_DIARY_HARD_COMPLETE, VarbitID.KANDARIN_DIARY_ELITE_COMPLETE},
		{VarbitID.ATJUN_EASY_REWARD, VarbitID.ATJUN_MED_REWARD, VarbitID.ATJUN_HARD_REWARD, VarbitID.KARAMJA_DIARY_ELITE_COMPLETE},
		{VarbitID.KOUREND_DIARY_EASY_COMPLETE, VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE, VarbitID.KOUREND_DIARY_HARD_COMPLETE, VarbitID.KOUREND_DIARY_ELITE_COMPLETE},
		{VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE, VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE, VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE, VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE},
		{VarbitID.MORYTANIA_DIARY_EASY_COMPLETE, VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE, VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE},
		{VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE, VarbitID.VARROCK_DIARY_HARD_COMPLETE, VarbitID.VARROCK_DIARY_ELITE_COMPLETE},
		{VarbitID.WESTERN_DIARY_EASY_COMPLETE, VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE, VarbitID.WESTERN_DIARY_HARD_COMPLETE, VarbitID.WESTERN_DIARY_ELITE_COMPLETE},
		{VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE, VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE},
	};
	private static final int[] COMBAT_ACHIEVEMENT_VARBITS = {
		VarbitID.CA_TOTAL_TASKS_COMPLETED_EASY, VarbitID.CA_TOTAL_TASKS_COMPLETED_MEDIUM, VarbitID.CA_TOTAL_TASKS_COMPLETED_HARD,
		VarbitID.CA_TOTAL_TASKS_COMPLETED_ELITE, VarbitID.CA_TOTAL_TASKS_COMPLETED_MASTER, VarbitID.CA_TOTAL_TASKS_COMPLETED_GRANDMASTER,
	};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Gson gson;

	@Inject
	private CodexNavigatorConfig config;

	private final FocusOverlay overlay = new FocusOverlay();
	private final Map<String, Quest> questsByName = new HashMap<>();
	/** Last quest states read from the client; reused between the throttled quest reads. */
	private final Map<String, QuestState> questCache = new HashMap<>();
	/** Whether each pinned goal was complete at the last publish, to announce the moment one finishes. */
	private final Map<Goal, Boolean> completion = new HashMap<>();

	private GoalStore goals;
	private Planner planner;
	private Recommender recommender;
	private NavigatorPanel panel;
	private NavigationButton navButton;

	private volatile AccountSnapshot snapshot = AccountSnapshot.empty();
	/** Pins made while no RuneScape profile was active; merged into the profile at the next login. */
	private volatile List<String> offlinePins;
	private final PublishGate gate = new PublishGate(XP_ONLY_TICKS);
	private AccountSnapshot heldBack;
	private boolean dirty;
	private boolean questsPending;
	private int tick;
	private int lastRefreshTick;
	private int lastQuestTick = -QUEST_TICKS;
	private int loginTick;
	private int lastQuestPoints = -1;

	@Provides
	CodexNavigatorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CodexNavigatorConfig.class);
	}

	@Override
	protected void startUp()
	{
		CodexData data = CodexData.load(gson);
		planner = new Planner(data);
		recommender = new Recommender(planner);
		for (Quest quest : Quest.values())
		{
			questsByName.put(quest.getName(), quest);
		}

		// A fresh store each start, so listeners from a previous run of the plugin never pile up.
		goals = new GoalStore();
		goals.setSaver(this::saveGoals);
		goals.addListener(this::updateOverlay);
		loadGoals();

		panel = new NavigatorPanel(planner, recommender, goals, skillIconManager, config::includeMiniquests);
		navButton = NavigationButton.builder()
			.tooltip("Codex Navigator")
			.icon(Icons.compass(16))
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);

		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				markLogin();
				refresh();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		overlay.setView(null);
		questsByName.clear();
		questCache.clear();
		completion.clear();
		snapshot = AccountSnapshot.empty();
		gate.reset();
		heldBack = null;
		offlinePins = null;
		panel = null;
		goals = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			if (!snapshot.isLoggedIn())
			{
				markLogin();
			}
			dirty = true;
			questsPending = true;
		}
		else if (state == GameState.LOGIN_SCREEN)
		{
			questCache.clear();
			lastQuestPoints = -1;
			completion.clear();
			heldBack = null;
			publish(AccountSnapshot.empty(), true);
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		completion.clear();
		loadGoals();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		dirty = true;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		dirty = true;
		questsPending = true;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CodexNavigatorConfig.GROUP.equals(event.getGroup()) || GOALS_KEY.equals(event.getKey()))
		{
			return;
		}
		updateOverlay();
		NavigatorPanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(target::refreshViews);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick++;
		if (dirty && tick - lastRefreshTick >= REFRESH_TICKS)
		{
			refresh();
		}
		else if (heldBack != null && gate.due(tick))
		{
			AccountSnapshot next = heldBack;
			heldBack = null;
			publish(next, true);
		}
	}

	private void markLogin()
	{
		loginTick = tick;
		questsPending = true;
		dirty = true;
	}

	private void loadGoals()
	{
		String json = configManager.getRSProfileConfiguration(CodexNavigatorConfig.GROUP, GOALS_KEY);
		List<String> keys = null;
		if (json != null && !json.isEmpty())
		{
			try
			{
				keys = gson.fromJson(json, GOAL_LIST);
			}
			catch (RuntimeException e)
			{
				keys = null;
			}
		}
		List<String> extra = configManager.getRSProfileKey() != null ? offlinePins : null;
		if (extra != null)
		{
			offlinePins = null;
		}
		GoalStore store = goals;
		if (store != null)
		{
			store.load(keys, extra);
		}
	}

	/**
	 * Saves pinned goals to the active RuneScape profile. With no profile (logged out) there is nowhere
	 * to save them yet, so they are held and merged into the profile at the next login.
	 */
	private void saveGoals(List<String> keys)
	{
		if (configManager.getRSProfileKey() == null)
		{
			offlinePins = new ArrayList<>(keys);
			return;
		}
		configManager.setRSProfileConfiguration(CodexNavigatorConfig.GROUP, GOALS_KEY, gson.toJson(keys));
	}

	/** Reads the account on the client thread and hands it to the panel if anything changed. */
	private void refresh()
	{
		dirty = false;
		lastRefreshTick = tick;
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		AccountSnapshot.Builder b = new AccountSnapshot.Builder().loggedIn(true)
			.members(client.getWorldType().contains(WorldType.MEMBERS));
		Player local = client.getLocalPlayer();
		if (local != null && local.getName() != null)
		{
			b.playerName(local.getName()).combatLevel(local.getCombatLevel());
		}
		else
		{
			// The local player arrives a tick or two after login; look again soon.
			dirty = true;
		}
		for (Skill skill : Skill.values())
		{
			b.skill(skill, client.getRealSkillLevel(skill), client.getSkillExperience(skill));
		}
		int questPoints = client.getVarpValue(VarPlayerID.QP);
		if (questPoints != lastQuestPoints)
		{
			boolean first = lastQuestPoints < 0;
			lastQuestPoints = questPoints;
			questsPending = true;
			lastQuestTick = -QUEST_TICKS;
			if (!first)
			{
				readQuests();
				// The quest's own state can land a tick after its points; look once more shortly.
				questsPending = true;
				dirty = true;
				lastQuestTick = tick - QUEST_TICKS + 3;
			}
		}
		if (questsPending && (questCache.isEmpty() || tick - lastQuestTick >= QUEST_TICKS))
		{
			readQuests();
		}
		else if (questsPending)
		{
			dirty = true;
		}
		questCache.forEach(b::quest);
		b.questPoints(questPoints);
		for (int area = 0; area < DIARY_AREAS.length; area++)
		{
			for (int tier = 0; tier < DIARY_TIERS.length; tier++)
			{
				if (client.getVarbitValue(DIARY_VARBITS[area][tier]) > 0)
				{
					b.diary(DIARY_AREAS[area] + " " + DIARY_TIERS[tier]);
				}
			}
		}
		for (int i = 0; i < COMBAT_ACHIEVEMENT_VARBITS.length; i++)
		{
			b.combatAchievements(CodexData.COMBAT_ACHIEVEMENT_TIERS[i], client.getVarbitValue(COMBAT_ACHIEVEMENT_VARBITS[i]));
		}
		b.combatAchievementPoints(client.getVarbitValue(VarbitID.CA_POINTS));
		publish(b.build(), false);
	}

	private void readQuests()
	{
		questsPending = false;
		lastQuestTick = tick;
		for (QuestDef def : planner.getData().quests())
		{
			Quest quest = questsByName.get(def.name);
			if (quest != null)
			{
				QuestState state = quest.getState(client);
				if (state != null)
				{
					questCache.put(def.name, state);
				}
			}
		}
	}

	/**
	 * Hands a new snapshot to the overlay and panel. A change that only moves experience is held back
	 * and published at most every few seconds, so skilling does not rebuild the panel every tick.
	 */
	private void publish(AccountSnapshot next, boolean force)
	{
		PublishGate.Decision decision = gate.offer(next, tick, force);
		if (decision == PublishGate.Decision.HOLD)
		{
			heldBack = next;
			return;
		}
		heldBack = null;
		if (decision == PublishGate.Decision.SKIP)
		{
			return;
		}
		snapshot = next;
		announceCompletions(next);
		updateOverlay();
		NavigatorPanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.update(next));
		}
	}

	/** Posts a chat line the moment a pinned goal is finished, once varbits have settled after login. */
	private void announceCompletions(AccountSnapshot account)
	{
		GoalStore store = goals;
		if (store == null || !account.isLoggedIn() || account.getPlayerName() == null)
		{
			return;
		}
		boolean settled = tick - loginTick >= WARMUP_TICKS;
		for (Goal goal : store.goals())
		{
			Plan plan = planner.plan(goal, account);
			Boolean before = completion.put(goal, plan.isComplete());
			if (settled && plan.isComplete() && Boolean.FALSE.equals(before) && config.announceCompletions())
			{
				String message = new ChatMessageBuilder()
					.append(ChatColorType.HIGHLIGHT)
					.append("Codex: ")
					.append(ChatColorType.NORMAL)
					.append(plan.getTitle() + " complete. Open the Codex to pick your next goal.")
					.build();
				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.CONSOLE)
					.runeLiteFormattedMessage(message)
					.build());
			}
		}
	}

	private void updateOverlay()
	{
		AccountSnapshot account = snapshot;
		GoalStore store = goals;
		if (!config.showOverlay() || !account.isLoggedIn() || planner == null || store == null)
		{
			overlay.setView(null);
			return;
		}
		Goal focus = store.focus();
		if (focus != null)
		{
			Plan plan = planner.plan(focus, account);
			Step next = plan.getNext();
			if (plan.isComplete())
			{
				overlay.setView(new FocusOverlay.View("CODEX  ·  COMPLETE", plan.getTitle(), 1,
					"Pick your next goal in the Codex"));
			}
			else
			{
				overlay.setView(new FocusOverlay.View("CODEX  ·  FOCUS", plan.getTitle(), plan.getProgress(),
					next == null ? null : stepText(next)));
			}
			return;
		}
		if (config.suggestWithoutFocus())
		{
			List<Recommender.Pick> picks = recommender.readyNow(account, config.includeMiniquests(), 1);
			if (!picks.isEmpty())
			{
				Recommender.Pick pick = picks.get(0);
				overlay.setView(new FocusOverlay.View("CODEX  ·  SUGGESTED", pick.getQuest().name, -1,
					pick.getReason() != null ? pick.getReason() : "Ready to start"));
				return;
			}
		}
		overlay.setView(null);
	}

	static String stepText(Step step)
	{
		if (step.getKind() == Step.Kind.QUEST)
		{
			return (step.getStatus() == Step.Status.IN_PROGRESS ? "Continue " : "Start ") + step.getTitle();
		}
		return step.getTitle();
	}
}
