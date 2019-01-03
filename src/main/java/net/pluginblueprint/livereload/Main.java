package net.pluginblueprint.livereload;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

	private static   String PREFIX = "[PB] ";
	static Main   instance;

	@Override
	public void onEnable() {
		instance = this;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if ("pluginblueprint".equalsIgnoreCase(label)) {
			if (args.length <= 0) {
				sender.sendMessage(PREFIX + "Missing action argument");
				return false;
			}
			if (args.length <= 1) {
				sender.sendMessage(PREFIX + "Missing plugin name argument");
				return false;
			}
			String action = args[0];
			String name = args[1];

			sender.sendMessage(PREFIX + "Performing " + action + " for " + name);

			if ("enable".equalsIgnoreCase(action)) {
				PluginUtil.enable(Bukkit.getPluginManager().getPlugin(name));
			} else if ("disable".equalsIgnoreCase(action)) {
				PluginUtil.disable(Bukkit.getPluginManager().getPlugin(name));
			} else if ("load".equalsIgnoreCase(action)) {
				PluginUtil.load(name);
			} else if ("reload".equalsIgnoreCase(action)) {
				PluginUtil.reload(Bukkit.getPluginManager().getPlugin(name));
			} else if ("unload".equalsIgnoreCase(action)) {
				PluginUtil.unload(Bukkit.getPluginManager().getPlugin(name));
			} else {
				sender.sendMessage(PREFIX + "Unknown action: " + action);
			}

			return true;
		}
		return false;
	}
}
