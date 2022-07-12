package gitsync.enums;

public class GitState {
    // Following block of literals is for git return messages.
    public static final String SUCCESS_GIT_INIT = "Initialized empty Git repository";
    public static final String SUCCESS_LINK = "Branch 'master' set up to track remote branch 'master' from 'origin'.";
    public static final String FORCED_UPDATE_SUCCESS = "forced update";
    public static final String AUTOMATIC_MERGE_FAILED = "Automatic merge failed";
    public static final String AUTOMATIC_MERGE_SUCCESS = "Automatic merge went well; stopped before committing as requested";
    public static final String NO_TRACKED_BRANCH = "fatal: couldn't find remote ref master";
    public static final String NO_UPDATES_FROM_LOCAL_MANY = "files changed";
    public static final String NO_UPDATES_FROM_LOCAL_ONE = "file changed";
    public static final String NO_UPDATES_FROM_REMOTE = "Already up to date.";
    public static final String NO_CHANGES_TO_PUSH = "Everything up-to-date";
    public static final String SERVER_COMMIT_TO_FIND = "[server update]";
    public static final String SERVER_COMMIT_TO_MAKE = "[server update]";
    public static final String NO_REF_TO_MASTER = NO_TRACKED_BRANCH;
    public static final String NO_PERMISSION_OR_NO_REPO = "remote: The project you were looking for could not be found or you don't have permission to view it.";

    private GitState() {
    }
}
