package com.codex.navigator.overlay;

import com.codex.navigator.core.SkillNeeds;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetType;

/**
 * Lays the bars into the game's own skills tab, as Skills Progress Bars does: a few filled rectangles are added as
 * children of each skill's tile (gameval {@code InterfaceID.Stats}), so they scroll, hide and redraw with the tab
 * itself. Nothing is clicked or changed in the game; the rectangles take no input.
 *
 * <p>Client thread only.
 */
public final class SkillTabWidgets
{
	private final Client client;
	/** The widgets made for each skill's tile, a slot per {@link SkillBars#SLOTS}. */
	private final Map<Skill, Widget[]> made = new EnumMap<>(Skill.class);

	public SkillTabWidgets(Client client)
	{
		this.client = client;
	}

	/**
	 * Draws every bar from the live experience. Makes the widgets the first time a tile is seen, and again when
	 * the game has rebuilt the tab and dropped them.
	 */
	public void update(SkillNeeds needs, boolean plainBars)
	{
		for (Skill skill : Skill.values())
		{
			int id = SkillBars.tileId(skill);
			if (id < 0)
			{
				continue;
			}
			Widget tile = client.getWidget(id);
			if (tile == null)
			{
				made.remove(skill);
				continue;
			}
			SkillBars.Rect[] plan = SkillBars.plan(tile.getOriginalWidth(), client.getSkillExperience(skill),
				needs.get(skill), plainBars);
			Widget[] widgets = made.get(skill);
			if (plan == null)
			{
				if (widgets != null && stillThere(tile, widgets))
				{
					hide(widgets);
				}
				continue;
			}
			if (widgets == null || !stillThere(tile, widgets))
			{
				widgets = make(tile);
				made.put(skill, widgets);
			}
			apply(widgets, plan);
		}
	}

	/** Takes every bar out of the tab, as when the plugin stops or the setting is turned off. */
	public void remove()
	{
		for (Map.Entry<Skill, Widget[]> e : made.entrySet())
		{
			Widget tile = client.getWidget(SkillBars.tileId(e.getKey()));
			if (tile == null || !stillThere(tile, e.getValue()))
			{
				continue;
			}
			Widget[] children = tile.getChildren();
			if (children == null)
			{
				continue;
			}
			for (int i = 0; i < children.length; i++)
			{
				for (Widget w : e.getValue())
				{
					if (children[i] == w)
					{
						children[i] = null;
					}
				}
			}
			tile.setChildren(children);
		}
		made.clear();
	}

	/** True while the tile still holds the widgets made for it (a rebuilt tab drops them). */
	private static boolean stillThere(Widget tile, Widget[] widgets)
	{
		Widget[] children = tile.getChildren();
		if (children == null)
		{
			return false;
		}
		for (Widget child : children)
		{
			if (child == widgets[0])
			{
				return true;
			}
		}
		return false;
	}

	private static Widget[] make(Widget tile)
	{
		Widget[] widgets = new Widget[SkillBars.SLOTS];
		for (int i = 0; i < widgets.length; i++)
		{
			Widget w = tile.createChild(-1, WidgetType.RECTANGLE);
			w.setFilled(true);
			w.setHidden(true);
			widgets[i] = w;
		}
		return widgets;
	}

	private static void apply(Widget[] widgets, SkillBars.Rect[] plan)
	{
		for (int i = 0; i < widgets.length; i++)
		{
			Widget w = widgets[i];
			SkillBars.Rect r = plan[i];
			if (r == null)
			{
				w.setHidden(true);
				continue;
			}
			w.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
			w.setYPositionMode(r.fromTop ? WidgetPositionMode.ABSOLUTE_TOP : WidgetPositionMode.ABSOLUTE_BOTTOM);
			w.setOriginalX(r.x);
			w.setOriginalY(r.y);
			w.setOriginalWidth(r.width);
			w.setOriginalHeight(r.height);
			w.setTextColor(r.color.getRGB() & 0xFFFFFF);
			w.setOpacity(r.seeThrough);
			w.setHidden(false);
			w.revalidate();
		}
	}

	private static void hide(Widget[] widgets)
	{
		for (Widget w : widgets)
		{
			w.setHidden(true);
		}
	}
}
