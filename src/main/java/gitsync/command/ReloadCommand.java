package gitsync.command;

import gitsync.GitSync;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (commandSender.hasPermission("gs.reload")) {
            commandSender.sendMessage(ChatColor.GREEN + "Reloading configuration...");
            GitSync.getInstance().getLogger().info("Reloading configuration...");
            GitSync.getInstance().loadDataIntoMemory();
            commandSender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
            GitSync.getInstance().getLogger().info("Configuration reloaded.");
        } else {
            commandSender.sendMessage(ChatColor.DARK_RED + "Not enough permissions to do that!");
        }

        return true;
    }
}
