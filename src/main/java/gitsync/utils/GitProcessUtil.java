package gitsync.utils;

import gitsync.GitSync;
import gitsync.Repository;
import gitsync.enums.GitState;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GitProcessUtil {
    private GitProcessUtil() {
    }

    public static String getGitPath() {
        return ExeUtil.getPathOfExecutable("git");
    }

    public static String getGitVersion() {
        final @NotNull List<String> gitVersionOutput = ExeUtil.runCommand("git", "version");
        assert gitVersionOutput.size() == 1;
        return gitVersionOutput.get(0).split(" ")[2];
    }

    public static boolean repoExists(final @NotNull File parentFolder) {
        assert parentFolder.isDirectory();
        return Arrays.stream(parentFolder.listFiles()).anyMatch((file) -> file.getName().equals(".git"));
    }

    // Helpers
    public static void abortMerge(Repository repository) {
        ExeUtil.runCommand(new String[]{"git", "merge", "--abort"}, repository.getDirectory().getAbsoluteFile());
    }

    public static List<String> pull(Repository repository) {
        return ExeUtil.runCommand(new String[]{"git", "pull", "--no-commit", repository.getUpstream(), repository.getBranch()}, repository.getDirectory().getAbsoluteFile());
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
        args.add(repository.getUpstream());
        args.add(repository.getBranch());

        return ExeUtil.runCommand(args.toArray(String[]::new), repository.getDirectory().getAbsoluteFile());
    }

    public static void add(Repository repository) {
        ExeUtil.runCommand(new String[]{"git", "add", "."}, repository.getDirectory().getAbsoluteFile());
    }

    /**
     * Commits added changes to the repository.
     * @return If any changes were committed.
     */
    public static boolean commit(Repository repository, String message) {
        List<String> output = ExeUtil.runCommand(new String[]{"git", "commit", "-m", message}, repository.getDirectory());
        return ExeUtil.isOutputContains(output, GitState.SERVER_COMMIT_TO_FIND);
    }

    public static void configure(Repository repository, String item, String value) {
        ExeUtil.runCommand(new String[]{"git", "config", item, value}, repository.getDirectory());
    }

    public static boolean init(Repository repository) {
        List<String> output = ExeUtil.runCommand(new String[]{"git", "init"}, repository.getDirectory());
        final boolean isSuccess = ExeUtil.isOutputContains(output, GitState.SUCCESS_GIT_INIT);
        if (isSuccess) {
            configure(repository, "user.name", repository.getUsername());
            configure(repository, "user.email", repository.getEmail());
        }
        return isSuccess;
    }

    // Tasks
    public static void createReposWhereNeeded() {
        for (Repository repository : GitSync.Config.getInstance().getRepositories()) {
            if (repository.isEnabled() && !repository.isRepoExists()) {
                File file = new File(GitSync.getInstance().getPluginDirectory(), repository.getName());
                GitSync.getInstance().getLogger().info(String.format("Creating local repo for %s", file.getName()));
                if (init(repository)) {
                    GitSync.getInstance().getLogger().info(String.format("Created local repo for %s", file.getName()));
                } else {
                    GitSync.getInstance().getLogger().severe(String.format("Something got wrong while initializing local repo for %s", file.getName()));
                }
            }
        }
    }

    public static void linkRemotesAndLocals() {
        for (Repository repository : GitSync.Config.getInstance().getRepositories()) {
            if (repository.isEnabled() && repository.isRepoExists() && !repository.getRemote().equals("empty")) {
                GitSync.getInstance().getLogger().info(String.format("Linking local and remote for %s", repository.getName()));
                List<String> output = ExeUtil.runCommand(new String[]{"git", "remote", repository.getRemote()}, repository.getDirectory().getAbsoluteFile());
                if (ExeUtil.isOutputContains(output, repository.getUpstream())) {
                    GitSync.getInstance().getLogger().info(String.format("Local and remote for %s already linked", repository.getName()));
                } else {
                    ExeUtil.runCommand(new String[]{"git", "remote", "add", repository.getUpstream(), repository.getRemote()}, repository.getDirectory().getAbsoluteFile());
                    GitSync.getInstance().getLogger().info(String.format("Linked local and remote for %s", repository.getName()));
                }
            }
        }

    }

    public static void forceSync(Repository repository) {
        GitSync.getInstance().getLogger().info(String.format("Using force sync scenario for %s", repository.getName()));
        List<String> output = push(repository, false, true);
        if (ExeUtil.isOutputContains(output, GitState.FORCED_UPDATE_SUCCESS)) {
            GitSync.getInstance().getLogger().info(String.format("Force sync scenario successfully applied for %s", repository.getName()));
        }
    }

    public static boolean favorableSync(Repository repository) {
        List<String> output = pull(repository);
        if (ExeUtil.isOutputContains(output, GitState.NO_TRACKED_BRANCH)) {
            GitSync.getInstance().getLogger().info(String.format("There is no ref to remote master for %s, will try to push with upstream.", repository.getName()));
            output = push(repository, true, false);
            if (ExeUtil.isOutputContains(output, GitState.SUCCESS_LINK)) {
                GitSync.getInstance().getLogger().info(String.format("Masters of remote and local are linked for %s (no ref to master was there)", repository.getName()));
                return true;
            }
        }

        if (ExeUtil.isOutputContains(output, GitState.AUTOMATIC_MERGE_FAILED)) {
            GitSync.getInstance().getLogger().info(String.format("Can't merge remote and local to sync them, will abort merge for %s", repository.getName()));
            abortMerge(repository);
            GitSync.getInstance().getLogger().info(String.format("Merge for synchronization of %s is aborted, use manual push and pull or enable both scenarios", repository.getName()));
            return false;
        } else if (ExeUtil.isOutputContains(output, GitState.NO_PERMISSION_OR_NO_REPO)) {
            GitSync.getInstance().getLogger().info(String.format("Can't pull or push because of lack of permission, or such remote repo for %s is not exists", repository.getName()));
            return false;
        } else {
            if (ExeUtil.isOutputContains(output, GitState.NO_REF_TO_MASTER)) {
                output = push(repository, true, false);
                if (ExeUtil.isOutputContains(output, GitState.SUCCESS_LINK)) {
                    GitSync.getInstance().getLogger().info(String.format("Masters of remote and local are linked for %s", repository.getName()));
                    return true;
                }
            }

            if (ExeUtil.isOutputContains(output, GitState.AUTOMATIC_MERGE_SUCCESS)) {
                commit(repository, "'[merged]'");
                push(repository, false, false);
                return true;
            } else if (!ExeUtil.isOutputContains(output, GitState.NO_UPDATES_FROM_LOCAL_MANY) && !ExeUtil.isOutputContains(output, GitState.NO_UPDATES_FROM_LOCAL_ONE)) {
                if (ExeUtil.isOutputContains(output, GitState.NO_UPDATES_FROM_REMOTE)) {
                    GitSync.getInstance().getLogger().info(String.format("No changes in remote repo of %s detected, just pushing from server...", repository.getName()));
                    output = push(repository, false, false);
                    if (ExeUtil.isOutputContains(output, GitState.NO_CHANGES_TO_PUSH)) {
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
