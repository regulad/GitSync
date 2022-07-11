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

import java.util.List;
import java.util.stream.Collectors;

public class PullCommand implements CommandExecutor, TabCompleter {
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        if (commandSender.hasPermission("gs.pull")) {
            if (args.length == 0) {
                commandSender.sendMessage(ChatColor.DARK_RED + "Should be specified one of repos to pull!");
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
                commandSender.sendMessage("" + var10001 + String.format("Can't pull repo %s because there is no remote repo", repository.getName()));
                return false;
            }

            GitSync.getInstance().getLogger().info(String.format("Trying to pull. Applier: %s", commandSender.getName()));
            RepoService.add(repository);
            RepoService.commit(repository, "'[server update]'");
            List<String> output = RepoService.pull(repository);
            if (RepoService.isOutputContains(output, "Automatic merge failed")) {
                var10001 = ChatColor.DARK_RED;
                commandSender.sendMessage("" + var10001 + String.format("Can't merge remote and local to sync them, will abort merge for %s", repository.getName()));
                RepoService.abortMerge(repository);
                var10001 = ChatColor.YELLOW;
                commandSender.sendMessage("" + var10001 + String.format("Merge for pull of %s is aborted, use manual push and pull again", repository.getName()));
                GitSync.getInstance().getLogger().info(String.format("Automatic merge failed and aborted for %s. Applier: %s", repository.getName(), commandSender.getName()));
                return true;
            }

            if (RepoService.isOutputContains(output, "Automatic merge went well; stopped before committing as requested")) {
                RepoService.commit(repository, "'[merged]'");
                RepoService.push(repository, false, false);
                var10001 = ChatColor.GREEN;
                commandSender.sendMessage("" + var10001 + String.format("Successful pull of %s to local repo, also automatic merge and push applied", repository.getName()));
                GitSync.getInstance().getLogger().info(String.format("Successful pull of %s to local repo, also automatic merge and push applied. Applier: %s", repository.getName(), commandSender.getName()));
                return true;
            }

            if (RepoService.isOutputContains(output, "files changed") || RepoService.isOutputContains(output, "file changed")) {
                var10001 = ChatColor.GREEN;
                commandSender.sendMessage("" + var10001 + String.format("Successful pull of %s to local repo", repository.getName()));
                GitSync.getInstance().getLogger().info(String.format("Successful pull of %s to local repo. Applier: %s", repository.getName(), commandSender.getName()));
                return true;
            }

            if (RepoService.isOutputContains(output, "Already up to date.")) {
                var10001 = ChatColor.GREEN;
                commandSender.sendMessage("" + var10001 + String.format("No changes in remote repo of %s detected. Local repos is already up-to-date", repository.getName()));
                GitSync.getInstance().getLogger().info(String.format("No changes in remote repo of %s detected. Local repos is already up-to-date. Applier: %s", repository.getName(), commandSender.getName()));
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
        if (commandSender.hasPermission("gs.pull")) {
            List<String> reposNames = Config.getInstance().getRepositories().stream().filter((repository) -> repository.isEnabled() && !repository.getRemote().equals("empty")).map(Repository::getName).toList();
            if (args.length == 1) {
                return reposNames.stream().filter((string) -> string.startsWith(args[0])).collect(Collectors.toList());
            }
        }

        return null;
    }
}
