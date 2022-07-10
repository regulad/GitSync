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

    public void createReposWhereNeeded(File dirWithPlugins, Logger logger) {

        for (Repository repository : Config.getInstance().getRepositories()) {
            if (repository.isEnabled() && !repository.isLocalRepoCreated()) {
                File file = new File(dirWithPlugins, repository.getName());
                logger.info(String.format("Creating local repo for %s", file.getName()));
                ProcessBuilder processBuilder = new ProcessBuilder("git", "init");
                processBuilder.directory(file.getAbsoluteFile());
                List<String> output = this.executeCommand(processBuilder);
                if (this.isOutputContains(output, SUCCESS_GIT_INIT)) {
                    logger.info(String.format("Created local repo for %s", file.getName()));
                    repository.setLocalRepoCreated(true);
                } else {
                    logger.warning(String.format("Something got wrong while initializing local repo for %s", file.getName()));
                }
            }
        }

    }

    public void recreateGitIgnores(Logger logger) {
        Iterator<Repository> var2 = Config.getInstance().getRepositories().iterator();

        while (var2.hasNext()) {
            Repository repository = var2.next();
            if (repository.isEnabled() && repository.isLocalRepoCreated()) {
                File gitIgnore = new File(repository.getDirectory(), ".gitignore");
                logger.info(String.format("Recreating .gitignore for repo %s ...", repository.getName()));
                if (gitIgnore.exists()) {
                    gitIgnore.delete();
                }

                if (!repository.getIgnoreList().isEmpty()) {
                    try {
                        gitIgnore.createNewFile();
                    } catch (IOException var7) {
                        logger.warning(String.format("Cannot create .gitignore file for repo %s", repository.getName()));
                        return;
                    }

                    try {
                        Files.write(Paths.get(gitIgnore.toURI()), repository.getIgnoreList(), StandardCharsets.UTF_8, StandardOpenOption.WRITE);
                    } catch (IOException var6) {
                        logger.warning(String.format("Cannot write into .gitignore file for repo %s", repository.getName()));
                    }
                }

                logger.info(String.format("Recreated .gitignore for repo %s", repository.getName()));
            }
        }

    }

    public void linkRemotesAndLocals(Logger logger) {

        for (Repository repository : Config.getInstance().getRepositories()) {
            if (repository.isEnabled() && repository.isLocalRepoCreated() && !repository.getRemote().equals("empty")) {
                logger.info(String.format("Linking local and remote for %s", repository.getName()));
                ProcessBuilder processBuilder = new ProcessBuilder("git", "remote");
                processBuilder.directory(repository.getDirectory().getAbsoluteFile());
                List<String> output = this.executeCommand(processBuilder);
                if (this.isOutputContains(output, REMOTE_PRESENT)) {
                    logger.info(String.format("Local and remote for %s already linked", repository.getName()));
                } else {
                    processBuilder = new ProcessBuilder("git", "remote", "add", "origin", repository.getRemote()); // Authentication stuff will probably go here
                    processBuilder.directory(repository.getDirectory().getAbsoluteFile());
                    this.executeCommand(processBuilder);
                    logger.info(String.format("Linked local and remote for %s", repository.getName()));
                }
            }
        }

    }

    public void dailySync(Logger logger) {

        for (Repository o : Config.getInstance().getRepositories()) {
            if (o.isEnabled() && o.isLocalRepoCreated()) {
                this.addChangesToCommit(o, logger);
                this.createCommit(o, logger, SERVER_COMMIT_TO_MAKE);
                if (!o.getRemote().equals("empty")) {
                    switch (Config.getInstance().getScenarioWhileDailySync()) {
                        case ALL:
                            if (!this.favorableSync(o, logger)) {
                                this.unfavorableSync(o, logger);
                            }
                            break;
                        case FAVORABLE:
                            this.favorableSync(o, logger);
                            break;
                        case FORCE:
                            this.unfavorableSync(o, logger);
                    }
                }
            }
        }

    }

    public void unfavorableSync(Repository repository, Logger logger) {
        logger.info(String.format("Using force sync scenario for %s", repository.getName()));
        List<String> output = this.pushForce(repository, logger);
        if (this.isOutputContains(output, FORCED_UPDATE_SUCCESS)) {
            logger.info(String.format("Force sync scenario successfully applied for %s", repository.getName()));
        }

    }

    public boolean favorableSync(Repository repository, Logger logger) {
        List<String> output = this.pull(repository, logger);
        this.printOutput(output, logger);
        if (this.isOutputContains(output, NO_TRACKED_BRANCH)) {
            logger.info(String.format("There is no ref to remote master for %s, will try to push with upstream.", repository.getName()));
            output = this.pushWithUpstream(repository, logger);
            if (this.isOutputContains(output, SUCCESS_LINK)) {
                logger.info(String.format("Masters of remote and local are linked for %s (no ref to master was there)", repository.getName()));
                return true;
            }
        }

        if (this.isOutputContains(output, AUTOMATIC_MERGE_FAILED)) {
            logger.info(String.format("Can't merge remote and local to sync them, will abort merge for %s", repository.getName()));
            this.abortMerge(repository, logger);
            logger.info(String.format("Merge for synchronization of %s is aborted, use manual push and pull or enable both scenarios", repository.getName()));
            return false;
        } else if (this.isOutputContains(output, NO_PERMISSION_OR_NO_REPO)) {
            logger.info(String.format("Can't pull or push because of lack of permission, or such remote repo for %s is not exists", repository.getName()));
            return false;
        } else {
            if (this.isOutputContains(output, NO_REF_TO_MASTER)) {
                output = this.pushWithUpstream(repository, logger);
                if (this.isOutputContains(output, SUCCESS_LINK)) {
                    logger.info(String.format("Masters of remote and local are linked for %s", repository.getName()));
                    return true;
                }
            }

            if (this.isOutputContains(output, AUTOMATIC_MERGE_SUCCESS)) {
                this.createCommit(repository, logger, "'[merged]'");
                this.push(repository, logger);
                return true;
            } else if (!this.isOutputContains(output, NO_UPDATES_FROM_LOCAL_MANY) && !this.isOutputContains(output, NO_UPDATES_FROM_LOCAL_ONE)) {
                if (this.isOutputContains(output, NO_UPDATES_FROM_REMOTE)) {
                    logger.info(String.format("No changes in remote repo of %s detected, just pushing from server...", repository.getName()));
                    output = this.push(repository, logger);
                    if (this.isOutputContains(output, NO_CHANGES_TO_PUSH)) {
                        logger.info(String.format("There are no changes to push to remote for %s", repository.getName()));
                    } else {
                        logger.info(String.format("Changes from server pushed to remote for %s", repository.getName()));
                    }

                    return true;
                } else {
                    return false;
                }
            } else {
                logger.info(String.format("Changes just pulled from remote repo to local for %s", repository.getName()));
                return true;
            }
        }
    }

    public void printOutput(List<String> output, Logger logger) {
        output.forEach((string) -> logger.info(String.format("OUTPUT > %s", string)));
    }

    public void abortMerge(Repository repository, Logger logger) {
        ProcessBuilder processBuilder = new ProcessBuilder("git", "merge", "--abort");
        processBuilder.directory(repository.getDirectory().getAbsoluteFile());
        this.executeCommand(processBuilder);
    }

    public List<String> pull(Repository repository, Logger logger) {
        ProcessBuilder processBuilder = new ProcessBuilder("git", "pull", "--no-commit", "origin", "master");
        processBuilder.directory(repository.getDirectory().getAbsoluteFile());
        return this.executeCommand(processBuilder);
    }

    public List<String> push(Repository repository, Logger logger) {
        ProcessBuilder processBuilder = new ProcessBuilder("git", "push", "origin", "master");
        processBuilder.directory(repository.getDirectory().getAbsoluteFile());
        return this.executeCommand(processBuilder);
    }

    public List<String> pushForce(Repository repository, Logger logger) {
        ProcessBuilder processBuilder = new ProcessBuilder("git", "push", "-f", "origin", "master");
        processBuilder.directory(repository.getDirectory().getAbsoluteFile());
        List<String> output = this.executeCommand(processBuilder);
        this.printOutput(output, logger);
        return output;
    }

    private List<String> pushWithUpstream(Repository repository, Logger logger) {
        ProcessBuilder processBuilder = new ProcessBuilder("git", "push", "--set-upstream", "origin", "master");
        processBuilder.directory(repository.getDirectory().getAbsoluteFile());
        return this.executeCommand(processBuilder);
    }

    public void addChangesToCommit(Repository repository, Logger logger) {
        logger.info(String.format("Adding changes to future commit for %s", repository.getName()));
        ProcessBuilder processBuilder = new ProcessBuilder("git", "add", ".");
        processBuilder.directory(repository.getDirectory().getAbsoluteFile());
        this.executeCommand(processBuilder);
        logger.info(String.format("Added changes to future commit for %s", repository.getName()));
    }

    public void createCommit(Repository repository, Logger logger, String message) {
        logger.info(String.format("Creating commit for latest changes for %s", repository.getName()));
        ProcessBuilder processBuilder = new ProcessBuilder("git", "commit", "-m", message);
        processBuilder.directory(repository.getDirectory());
        List<String> output = this.executeCommand(processBuilder);
        if (this.isOutputContains(output, SERVER_COMMIT_TO_FIND)) {
            logger.info(String.format("Created commit for latest changes for %s", repository.getName()));
        } else {
            logger.info(String.format("There are no changes to commit for %s", repository.getName()));
        }

    }

    private List<String> executeCommand(ProcessBuilder processBuilder) {
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

    public boolean isOutputContains(List<String> outputToScan, String string) {
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
}
