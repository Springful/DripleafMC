package net.dripleaf.core.common.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads every yml the plugin ships, and keeps player edits when the jar's
 * defaults gain new keys.
 *
 * <p>On load a file is written out if missing, then compared against the copy
 * inside the jar: any key present in the default and absent on disk is added,
 * with the file rewritten once. Existing values are never touched, so a staff
 * member's edits survive an update and a new feature's config appears without
 * anyone having to diff a changelog.
 */
public final class ConfigManager {

    private final Plugin plugin;
    private final ValidationLog log = new ValidationLog();
    private final Map<String, YamlConfiguration> loaded = new HashMap<>(16);

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public ValidationLog log() {
        return log;
    }

    /**
     * @param resource path inside the jar and, identically, inside the data
     *                 folder — e.g. {@code core/warps.yml}
     */
    public YamlConfiguration load(String resource) {
        File file = new File(plugin.getDataFolder(), resource);
        if (!file.exists()) {
            plugin.saveResource(resource, false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (mergeDefaults(resource, config)) {
            try {
                config.save(file);
                plugin.getLogger().info("Added new default keys to " + resource + ".");
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not write merged defaults to " + resource, ex);
            }
        }
        loaded.put(resource, config);
        return config;
    }

    /** Re-reads a file already known to {@link #load}. */
    public YamlConfiguration reload(String resource) {
        return load(resource);
    }

    public YamlConfiguration cached(String resource) {
        YamlConfiguration config = loaded.get(resource);
        return config == null ? load(resource) : config;
    }

    /** A validated view of a whole file. */
    public Cfg view(String resource) {
        return new Cfg(cached(resource), resource, log);
    }

    public Cfg view(String resource, String path) {
        YamlConfiguration config = cached(resource);
        ConfigurationSection section = config.getConfigurationSection(path);
        return new Cfg(section == null ? config.createSection(path) : section, resource, log);
    }

    /**
     * Copies keys the jar default has and the on-disk file lacks.
     *
     * @return true when anything was added
     */
    private boolean mergeDefaults(String resource, YamlConfiguration target) {
        YamlConfiguration defaults = jarDefaults(resource);
        if (defaults == null) {
            return false;
        }
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            if (!target.contains(key)) {
                target.set(key, defaults.get(key));
                changed = true;
            }
        }
        return changed;
    }

    private YamlConfiguration jarDefaults(String resource) {
        try (InputStream stream = plugin.getResource(resource)) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not read bundled defaults for " + resource, ex);
            return null;
        }
    }

    /** Every file loaded so far, for the reload summary. */
    public List<String> tracked() {
        return new ArrayList<>(loaded.keySet());
    }
}
