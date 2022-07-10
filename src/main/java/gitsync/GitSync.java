package gitsync;

import gitsync.command.ForcePushCommand;
import gitsync.command.PullCommand;
import gitsync.command.ReloadCommand;
import gitsync.command.SyncCommand;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class GitSync extends JavaPlugin {
    @Getter
    private static GitSync instance;

    public void onEnable() {
        new Metrics(this, 14817);
        instance = this;
        this.loadDataIntoMemory();
        RepoService repoService = new RepoService();
        repoService.dailySync(this.getLogger());
        this.getCommand("gsreload").setExecutor(new ReloadCommand());
        this.getCommand("gssync").setExecutor(new SyncCommand());
        this.getCommand("gspush").setExecutor(new ForcePushCommand());
        this.getCommand("gspull").setExecutor(new PullCommand());
    }

    public void loadDataIntoMemory() {
        Config.clear();
        File configFile = this.checkConfig(this.getDataFolder());
        this.loadConfig(configFile, this.getDataFolder().getParentFile());
        RepoService repoService = new RepoService();
        repoService.createReposWhereNeeded(this.getDataFolder().getParentFile(), this.getLogger());
        repoService.linkRemotesAndLocals(this.getLogger());
        repoService.recreateGitIgnores(this.getLogger());
    }

    private File checkConfig(File directory) {
        if (!directory.exists()) {
            directory.mkdir();
        }

        File config = new File(directory, "config.yml");
        if (!config.exists()) {
            try {
                config.createNewFile();
            } catch (IOException var8) {
                this.getLogger().warning("Cannot create main config!");
            }
        }

        List<String> names = this.getNamesOfEachPluginFolder(Arrays.stream(directory.getParentFile().listFiles()).filter(File::isDirectory).collect(Collectors.toList()));
        FileConfiguration fileConfiguration = YamlConfiguration.loadConfiguration(config);

        for (String name : names) {
            if (!fileConfiguration.contains(name)) {
                fileConfiguration.createSection(name);
            }

            if (!fileConfiguration.contains(name + ".enabled")) {
                fileConfiguration.set(name + ".enabled", false);
            }

            if (!fileConfiguration.contains(name + ".remote")) {
                fileConfiguration.set(name + ".remote", "empty");
            }
        }

        if (!fileConfiguration.contains("dailySync")) {
            fileConfiguration.set("dailySync", Config.Scenario.ALL.toString());
        }

        try {
            fileConfiguration.save(config);
        } catch (IOException var7) {
            this.getLogger().warning("Cannot save main config!");
        }

        return config;
    }

    private void loadConfig(File configFile, File dirOfPlugins) {
        FileConfiguration fileConfiguration = YamlConfiguration.loadConfiguration(configFile);

        for (String pluginSection : fileConfiguration.getConfigurationSection("").getKeys(false)) {
            File dirOfSomePlugin = new File(dirOfPlugins, pluginSection);
            if (dirOfSomePlugin.exists()) {
                Repository repository = new Repository();
                repository.setEnabled(fileConfiguration.getBoolean(pluginSection + ".enabled"));
                repository.setName(pluginSection);
                repository.setRemote(fileConfiguration.getString(pluginSection + ".remote"));
                repository.setLocalRepoCreated(this.isLocalRepoCreated(dirOfSomePlugin));
                repository.setDirectory(dirOfSomePlugin);
                if (fileConfiguration.contains(pluginSection + ".exclude")) {
                    repository.setIgnoreList(fileConfiguration.getStringList(pluginSection + ".exclude"));
                } else {
                    repository.setIgnoreList(new ArrayList<>());
                }

                Config.getInstance().getRepositories().add(repository);
            }
        }

        Config.getInstance().setScenarioWhileDailySync(Config.Scenario.valueOf(fileConfiguration.getString("dailySync")));
    }

    private boolean isLocalRepoCreated(File dirOfSomePlugin) {
        List<File> filesInSomePluginDir = Arrays.asList(dirOfSomePlugin.listFiles());
        Iterator<File> var3 = filesInSomePluginDir.iterator();

        File eachFile;
        do {
            if (!var3.hasNext()) {
                return false;
            }

            eachFile = var3.next();
        } while (!eachFile.getName().equals(".git"));

        return true;
    }

    private List<String> getNamesOfEachPluginFolder(List<File> files) {
        return files.stream().map(File::getName).collect(Collectors.toList());
    }
}
