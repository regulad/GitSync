package gitsync;

import gitsync.utils.OutputReader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

public class RepoService {
    // Following block of literals is for git return messages.
    private static final String SUCCESS_GIT_INIT = "Initialized empty Git repository";
    private static final String REMOTE_PRESENT = "origin";
    private static final String SUCCESS_LINK = "Branch 'master' set up to track remote branch 'master' from 'origin'.";
    public static final String FORCED_UPDATE_SUCCESS = "forced update";
    public static final String AUTOMATIC_MERGE_FAILED = "Automatic merge failed";
    public static final String AUTOMATIC_MERGE_SUCCESS = "Automatic merge went well; stopped before committing as requested";
    private static final String NO_TRACKED_BRANCH = "fatal: couldn't find remote ref master";
    public static final String NO_UPDATES_FROM_LOCAL_MANY = "files changed";
    public static final String NO_UPDATES_FROM_LOCAL_ONE = "file changed";
    public static final String NO_UPDATES_FROM_REMOTE = "Already up to date.";
    public static final String NO_CHANGES_TO_PUSH = "Everything up-to-date";
    private static final String SERVER_COMMIT_TO_FIND = "[server update]";
    public static final String SERVER_COMMIT_TO_MAKE = "'[server update]'";
    public static final String NO_REF_TO_MASTER = NO_TRACKED_BRANCH;
    private static final String NO_PERMISSION_OR_NO_REPO = "remote: The project you were looking for could not be found or you don't have permission to view it.";
    public static final String FAILED_TO_PUSH_SOME_REFS = "failed to push some refs";

    private RepoService() {
    }

    public static Logger getLogger() {
        return GitSync.getInstance().getLogger();
    }

    public static void printOutput(List<String> output) {
        output.forEach((string) -> getLogger().warning(String.format("git output > %s", string)));
    }

    public static List<String> executeCommand(ProcessBuilder processBuilder) {
        List<String> output = new ArrayList<>();

        try {
            Process process = processBuilder.start();
            OutputReader outputReader = new OutputReader(process.getInputStream());
            OutputReader errorReader = new OutputReader(process.getErrorStream());
            int exit = process.waitFor();
            output.addAll(outputReader.getOutput());
            output.addAll(errorReader.getOutput());
        } catch (InterruptedException | IOException var7) {
            var7.printStackTrace();
        }

        return output;
    }

    public static List<String> executeCommand(String... command) {
        return executeCommand(new ProcessBuilder(command));
    }

    public static List<String> executeCommand(String[] command, File workingDirectory) {
        return executeCommand(new ProcessBuilder(command).directory(workingDirectory));
    }

    public static List<String> runCommand(String... command) {
        List<String> output = executeCommand(command);
        printOutput(output);
        return output;
    }

    public static List<String> runCommand(String[] command, File workingDirectory) {
        List<String> output = executeCommand(command, workingDirectory);
        printOutput(output);
        return output;
    }

    public static boolean isOutputContains(List<String> outputToScan, String string) {
        Iterator<String> var3 = outputToScan.iterator();

        String out;
        do {
            if (!var3.hasNext()) {
                return false;
            }

            out = var3.next();
        } while (!out.contains(string));

        return true;
    }

    // Helpers
    public static void abortMerge(Repository repository) {
        runCommand(new String[]{"git", "merge", "--abort"}, repository.getDirectory().getAbsoluteFile());
    }

    public static List<String> pull(Repository repository) {
        return runCommand(new String[]{"git", "pull", "--no-commit", "origin", "master"}, repository.getDirectory().getAbsoluteFile());
    }

    public static List<String> push(Repository repository, boolean setUpstream, boolean force) {
        ArrayList<String> args = new ArrayList<>();

        args.add("git");
        args.add("push");
        if (setUpstream) {
            args.add("--set-upstream");
        }
        if (force) {
            args.add("-f");
        }
        args.add("origin");
        args.add("master");

        return runCommand(args.toArray(String[]::new), repository.getDirectory().getAbsoluteFile());
    }

    public static void add(Repository repository) {
        runCommand(new String[]{"git", "add", "."}, repository.getDirectory().getAbsoluteFile());
    }

    /**
     * Commits added changes to the repository.
     * @return If any changes were committed.
     */
    public static boolean commit(Repository repository, String message) {
        List<String> output = runCommand(new String[]{"git", "commit", "-m", message}, repository.getDirectory());
        return isOutputContains(output, SERVER_COMMIT_TO_FIND);
    }

    public static void configure(Repository repository, String item, String value) {
        runCommand(new String[]{"git", "config", item, value}, repository.getDirectory());
    }

    public static boolean init(Repository repository) {
        List<String> output = runCommand(new String[]{"git", "init"}, repository.getDirectory());
        final boolean isSuccess = isOutputContains(output, SUCCESS_GIT_INIT);
        if (isSuccess) {
            configure(repository, "user.name", "GitSync");
            configure(repository, "user.email", "gitsync@regulad.xyz");
        }
        return isSuccess;
    }

    // Tasks
    public static void createReposWhereNeeded(File pluginDirectory) {
        for (Repository repository : Config.getInstance().getRepositories()) {
            if (repository.isEnabled() && !repository.isLocalRepoCreated()) {
                File file = new File(pluginDirectory, repository.getName());
                getLogger().info(String.format("Creating local repo for %s", file.getName()));
                if (init(repository)) {
                    repository.setLocalRepoCreated(true);
                    getLogger().info(String.format("Created local repo for %s", file.getName()));
                } else {
                    getLogger().severe(String.format("Something got wrong while initializing local repo for %s", file.getName()));
                }
            }
        }

    }

    public static void recreateGitIgnores() {
        for (Repository repository : Config.getInstance().getRepositories()) {
            if (repository.isEnabled() && repository.isLocalRepoCreated()) {
                File gitIgnore = new File(repository.getDirectory(), ".gitignore");
                GitSync.getInstance().getLogger().info(String.format("Recreating .gitignore for repo %s ...", repository.getName()));
                if (gitIgnore.exists()) {
                    gitIgnore.delete();
                }

                if (!repository.getIgnoreList().isEmpty()) {
                    try {
                        gitIgnore.createNewFile();
                    } catch (IOException var7) {
                        GitSync.getInstance().getLogger().warning(String.format("Cannot create .gitignore file for repo %s", repository.getName()));
                        return;
                    }

                    try {
                        Files.write(Paths.get(gitIgnore.toURI()), repository.getIgnoreList(), StandardCharsets.UTF_8, StandardOpenOption.WRITE);
                    } catch (IOException var6) {
                        GitSync.getInstance().getLogger().warning(String.format("Cannot write into .gitignore file for repo %s", repository.getName()));
                    }
                }

                GitSync.getInstance().getLogger().info(String.format("Recreated .gitignore for repo %s", repository.getName()));
            }
        }

    }

    public static void linkRemotesAndLocals() {
        for (Repository repository : Config.getInstance().getRepositories()) {
            if (repository.isEnabled() && repository.isLocalRepoCreated() && !repository.getRemote().equals("empty")) {
                GitSync.getInstance().getLogger().info(String.format("Linking local and remote for %s", repository.getName()));
                ProcessBuilder processBuilder = new ProcessBuilder("git", "remote");
                processBuilder.directory(repository.getDirectory().getAbsoluteFile());
                List<String> output = executeCommand(processBuilder);
                if (isOutputContains(output, REMOTE_PRESENT)) {
                    GitSync.getInstance().getLogger().info(String.format("Local and remote for %s already linked", repository.getName()));
                } else {
                    processBuilder = new ProcessBuilder("git", "remote", "add", "origin", repository.getRemote()); // Authentication stuff will probably go here
                    processBuilder.directory(repository.getDirectory().getAbsoluteFile());
                    executeCommand(processBuilder);
                    GitSync.getInstance().getLogger().info(String.format("Linked local and remote for %s", repository.getName()));
                }
            }
        }

    }

    public static void dailySync() {
        for (Repository o : Config.getInstance().getRepositories()) {
            if (o.isEnabled() && o.isLocalRepoCreated()) {
                add(o);
                commit(o, SERVER_COMMIT_TO_MAKE);
                if (!o.getRemote().equals("empty")) {
                    switch (Config.getInstance().getScenarioWhileDailySync()) {
                        case ALL:
                            if (!favorableSync(o)) {
                                unfavorableSync(o);
                            }
                            break;
                        case FAVORABLE:
                            favorableSync(o);
                            break;
                        case FORCE:
                            unfavorableSync(o);
                    }
                }
            }
        }

    }

    public static void unfavorableSync(Repository repository) {
        GitSync.getInstance().getLogger().info(String.format("Using force sync scenario for %s", repository.getName()));
        List<String> output = push(repository, false, true);
        if (isOutputContains(output, FORCED_UPDATE_SUCCESS)) {
            GitSync.getInstance().getLogger().info(String.format("Force sync scenario successfully applied for %s", repository.getName()));
        }
    }

    public static boolean favorableSync(Repository repository) {
        List<String> output = pull(repository);
        if (isOutputContains(output, NO_TRACKED_BRANCH)) {
            GitSync.getInstance().getLogger().info(String.format("There is no ref to remote master for %s, will try to push with upstream.", repository.getName()));
            output = push(repository, true, false);
            if (isOutputContains(output, SUCCESS_LINK)) {
                GitSync.getInstance().getLogger().info(String.format("Masters of remote and local are linked for %s (no ref to master was there)", repository.getName()));
                return true;
            }
        }

        if (isOutputContains(output, AUTOMATIC_MERGE_FAILED)) {
            GitSync.getInstance().getLogger().info(String.format("Can't merge remote and local to sync them, will abort merge for %s", repository.getName()));
            abortMerge(repository);
            GitSync.getInstance().getLogger().info(String.format("Merge for synchronization of %s is aborted, use manual push and pull or enable both scenarios", repository.getName()));
            return false;
        } else if (isOutputContains(output, NO_PERMISSION_OR_NO_REPO)) {
            GitSync.getInstance().getLogger().info(String.format("Can't pull or push because of lack of permission, or such remote repo for %s is not exists", repository.getName()));
            return false;
        } else {
            if (isOutputContains(output, NO_REF_TO_MASTER)) {
                output = push(repository, true, false);
                if (isOutputContains(output, SUCCESS_LINK)) {
                    GitSync.getInstance().getLogger().info(String.format("Masters of remote and local are linked for %s", repository.getName()));
                    return true;
                }
            }

            if (isOutputContains(output, AUTOMATIC_MERGE_SUCCESS)) {
                commit(repository, "'[merged]'");
                push(repository, false, false);
                return true;
            } else if (!isOutputContains(output, NO_UPDATES_FROM_LOCAL_MANY) && !isOutputContains(output, NO_UPDATES_FROM_LOCAL_ONE)) {
                if (isOutputContains(output, NO_UPDATES_FROM_REMOTE)) {
                    GitSync.getInstance().getLogger().info(String.format("No changes in remote repo of %s detected, just pushing from server...", repository.getName()));
                    output = push(repository, false, false);
                    if (isOutputContains(output, NO_CHANGES_TO_PUSH)) {
                        GitSync.getInstance().getLogger().info(String.format("There are no changes to push to remote for %s", repository.getName()));
                    } else {
                        GitSync.getInstance().getLogger().info(String.format("Changes from server pushed to remote for %s", repository.getName()));
                    }

                    return true;
                } else {
                    return false;
                }
            } else {
                GitSync.getInstance().getLogger().info(String.format("Changes just pulled from remote repo to local for %s", repository.getName()));
                return true;
            }
        }
    }
}
