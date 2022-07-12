package gitsync;

import gitsync.command.ForcePushCommand;
import gitsync.command.PullCommand;
import gitsync.command.SyncCommand;
import gitsync.enums.GitState;
import gitsync.enums.Scenario;
import gitsync.utils.GitProcessUtil;
import lombok.Getter;
import lombok.Setter;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class GitSync extends JavaPlugin {
    @Getter
    private static GitSync instance;
    @Getter
    private final Config gitSyncConfig = new Config();

    public void dailySync() {
        for (Repository o : this.gitSyncConfig.getRepositories()) {
            if (o.isEnabled() && o.isRepoExists()) {
                GitProcessUtil.add(o);
                GitProcessUtil.commit(o, GitState.SERVER_COMMIT_TO_MAKE);
                if (!o.getRemote().equals("empty")) {
                    switch (Config.getInstance().getScenarioWhileDailySync()) {
                        case ALL:
                            if (!GitProcessUtil.favorableSync(o)) {
                                GitProcessUtil.forceSync(o);
                            }
                            break;
                        case FAVORABLE:
                            GitProcessUtil.favorableSync(o);
                            break;
                        case FORCE:
                            GitProcessUtil.forceSync(o);
                    }
                }
            }
        }

    }

    public File getPluginDirectory() {
        return this.getDataFolder().getParentFile(); // This could be wrong if you have a really weird folder structure
    }

    public void onEnable() {
        new Metrics(this, 14817);
        instance = this;

        this.saveDefaultConfig();

        if (GitProcessUtil.getGitPath() == null) {
            this.getLogger().severe("Git is not installed. Please install git and reload this plugin. Disabling...");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        } else {
            this.getLogger().info("Git is installed. Using git version: " + GitProcessUtil.getGitVersion());
        }

        // Check/Repair configuration

        this.getGitSyncConfig().attemptRepair();

        GitProcessUtil.createReposWhereNeeded();
        GitProcessUtil.linkRemotesAndLocals();

        dailySync();

        this.getCommand("gssync").setExecutor(new SyncCommand());
        this.getCommand("gspush").setExecutor(new ForcePushCommand());
        this.getCommand("gspull").setExecutor(new PullCommand());

        // Register a runnable for dailySyncing
    }

    public class Config {
        @Getter
        @Setter
        private static Config instance;

        private Config() {
            instance = this;
        }

        private @NotNull ConfigurationSection getPluginsSection() {
            return GitSync.this.getConfig().getConfigurationSection("plugins");
        }

        private static @NotNull Repository getRepository(final @NotNull ConfigurationSection pluginSection, final @NotNull File directory) {
            final @NotNull Repository repository = new Repository();

            repository.setName(pluginSection.getName());

            repository.setDirectory(directory);
            repository.setConfig(pluginSection);

            repository.setPlugin(Stream.of(Bukkit.getServer().getPluginManager().getPlugins()).filter(plugin -> plugin.getDataFolder().getName().equals(repository.getName())).findFirst().orElse(null));

            repository.setEnabled(pluginSection.getBoolean("enabled"));
            if (pluginSection.getString("remote") != null) {
                repository.setRemote(pluginSection.getString("remote"));
            }
            if (pluginSection.getString("username") != null) {
                repository.setUsername(pluginSection.getString("username"));
            }
            if (pluginSection.getString("email") != null) {
                repository.setEmail(pluginSection.getString("email"));
            }
            if (pluginSection.getString("upstream") != null) {
                repository.setUpstream(pluginSection.getString("upstream"));
            }
            if (pluginSection.getString("branch") != null) {
                repository.setBranch(pluginSection.getString("branch"));
            }

            return repository;
        }

        private void attemptRepair() {
            // Create configuration sections
            final @NotNull List<@NotNull String> folderNames = Arrays.stream(getPluginDirectory().listFiles()).filter(File::isDirectory).map(File::getName).toList();
            for (final @NotNull String folderName : folderNames) {
                final @NotNull File pluginFolder = new File(getPluginDirectory(), folderName);

                @Nullable ConfigurationSection pluginSection = getPluginsSection().getConfigurationSection(folderName);
                if (!getPluginsSection().isConfigurationSection(folderName)) {
                    pluginSection = getPluginsSection().createSection(folderName);
                    // fixme: May have to set it back into the parent configuration section, not sure
                }
                assert pluginSection != null;

                final @NotNull Repository tempRepo = getRepository(pluginSection, pluginFolder);

                if (pluginSection.getString("remote") == null) {
                    pluginSection.set("remote", tempRepo.getRemote());
                }
                if (pluginSection.getString("username") == null) {
                    pluginSection.set("username", tempRepo.getUsername());
                }
                if (pluginSection.getString("email") == null) {
                    pluginSection.set("email", tempRepo.getEmail());
                }
                if (pluginSection.getString("upstream") == null) {
                    pluginSection.set("upstream", tempRepo.getUpstream());
                }
                if (pluginSection.getString("branch") == null) {
                    pluginSection.set("branch", tempRepo.getBranch());
                }
            }

            // Repair configuration sections

            GitSync.this.saveConfig();
        }

        public @NotNull List<@NotNull Repository> getRepositories() { // TODO: This may as well be immutable because changes are not reflected. Fix.
            final @NotNull ArrayList<@NotNull Repository> workingRepositories = new ArrayList<>();
            for (final @NotNull String pluginSectionName : getPluginsSection().getKeys(false)) {
                final @NotNull ConfigurationSection pluginSection = getPluginsSection().getConfigurationSection(pluginSectionName);
                final @NotNull File dirOfSomePlugin = new File(GitSync.getInstance().getPluginDirectory(), pluginSectionName);

                if (dirOfSomePlugin.exists()) {
                    workingRepositories.add(getRepository(pluginSection, dirOfSomePlugin));
                }
            }
            return workingRepositories;
        }

        public Scenario getScenarioWhileDailySync() {
            return Scenario.valueOf(GitSync.this.getConfig().getString("dailySync"));
        }

        public boolean isQuiet() {
            return GitSync.this.getConfig().getBoolean("quiet");
        }

        public @Nullable Repository findRepoByName(String name) {
            return getRepositories().stream().filter(repo -> repo.getName().equals(name)).findFirst().orElse(null);
        }
    }
}
