package com.codex.navigator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;

/** Every setting has a short name, one plain sentence, a section and a place of its own, worded as across the suite. */
public class SettingsTest
{
	@Test
	public void everySettingIsNamedAndPlacedClearly()
	{
		Set<String> sections = new HashSet<>();
		for (Field f : CodexNavigatorConfig.class.getFields())
		{
			if (f.isAnnotationPresent(ConfigSection.class))
			{
				ConfigSection section = f.getAnnotation(ConfigSection.class);
				assertFalse(section.name().isEmpty());
				assertFalse(section.description().isEmpty());
				try
				{
					sections.add((String) f.get(null));
				}
				catch (IllegalAccessException e)
				{
					throw new AssertionError(e);
				}
			}
		}
		Set<String> places = new HashSet<>();
		Set<String> names = new HashSet<>();
		for (Method m : CodexNavigatorConfig.class.getMethods())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			if (item == null || item.hidden())
			{
				continue;
			}
			String name = item.name();
			String description = item.description();
			assertTrue(m.getName(), !name.isEmpty() && name.length() <= 26);
			assertTrue(m.getName(), names.add(name));
			assertTrue(m.getName(), Character.isUpperCase(name.charAt(0)) && !name.endsWith("."));
			assertTrue(m.getName(), description.length() >= 20 && description.length() <= 200);
			assertTrue(m.getName(), Character.isUpperCase(description.charAt(0)) && !description.endsWith("."));
			assertTrue(m.getName() + " has no section", sections.isEmpty() || sections.contains(item.section()));
			assertTrue(m.getName() + " shares its place", places.add(item.section() + "#" + item.position()));
			if ("nameOnCard".equals(item.keyName()))
			{
				assertEquals("Name on cards", name);
			}
			if ("worthOnCard".equals(item.keyName()))
			{
				assertEquals("Worth on cards", name);
			}
		}
		assertFalse(names.isEmpty());
	}

	@Test
	public void savedSettingsKeepTheirKeys()
	{
		// A setting's key is where its value is saved: renaming one quietly resets it for every player who changed it.
		Set<String> keys = new HashSet<>();
		for (Method m : CodexNavigatorConfig.class.getMethods())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			if (item != null)
			{
				keys.add(item.keyName());
			}
		}
		for (String key : new String[]{"announceCompletions", "includeMiniquests", "learnPace", "nameOnCard", "oneLifeCare", "showOverlay", "suggestWithoutFocus"})
		{
			assertTrue(key, keys.contains(key));
		}
	}
}
