package net.pluginblueprint.livereload;

/*
 * #%L
 * PlugMan
 * %%
 * Copyright (C) 2010 - 2014 PlugMan
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Event;
import org.bukkit.plugin.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilities for managing plugins. Adapted by inventivetalent from PlugMan, for usage in PluginBlueprint
 *
 * https://github.com/r-clancy/PlugMan/blob/master/src/main/java/com/rylinaux/plugman/util/PluginUtil.java
 *
 * @author rylinaux
 * @author inventivetalent
 */
public class PluginUtil {

	/**
	 * Enable a plugin.
	 *
	 * @param plugin the plugin to enable
	 */
	public static void enable(Plugin plugin) {
		if (plugin != null && !plugin.isEnabled()) {
			Bukkit.getPluginManager().enablePlugin(plugin);
		}
	}

	/**
	 * Disable a plugin.
	 *
	 * @param plugin the plugin to disable
	 */
	public static void disable(Plugin plugin) {
		if (plugin != null && plugin.isEnabled()) {
			Bukkit.getPluginManager().disablePlugin(plugin);
		}
	}

	/**
	 * Loads and enables a plugin.
	 *
	 * @param plugin plugin to load
	 * @return status
	 */
	private static boolean load(Plugin plugin) {
		return load(plugin.getName());
	}

	/**
	 * Loads and enables a plugin.
	 *
	 * @param name plugin's name
	 * @return status
	 */
	public static boolean load(String name) {
		Plugin target = null;
		File pluginDir = new File("plugins");
		if (!pluginDir.isDirectory()) {
			return false;
		}

		File pluginFile = new File(pluginDir, name + ".jar");
		if (!pluginFile.isFile()) {
			//noinspection ConstantConditions
			for (File f : pluginDir.listFiles()) {
				if (f.getName().endsWith(".jar")) {
					try {
						PluginDescriptionFile desc = Main.instance.getPluginLoader().getPluginDescription(f);
						if (desc.getName().equalsIgnoreCase(name)) {
							pluginFile = f;
							break;
						}
					} catch (InvalidDescriptionException e) {
						return false;
					}
				}
			}
		}

		try {
			target = Bukkit.getPluginManager().loadPlugin(pluginFile);
		} catch (InvalidDescriptionException | InvalidPluginException e) {
			e.printStackTrace();
			return false;
		}

		target.onLoad();
		Bukkit.getPluginManager().enablePlugin(target);

		return true;

	}

	/**
	 * Reload a plugin.
	 *
	 * @param plugin the plugin to reload
	 */
	public static void reload(Plugin plugin) {
		if (plugin != null) {
			unload(plugin);
			load(plugin);
		}
	}

	/**
	 * Unload a plugin.
	 *
	 * @param plugin the plugin to unload
	 * @return status
	 */
	@SuppressWarnings("unchecked")
	public static boolean unload(Plugin plugin) {
		String name = plugin.getName();
		PluginManager pluginManager = Bukkit.getPluginManager();

		SimpleCommandMap commandMap = null;
		List<Plugin> plugins = null;
		Map<String, Plugin> names = null;
		Map<String, Command> commands = null;
		Map<Event, SortedSet<RegisteredListener>> listeners = null;

		boolean reloadlisteners = true;
		if (pluginManager != null) {
			pluginManager.disablePlugin(plugin);

			try {
				Field pluginsField = Bukkit.getPluginManager().getClass().getDeclaredField("plugins");
				pluginsField.setAccessible(true);
				plugins = (List<Plugin>) pluginsField.get(pluginManager);

				Field lookupNamesField = Bukkit.getPluginManager().getClass().getDeclaredField("lookupNames");
				lookupNamesField.setAccessible(true);
				names = (Map<String, Plugin>) lookupNamesField.get(pluginManager);

				try {
					Field listenersField = Bukkit.getPluginManager().getClass().getDeclaredField("listeners");
					listenersField.setAccessible(true);
					listeners = (Map<Event, SortedSet<RegisteredListener>>) listenersField.get(pluginManager);
				} catch (Exception e) {
					reloadlisteners = false;
				}

				Field commandMapField = Bukkit.getPluginManager().getClass().getDeclaredField("commandMap");
				commandMapField.setAccessible(true);
				commandMap = (SimpleCommandMap) commandMapField.get(pluginManager);

				Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
				knownCommandsField.setAccessible(true);
				commands = (Map<String, Command>) knownCommandsField.get(commandMap);
			} catch (NoSuchFieldException | IllegalAccessException e) {
				e.printStackTrace();
				return false;
			}

			pluginManager.disablePlugin(plugin);
		}

		if (plugins != null) { plugins.remove(plugin); }
		if (names != null) { names.remove(name); }
		if (listeners != null && reloadlisteners) {
			for (SortedSet<RegisteredListener> set : listeners.values()) {
				set.removeIf(value -> value.getPlugin() == plugin);
			}
		}
		if (commandMap != null) {
			for (Iterator<Map.Entry<String, Command>> it = commands.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<String, Command> entry = it.next();
				if (entry.getValue() instanceof PluginCommand) {
					PluginCommand c = (PluginCommand) entry.getValue();
					if (c.getPlugin() == plugin) {
						c.unregister(commandMap);
						it.remove();
					}
				}
			}
		}

		// Attempt to close the classloader to unlock any handles on the plugin's jar file.
		ClassLoader cl = plugin.getClass().getClassLoader();
		if (cl instanceof URLClassLoader) {
			try {
				Field pluginField = cl.getClass().getDeclaredField("plugin");
				pluginField.setAccessible(true);
				pluginField.set(cl, null);

				Field pluginInitField = cl.getClass().getDeclaredField("pluginInit");
				pluginInitField.setAccessible(true);
				pluginInitField.set(cl, null);
			} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
				Logger.getLogger(PluginUtil.class.getName()).log(Level.SEVERE, null, ex);
			}

			try {
				((URLClassLoader) cl).close();
			} catch (IOException ex) {
				Logger.getLogger(PluginUtil.class.getName()).log(Level.SEVERE, null, ex);
			}
		}

		// Will not work on processes started with the -XX:+DisableExplicitGC flag, but lets try it anyway.
		// This tries to get around the issue where Windows refuses to unlock jar files that were previously loaded into the JVM.
		System.gc();

		return true;
	}

}