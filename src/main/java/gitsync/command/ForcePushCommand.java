package gitsync.command;

import gitsync.GitSync;
import gitsync.Repository;
import gitsync.utils.ExeUtil;
import gitsync.utils.GitProcessUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.stream.Collectors;

public class ForcePushCommand implements CommandExecutor, TabCompleter {
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        if (commandSender.hasPermission("gs.push")) {
            if (args.length == 0) {
                commandSender.sendMessage(ChatColor.DARK_RED + "Should be specified one of repos to push!");
                return false;
            }

            Repository repository = GitSync.Config.getInstance().findRepoByName(args[0]);
            ChatColor var10001;
            if (repository == null) {
                var10001 = ChatColor.DARK_RED;
                commandSender.sendMessage("" + var10001 + String.format("Can't find repo with name %s", args[0]));
                return false;
            }

            if (repository.getRemote().equals("empty")) {
                var10001 = ChatColor.YELLOW;
                commandSender.sendMessage("" + var10001 + String.format("Can't push repo %s because there is no remote repo", repository.getName()));
                return false;
            }

            GitSync.getInstance().getLogger().info(String.format("Trying to push force. Applier: %s", commandSender.getName()));
            GitProcessUtil.add(repository);
            if (args.length > 2 && args[1].equals("message")) {
                List<String> argsWithoutFirstTwo = new ArrayList<>(Arrays.asList(args));
                argsWithoutFirstTwo.remove(0);
                argsWithoutFirstTwo.remove(0);
                String customMessage = "";

                String part;
                for (Iterator<String> var9 = argsWithoutFirstTwo.iterator(); var9.hasNext(); customMessage = customMessage.concat(part + " ")) {
                    part = var9.next();
                }

                GitProcessUtil.commit(repository, "'[server update]' " + customMessage);
            } else {
                GitProcessUtil.commit(repository, "'[server update]'");
            }

            List<String> output = GitProcessUtil.push(repository, false, true);
            if (ExeUtil.isOutputContains(output, "Everything up-to-date")) {
                var10001 = ChatColor.GREEN;
                commandSender.sendMessage("" + var10001 + String.format("No forced changes in local repo of %s detected. Remote repos is already up-to-date", repository.getName()));
                GitSync.getInstance().getLogger().info(String.format("No changes forced in local repo of %s detected. Remote repos is already up-to-date. Applier: %s", repository.getName(), commandSender.getName()));
                return true;
            }

            if (ExeUtil.isOutputContains(output, "forced update")) {
                var10001 = ChatColor.GREEN;
                commandSender.sendMessage("" + var10001 + String.format("Force push successfully applied for %s", repository.getName()));
                GitSync.getInstance().getLogger().info(String.format("Force push successfully applied for %s. Applier: %s", repository.getName(), commandSender.getName()));
                return true;
            }

            if (ExeUtil.isOutputContains(output, "failed to push some refs")) {
                var10001 = ChatColor.DARK_RED;
                commandSender.sendMessage("" + var10001 + String.format("Not allowed to apply force push for %s (branch protected)", repository.getName()));
                GitSync.getInstance().getLogger().info(String.format("Not allowed to apply force push for %s (branch protected). Applier: %s", repository.getName(), commandSender.getName()));
                return true;
            }

            var10001 = ChatColor.YELLOW;
            commandSender.sendMessage("" + var10001 + String.format("Unknown type of output for system. Please check output by yourself to validate that is everything is OK (%s)", repository.getName()));
            GitSync.getInstance().getLogger().info(String.format("Unknown type of output for system. Please check output by yourself to validate that is everything is OK (%s). Applier: %s", repository.getName(), commandSender.getName()));
        } else {
            commandSender.sendMessage(ChatColor.DARK_RED + "Not enough permissions to do that!");
        }

        return true;
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] args) {
        if (commandSender.hasPermission("gs.push")) {
            List<String> reposNames = GitSync.Config.getInstance().getRepositories().stream().filter((repository) -> repository.isEnabled() && !repository.getRemote().equals("empty")).map(Repository::getName).toList();
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
