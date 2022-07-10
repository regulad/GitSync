package xyz.regulad.bukkittemplate;

import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * A template plugin to be used in Minecraft plugins.
 */
public class BukkitTemplate extends JavaPlugin {
    @Getter
    private static @Nullable BukkitTemplate instance;

    @Override
    public void onEnable() {
        // Setup instance access
        instance = this;
        // Setup bStats metrics
        new Metrics(this, 13761); // TODO: Replace this in your plugin!
    }

    @Override
    public void onDisable() {
        instance = null;
    }
}
