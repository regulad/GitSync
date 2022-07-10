package gitsync.command;

import gitsync.Config;
import gitsync.GitSync;
import gitsync.RepoService;
import gitsync.Repository;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.stream.Collectors;

public class SyncCommand implements CommandExecutor, TabCompleter {
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        if (commandSender.hasPermission("gs.sync")) {
            RepoService repoService = new RepoService();
            if (args.length == 0) {
                commandSender.sendMessage(ChatColor.DARK_RED + "Should be specified one of repos to push!");
                return false;
            }

            Repository repository = Config.getInstance().findRepoByName(args[0]);
            ChatColor var10001;
            if (repository == null) {
                var10001 = ChatColor.DARK_RED;
                commandSender.sendMessage("" + var10001 + String.format("Can't find repo with name %s", args[0]));
                return false;
            }

            if (repository.getRemote().equals("empty")) {
                var10001 = ChatColor.YELLOW;
                commandSender.sendMessage("" + var10001 + String.format("Can't sync repo %s because there is no remote repo", repository.getName()));
                return false;
            }

            repoService.addChangesToCommit(repository, GitSync.getInstance().getLogger());
            if (args.length > 2 && args[1].equals("message")) {
                List<String> argsWithoutFirstTwo = new ArrayList<>(Arrays.asList(args));
                argsWithoutFirstTwo.remove(0);
                argsWithoutFirstTwo.remove(0);
                String customMessage = "";

                String part;
                for (Iterator<String> var9 = argsWithoutFirstTwo.iterator(); var9.hasNext(); customMessage = customMessage.concat(part + " ")) {
                    part = var9.next();
                }

                repoService.createCommit(repository, GitSync.getInstance().getLogger(), "'[server update]' " + customMessage);
            } else {
                repoService.createCommit(repository, GitSync.getInstance().getLogger(), "'[server update]'");
            }

            if (repoService.favorableSync(repository, GitSync.getInstance().getLogger())) {
                var10001 = ChatColor.GREEN;
                commandSender.sendMessage("" + var10001 + String.format("Sync for %s repo successfully applied", repository.getName()));
                GitSync.getInstance().getLogger().info(String.format("Sync for %s repo successfully applied. Applier: %s", repository.getName(), commandSender.getName()));
            } else {
                var10001 = ChatColor.DARK_RED;
                commandSender.sendMessage("" + var10001 + String.format("Sync can't be applied because of reasons like conflict in local merge of %s and etc. Use manual push and pull, but your changes in remote will be overwrited!", repository.getName()));
                GitSync.getInstance().getLogger().info(String.format("Sync for repo %s failed. Applier: %s", repository.getName(), commandSender.getName()));
            }
        } else {
            commandSender.sendMessage(ChatColor.DARK_RED + "Not enough permissions to do that!");
        }

        return true;
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] args) {
        if (commandSender.hasPermission("gs.sync")) {
            List<String> reposNames = Config.getInstance().getRepositories().stream().filter((repository) -> repository.isEnabled() && !repository.getRemote().equals("empty")).map(Repository::getName).toList();
            if (args.length == 1) {
                return reposNames.stream().filter((string) -> string.startsWith(args[0])).collect(Collectors.toList());
            }

            if (args.length == 2) {
                return Collections.singletonList("message");
            }
        }

        return null;
    }
}
