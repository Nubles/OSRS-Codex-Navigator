package com.codex.navigator;

import com.codex.navigator.core.AccountSnapshot;
import com.codex.navigator.core.Goal;
import com.codex.navigator.core.GoalStore;
import com.codex.navigator.core.Plan;
import com.codex.navigator.core.Planner;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
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

	private static final String[] DIARY_AREAS = {"Ardougne", "Desert", "Falador", "Fremennik", "Kandarin", "Karamja",
		"Kourend", "Lumbridge", "Morytania", "Varrock", "Western", "Wilderness"};
	private static final String[] DIARY_TIERS = {"Easy", "Medium", "Hard", "Elite"};
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
	private Gson gson;

	@Inject
	private CodexNavigatorConfig config;

	private final GoalStore goals = new GoalStore();
	private final FocusOverlay overlay = new FocusOverlay();
	private final Map<String, Quest> questsByName = new HashMap<>();

	private Planner planner;
	private Recommender recommender;
	private NavigatorPanel panel;
	private NavigationButton navButton;

	private volatile AccountSnapshot snapshot = AccountSnapshot.empty();
	private int lastSignature;
	private boolean dirty;
	private int ticksSinceRefresh;

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

		goals.setSaver(keys -> configManager.setRSProfileConfiguration(CodexNavigatorConfig.GROUP, GOALS_KEY, gson.toJson(keys)));
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

		dirty = true;
		clientThread.invokeLater(this::refresh);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		questsByName.clear();
		snapshot = AccountSnapshot.empty();
		lastSignature = 0;
		panel = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			dirty = true;
		}
		else if (state == GameState.LOGIN_SCREEN)
		{
			publish(AccountSnapshot.empty());
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
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
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CodexNavigatorConfig.GROUP.equals(event.getGroup()) && !GOALS_KEY.equals(event.getKey()))
		{
			lastSignature = 0;
			dirty = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		ticksSinceRefresh++;
		if (dirty && ticksSinceRefresh >= REFRESH_TICKS)
		{
			refresh();
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
		goals.load(keys);
	}

	/** Reads the account on the client thread and hands it to the panel if anything changed. */
	private void refresh()
	{
		dirty = false;
		ticksSinceRefresh = 0;
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		AccountSnapshot.Builder b = new AccountSnapshot.Builder().loggedIn(true);
		Player local = client.getLocalPlayer();
		if (local != null)
		{
			b.playerName(local.getName()).combatLevel(local.getCombatLevel());
		}
		for (Skill skill : Skill.values())
		{
			b.skill(skill, client.getRealSkillLevel(skill), client.getSkillExperience(skill));
		}
		for (QuestDef def : planner.getData().quests())
		{
			Quest quest = questsByName.get(def.name);
			if (quest != null)
			{
				QuestState state = quest.getState(client);
				if (state != null)
				{
					b.quest(def.name, state);
				}
			}
		}
		b.questPoints(client.getVarpValue(VarPlayerID.QP));
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
		publish(b.build());
	}

	private void publish(AccountSnapshot next)
	{
		int signature = next.signature();
		if (signature == lastSignature)
		{
			return;
		}
		lastSignature = signature;
		snapshot = next;
		updateOverlay();
		NavigatorPanel target = panel;
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.update(next));
		}
	}

	private void updateOverlay()
	{
		AccountSnapshot account = snapshot;
		if (!config.showOverlay() || !account.isLoggedIn() || planner == null)
		{
			overlay.setView(null);
			return;
		}
		Goal focus = goals.focus();
		if (focus != null)
		{
			Plan plan = planner.plan(focus, account);
			Step next = plan.getNext();
			overlay.setView(new FocusOverlay.View("CODEX  ·  FOCUS", plan.getTitle(), plan.getProgress(),
				next == null ? "Complete" : stepText(next)));
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

	private static String stepText(Step step)
	{
		if (step.getKind() == Step.Kind.QUEST)
		{
			return (step.getStatus() == Step.Status.IN_PROGRESS ? "Continue " : "Start ") + step.getTitle();
		}
		return step.getTitle();
	}
}
