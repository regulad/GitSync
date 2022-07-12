package gitsync.utils;

import gitsync.GitSync;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ExeUtil {
    private ExeUtil() {
    }

    public static List<String> runCommand(String[] command, @Nullable File workingDirectory, boolean printOutput) {
        List<String> output = new ArrayList<>();
        final ProcessBuilder processBuilder = new ProcessBuilder(command).directory(workingDirectory);

        try {
            Process process = processBuilder.start();
            final InputStream inputStream = process.getInputStream();
            final InputStream errorStream = process.getErrorStream();
            OutputReader stdoutReader = new OutputReader(inputStream);
            OutputReader stderrReader = new OutputReader(errorStream);
            int exit = process.waitFor();
            final List<String> stdoutLines = stdoutReader.getOutput();
            final List<String> stderrLiens = stderrReader.getOutput();

            if (printOutput) {
                stdoutLines.forEach(GitSync.getInstance().getLogger()::warning);
                stderrLiens.forEach(GitSync.getInstance().getLogger()::severe);
            }
            // Note: This doesn't respect when things come through, and it will only log them after git doing it's work.
            output.addAll(stdoutLines);
            output.addAll(stderrLiens);
        } catch (InterruptedException | IOException var7) {
            var7.printStackTrace();
        }

        return output;
    }

    public static List<String> runCommand(String[] command, @Nullable File workingDirectory) {
        return runCommand(command, workingDirectory, !GitSync.getInstance().getGitSyncConfig().isQuiet());
    }

    public static List<String> runCommand(String... command) {
        return runCommand(command, null);
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

    public static @Nullable String getPathOfExecutableWindows(@NotNull String executable) {
        String path = runCommand("where", executable).get(0);
        if (path.contains("\\")) {
            return path.substring(0, path.lastIndexOf("\\"));
        } else {
            return null;
        }
    }

    public static @Nullable String getPathOfExecutableMac(@NotNull String executable) {
        String path = runCommand("which", executable).get(0);
        if (path.contains("/")) {
            return path.substring(0, path.lastIndexOf("/"));
        } else {
            return null;
        }
    }

    public static @Nullable String getPathOfExecutableUnix(@NotNull String executable) {
        String path = runCommand("whereis", executable).get(0);
        if (path.contains("/")) {
            return path.substring(0, path.lastIndexOf("/"));
        } else {
            return null;
        }
    }

    public static @Nullable String getPathOfExecutable(final @NotNull String executable) {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            return getPathOfExecutableWindows(executable);
        } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            return getPathOfExecutableMac(executable);
        } else {
            return getPathOfExecutableUnix(executable);
        }
    }
}
