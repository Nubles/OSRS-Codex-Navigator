package com.codex.navigator;

import com.codex.navigator.core.CombatTasks;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Reads the combat achievement tasks the way the game's own Combat Achievements window does. Client thread only.
 *
 * <p>The ids below are the game's, from its cache (revision 242) and its own scripts: the window lists each tier's
 * tasks from one enum of task structs per tier (enums 3981 to 3986, Easy to Grandmaster), reads each task's id, name,
 * description, tier, type and monster from struct params 1306, 1308, 1309, 1310, 1311 and 1312, names the type from
 * enum 3968 and the monster from enum 3971, and tells whether a task is done with script 4834: bit {@code id % 32} of
 * varp {@code CA_TASK_COMPLETED_<id / 32>}.
 */
final class CombatTaskReader
{
	static final int[] TIER_ENUMS = {3981, 3982, 3983, 3984, 3985, 3986};
	static final int TYPE_NAMES_ENUM = 3968;
	static final int MONSTER_NAMES_ENUM = 3971;
	static final int PARAM_ID = 1306;
	static final int PARAM_NAME = 1308;
	static final int PARAM_DESCRIPTION = 1309;
	static final int PARAM_TIER = 1310;
	static final int PARAM_TYPE = 1311;
	static final int PARAM_MONSTER = 1312;

	/** The completion varps in the order script 4834 picks them: task id / 32. */
	static final int[] COMPLETED_VARPS = {
		VarPlayerID.CA_TASK_COMPLETED_0, VarPlayerID.CA_TASK_COMPLETED_1, VarPlayerID.CA_TASK_COMPLETED_2,
		VarPlayerID.CA_TASK_COMPLETED_3, VarPlayerID.CA_TASK_COMPLETED_4, VarPlayerID.CA_TASK_COMPLETED_5,
		VarPlayerID.CA_TASK_COMPLETED_6, VarPlayerID.CA_TASK_COMPLETED_7, VarPlayerID.CA_TASK_COMPLETED_8,
		VarPlayerID.CA_TASK_COMPLETED_9, VarPlayerID.CA_TASK_COMPLETED_10, VarPlayerID.CA_TASK_COMPLETED_11,
		VarPlayerID.CA_TASK_COMPLETED_12, VarPlayerID.CA_TASK_COMPLETED_13, VarPlayerID.CA_TASK_COMPLETED_14,
		VarPlayerID.CA_TASK_COMPLETED_15, VarPlayerID.CA_TASK_COMPLETED_16, VarPlayerID.CA_TASK_COMPLETED_17,
		VarPlayerID.CA_TASK_COMPLETED_18, VarPlayerID.CA_TASK_COMPLETED_19, VarPlayerID.CA_TASK_COMPLETED_20,
	};

	/** The game's points for each tier's reward, Easy first. */
	static final int[] THRESHOLD_VARBITS = {
		VarbitID.CA_THRESHOLD_EASY, VarbitID.CA_THRESHOLD_MEDIUM, VarbitID.CA_THRESHOLD_HARD,
		VarbitID.CA_THRESHOLD_ELITE, VarbitID.CA_THRESHOLD_MASTER, VarbitID.CA_THRESHOLD_GRANDMASTER,
	};

	private CombatTaskReader()
	{
	}

	/** Every task the game lists, or an empty list when its lists are not there to read. */
	static List<CombatTasks.Task> tasks(Client client)
	{
		EnumComposition types = client.getEnum(TYPE_NAMES_ENUM);
		EnumComposition monsters = client.getEnum(MONSTER_NAMES_ENUM);
		List<CombatTasks.Task> out = new ArrayList<>();
		for (int enumId : TIER_ENUMS)
		{
			EnumComposition tier = client.getEnum(enumId);
			if (tier == null)
			{
				return Collections.emptyList();
			}
			for (int structId : tier.getIntVals())
			{
				StructComposition s = client.getStructComposition(structId);
				if (s == null)
				{
					continue;
				}
				int type = s.getIntValue(PARAM_TYPE);
				int monster = s.getIntValue(PARAM_MONSTER);
				out.add(new CombatTasks.Task(s.getIntValue(PARAM_ID), s.getStringValue(PARAM_NAME),
					s.getStringValue(PARAM_DESCRIPTION), s.getIntValue(PARAM_TIER),
					types == null ? null : types.getStringValue(type),
					monsters == null ? null : monsters.getStringValue(monster)));
			}
		}
		return out;
	}

	/** The ids of the tasks the game marks done. */
	static BitSet done(Client client)
	{
		int[] values = new int[COMPLETED_VARPS.length];
		for (int i = 0; i < values.length; i++)
		{
			values[i] = client.getVarpValue(COMPLETED_VARPS[i]);
		}
		return done(values);
	}

	/** Bit {@code id % 32} of the {@code id / 32}th value, for every id. */
	static BitSet done(int[] varps)
	{
		BitSet out = new BitSet();
		for (int i = 0; i < varps.length; i++)
		{
			for (int bit = 0; bit < 32; bit++)
			{
				if ((varps[i] & (1 << bit)) != 0)
				{
					out.set(i * 32 + bit);
				}
			}
		}
		return out;
	}

	static int[] thresholds(Client client)
	{
		int[] out = new int[THRESHOLD_VARBITS.length];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = client.getVarbitValue(THRESHOLD_VARBITS[i]);
		}
		return out;
	}
}
