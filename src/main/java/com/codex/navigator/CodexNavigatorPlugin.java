package com.codex.navigator;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Chains;
import com.codex.navigator.core.CompletionWatch;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.GoalBoard;
import com.codex.navigator.core.GoalStore;
import com.codex.navigator.core.Pace;
import com.codex.navigator.core.Plan;
import com.codex.navigator.core.Planner;
import com.codex.navigator.core.PlayTime;
import com.codex.navigator.core.PublishGate;
import com.codex.navigator.core.Recommender;
import com.codex.navigator.core.Step;
import com.codex.navigator.data.CodexData;
import com.codex.navigator.data.QuestDef;
import com.codex.navigator.overlay.FocusOverlay;
import com.codex.navigator.ui.Icons;
import com.codex.navigator.ui.NavigatorPanel;
import com.codex.navigator.ui.ShareActions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import javax.imageio.ImageIO;
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
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;

@PluginDescriptor(
	name = "Codex Navigator",
	description = "An account compass: every quest, diary and combat achievement tier, with the path to whatever you want next",
	tags = {"quest", "diary", "goal", "planner", "progress", "achievement", "codex"}
)
public class CodexNavigatorPlugin extends Plugin
{
	private static final String GOALS_KEY = "goals";
	/** What {@link Pace} has learned about this account's training speed, per RuneScape profile. */
	static final String PACE_KEY = "pace";
	private static final Type PACE_MAP = new TypeToken<Map<String, double[]>>()
	{
	}.getType();
	/** Learned pace is saved at most this often while it changes, and always on logout and shutdown. */
	private static final int PACE_SAVE_TICKS = 500;
	/** Worlds whose stats are not the account's own, or that do not save: nothing is learned there. */
	private static final Set<WorldType> NO_LEARNING = EnumSet.of(WorldType.LAST_MAN_STANDING, WorldType.PVP_ARENA,
		WorldType.BETA_WORLD, WorldType.NOSAVE_MODE, WorldType.TOURNAMENT_WORLD, WorldType.FRESH_START_WORLD);
	/** The pinned goals with progress, next step and what they still need, for other plugins to read. */
	static final String BOARD_KEY = "goalBoard";
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
	/** Codex Guardian's per-account one-life choice ("true" / "false"), read only. */
	static final String GUARDIAN_GROUP = "codexguardian";
	static final String GUARDIAN_ONE_LIFE_KEY = "oneLifeAccount";
	/** RuneLite's name for the world flag Hardcore Worlds carry; matched by name so an older client reads false. */
	private static final String HARDCORE_WORLD_TYPE = "HARDCORE_MAIN";

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

	@Inject
	private ScheduledExecutorService executor;

	private final FocusOverlay overlay = new FocusOverlay();
	private final Map<String, Quest> questsByName = new HashMap<>();
	/** Last quest states read from the client; reused between the throttled quest reads. */
	private final Map<String, QuestState> questCache = new HashMap<>();
	/** Compares each publish with the one before, to announce the moment a pinned goal finishes. */
	private final CompletionWatch completion = new CompletionWatch();
	/** How fast this account trains each skill, learned while the plugin runs. Touched on the client thread only. */
	private final Pace pace = new Pace();
	/** The RuneScape profile {@link #pace} was loaded from and is saved back to. */
	private String paceProfile;
	private int lastPaceSave;

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
	/** The board last written, so an unchanged board is not saved again. */
	private String lastBoard;
	/**
	 * Set when the profile changes and cleared by the next publish: until then the snapshot still belongs to
	 * the previous profile, and a board written from it would land under the new one.
	 */
	private boolean boardHeld;
	/**
	 * Set when a board was due in the first ticks after login, while quest and diary varbits are still arriving: a
	 * board written then would tell other plugins your goals had gone back to zero. It is written once they settle.
	 */
	private boolean boardDeferred;
	/** Set by a world hop, so the login after it waits for varbits like any other. */
	private boolean hopped;

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
		goals.addListener(() -> clientThread.invokeLater(this::writeBoard));
		loadGoals();
		loadPace();

		panel = new NavigatorPanel(planner, recommender, goals, skillIconManager, config::includeMiniquests);
		panel.setShare(new ShareActions()
		{
			@Override
			public void save(BufferedImage card, String fileName)
			{
				saveCard(card, fileName);
			}

			@Override
			public void copy(BufferedImage card)
			{
				copyCard(card);
			}
		}, config::nameOnCard);
		navButton = NavigationButton.builder()
			.tooltip("Codex Navigator")
			.icon(Icons.compass(16))
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		readPlayTime();

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
		savePace();
		pace.load(null);
		paceProfile = null;
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		overlay.setView(null);
		questsByName.clear();
		questCache.clear();
		completion.reset();
		snapshot = AccountSnapshot.empty();
		gate.reset();
		heldBack = null;
		offlinePins = null;
		lastBoard = null;
		boardHeld = false;
		boardDeferred = false;
		panel = null;
		goals = null;
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		// Closing the client while logged in reaches neither the login screen nor shutDown, and ConfigManager
		// writes its changes to disk in this same event, so the save is made here and sent on by hand.
		CompletableFuture<Void> done = new CompletableFuture<>();
		clientThread.invokeLater(() ->
		{
			try
			{
				savePace();
				configManager.sendConfig();
			}
			finally
			{
				done.complete(null);
			}
		});
		event.waitFor(done);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			// A world hop is a fresh login too: the varbits arrive again over the first ticks.
			if (!snapshot.isLoggedIn() || hopped)
			{
				hopped = false;
				markLogin();
			}
			dirty = true;
			questsPending = true;
		}
		else if (state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			hopped |= state == GameState.HOPPING;
			// The time away is not training; the next drop in each skill starts a fresh bout.
			pace.pause();
		}
		else if (state == GameState.LOGIN_SCREEN)
		{
			pace.pause();
			savePace();
			questCache.clear();
			lastQuestPoints = -1;
			completion.reset();
			heldBack = null;
			publish(AccountSnapshot.empty(), true);
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		// What was learned belongs to the profile it was learned on, which is the one being left.
		savePace();
		loadPace();
		completion.reset();
		lastBoard = null;
		boardHeld = true;
		// Publish afresh for the new profile even if the read happens to match, so its board is written.
		gate.reset();
		dirty = true;
		loadGoals();
		readPlayTime();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		dirty = true;
		if (config.learnPace() && client.getGameState() == GameState.LOGGED_IN && paceProfile != null
			&& Collections.disjoint(client.getWorldType(), NO_LEARNING))
		{
			pace.observe(event.getSkill(), event.getXp(), System.currentTimeMillis());
		}
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
		if (GUARDIAN_GROUP.equals(event.getGroup()) && GUARDIAN_ONE_LIFE_KEY.equals(event.getKey()))
		{
			// Marked one-life (or not) in Guardian: suggestions change order.
			clientThread.invokeLater(() -> dirty = true);
			return;
		}
		if (PlayTime.GROUP.equals(event.getGroup()) && PlayTime.JOURNAL.equals(event.getKey())
			&& (event.getProfile() == null || event.getProfile().equals(configManager.getRSProfileKey())))
		{
			// Session Chronicle filed a session: the daily habit may have moved.
			readPlayTime();
			return;
		}
		if (!CodexNavigatorConfig.GROUP.equals(event.getGroup()) || GOALS_KEY.equals(event.getKey())
			|| BOARD_KEY.equals(event.getKey()) || PACE_KEY.equals(event.getKey()))
		{
			return;
		}
		if ("oneLifeCare".equals(event.getKey()))
		{
			clientThread.invokeLater(() -> dirty = true);
		}
		if ("learnPace".equals(event.getKey()))
		{
			// Estimates switch between the learned pace and the steady rates: read the account again.
			clientThread.invokeLater(() ->
			{
				pace.pause();
				gate.reset();
				dirty = true;
			});
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
		if (tick - lastPaceSave >= PACE_SAVE_TICKS)
		{
			savePace();
		}
		if (boardDeferred && settled())
		{
			boardDeferred = false;
			// The quests cached during the login churn may predate their varbits, and are otherwise not read again
			// for QUEST_TICKS: read them now, so the first board written is a settled one.
			questsPending = true;
			lastQuestTick = tick - QUEST_TICKS;
			refresh();
			writeBoard();
		}
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

	/** True once the varbits sent at login have had time to arrive. */
	private boolean settled()
	{
		return tick - loginTick >= WARMUP_TICKS;
	}

	private void markLogin()
	{
		loginTick = tick;
		questsPending = true;
		dirty = true;
	}

	/**
	 * Reads how much this account plays a day from Codex Session Chronicle's journal, read only; nothing when
	 * Chronicle is not installed or has too little to go on, and the panel then shows no dates.
	 */
	private void readPlayTime()
	{
		PlayTime time = PlayTime.read(gson, configManager.getRSProfileConfiguration(PlayTime.GROUP, PlayTime.JOURNAL),
			System.currentTimeMillis());
		NavigatorPanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.setPlayTime(time));
		}
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

	/** Loads what has been learned about the active profile's training speed; nothing when logged out. */
	private void loadPace()
	{
		paceProfile = configManager.getRSProfileKey();
		Map<String, double[]> saved = null;
		if (paceProfile != null)
		{
			String json = configManager.getConfiguration(CodexNavigatorConfig.GROUP, paceProfile, PACE_KEY);
			if (json != null && !json.isEmpty())
			{
				try
				{
					saved = gson.fromJson(json, PACE_MAP);
				}
				catch (RuntimeException e)
				{
					saved = null;
				}
			}
		}
		pace.load(saved);
	}

	/** Saves the learned pace to the profile it belongs to, if anything new was learned since the last save. */
	private void savePace()
	{
		lastPaceSave = tick;
		String profile = paceProfile;
		if (profile != null && pace.takeChanged())
		{
			configManager.setConfiguration(CodexNavigatorConfig.GROUP, profile, PACE_KEY, gson.toJson(pace.toSaved()));
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
			.members(client.getWorldType().contains(WorldType.MEMBERS))
			.ironman(client.getVarbitValue(VarbitID.IRONMAN) != 0)
			.oneLife(config.oneLifeCare() && oneLife());
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
		if (config.learnPace())
		{
			b.pace(pace.factors()).paceHours(pace.hoursLearned());
		}
		publish(b.build(), false);
	}

	/**
	 * Whether this account has one life: a hardcore ironman or hardcore group ironman, any account on a Hardcore
	 * World, or one marked one-life in Codex Guardian (read only; without Guardian that part is simply false).
	 */
	private boolean oneLife()
	{
		int type = client.getVarbitValue(VarbitID.IRONMAN);
		if (type == 3 || type == 5)
		{
			return true;
		}
		for (WorldType world : client.getWorldType())
		{
			if (HARDCORE_WORLD_TYPE.equals(world.name()))
			{
				return true;
			}
		}
		return "true".equals(configManager.getRSProfileConfiguration(GUARDIAN_GROUP, GUARDIAN_ONE_LIFE_KEY));
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
		boardHeld = false;
		announceCompletions(next);
		writeBoard();
		updateOverlay();
		NavigatorPanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.update(next));
		}
	}

	/**
	 * Saves the goal board for other plugins when it has changed. Only a logged-in read is written:
	 * progress worked out from a logged-out snapshot would tell readers every goal had gone back to zero.
	 */
	private void writeBoard()
	{
		AccountSnapshot account = snapshot;
		GoalStore store = goals;
		if (store == null || planner == null || boardHeld || !account.isLoggedIn() || configManager.getRSProfileKey() == null)
		{
			return;
		}
		if (!settled())
		{
			boardDeferred = true;
			return;
		}
		String json = gson.toJson(GoalBoard.of(planner, store.goals(), account));
		if (json.equals(lastBoard))
		{
			return;
		}
		lastBoard = json;
		configManager.setRSProfileConfiguration(CodexNavigatorConfig.GROUP, BOARD_KEY, json);
	}

	/** Posts a chat line the moment a pinned goal is finished, once varbits have settled after login. */
	private void announceCompletions(AccountSnapshot account)
	{
		GoalStore store = goals;
		if (store == null || !account.isLoggedIn() || account.getPlayerName() == null)
		{
			return;
		}
		List<Plan> finished = completion.finished(planner, store.goals(), account);
		if (tick - loginTick < WARMUP_TICKS || !config.announceCompletions())
		{
			return;
		}
		for (Plan plan : finished)
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
				Goal after = Chains.next(planner, focus, account, config.includeMiniquests());
				overlay.setView(new FocusOverlay.View("CODEX  ·  COMPLETE", plan.getTitle(), 1,
					after != null && !store.isPinned(after) ? "Next in line: " + planner.plan(after, account).getTitle()
						: "Pick your next goal in the Codex"));
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
			List<Recommender.Pick> picks = recommender.readyNow(account, store.goals(), config.includeMiniquests(), 1);
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

	/** Asks where to save the road card (on the Swing thread, where the buttons run), then writes it off that thread. */
	private void saveCard(BufferedImage card, String fileName)
	{
		NavigatorPanel view = panel;
		if (view == null)
		{
			return;
		}
		List<Filepath> chosen = new Filepath.Chooser()
			.setIsSave()
			.setAcceptsFiles()
			.setDialogTitle("Save road card")
			.addExtensionFilter("PNG image", "png")
			.setDefaultExtension("png")
			.setFileName(fileName)
			.showDialog(view);
		if (chosen == null || chosen.isEmpty())
		{
			return;
		}
		Filepath target = chosen.get(0);
		executor.execute(() ->
		{
			String note;
			try (OutputStream out = target.openOutputStream())
			{
				ImageIO.write(card, "png", out);
				note = "Saved as " + target.getFileName() + ".";
			}
			catch (IOException e)
			{
				note = "The card could not be saved: " + e.getMessage();
			}
			String shown = note;
			SwingUtilities.invokeLater(() ->
			{
				NavigatorPanel p = panel;
				if (p != null)
				{
					p.shareNote(shown);
				}
			});
		});
	}

	/** Puts the road card on the clipboard; the buttons call this on the Swing thread. */
	private void copyCard(BufferedImage card)
	{
		String note;
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransfer(ImageTransfer.opaque(card)), null);
			note = "Copied. Paste it anywhere you share pictures.";
		}
		catch (IllegalStateException e)
		{
			note = "The clipboard is busy, try again in a moment.";
		}
		NavigatorPanel p = panel;
		if (p != null)
		{
			p.shareNote(note);
		}
	}
}
