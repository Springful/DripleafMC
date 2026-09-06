package net.dripleafmc.rebirth.config;

import net.dripleafmc.rebirth.RebirthPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/** Loads a bundled resource from the plugin folder, writing the default on first run. */
public final class ConfigFile {

    private ConfigFile() {
    }

    public static YamlConfiguration load(RebirthPlugin plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }
}
