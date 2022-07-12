package gitsync;

import gitsync.utils.GitProcessUtil;
import lombok.Data;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

@Data
public class Repository {
    private String name;
    private File directory;
    private ConfigurationSection config;
    // info
    private boolean enabled = false;
    private String remote = "";
    // creds
    private String username = "GitSync";
    private String email = "gitsync@regulad.xyz";
    private String upstream = "origin";
    private String branch = "master";
    // plugin
    private @Nullable Plugin plugin = null;

    public boolean isRepoExists() {
        return GitProcessUtil.repoExists(this.directory);
    }
}
