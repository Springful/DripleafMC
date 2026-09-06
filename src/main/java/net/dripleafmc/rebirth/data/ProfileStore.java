package net.dripleafmc.rebirth.data;

import net.dripleafmc.rebirth.RebirthPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flat-file storage for rebirth tiers.
 * <p>
 * Reads are served from memory. Writes mark the profile dirty and are flushed
 * asynchronously on a timer and on shutdown, so a rebirth never blocks the
 * main thread on disk I/O.
 * <p>
 * The tier lives here rather than being derived from LuckPerms groups. The old
 * Skript scanned up to 25 permission nodes on every menu open; this is a single
 * map lookup, and the LuckPerms group is still granted as a reward command so
 * every existing permission-based perk keeps working.
 */
public final class ProfileStore implements Listener {

    private final RebirthPlugin plugin;
    private final File file;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    private volatile boolean saving;

    public ProfileStore(RebirthPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String raw : root.getKeys(false)) {
            ConfigurationSection node = root.getConfigurationSection(raw);
            if (node == null) {
                continue;
            }
            try {
                profiles.put(UUID.fromString(raw), new Profile(
                        node.getInt("tier", 0),
                        node.getString("path"),
                        node.getLong("last-rebirth", 0L),
                        node.getInt("total", 0)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("players.yml: skipping malformed uuid '" + raw + "'");
            }
        }
        plugin.getLogger().info("Loaded " + profiles.size() + " rebirth profiles.");
    }

    public Profile get(UUID id) {
        return profiles.getOrDefault(id, Profile.EMPTY);
    }

    public int tier(UUID id) {
        return get(id).tier();
    }

    public void set(UUID id, Profile profile) {
        profiles.put(id, profile);
        dirty.add(id);
    }

    public Map<UUID, Profile> all() {
        return Map.copyOf(profiles);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.papi().invalidate(event.getPlayer().getUniqueId());
    }

    public void startAutoSave(int minutes) {
        if (minutes <= 0) {
            return;
        }
        long ticks = minutes * 60L * 20L;
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::flush, ticks, ticks);
    }

    /** Async-safe. Writes only if something changed. */
    public void flush() {
        if (dirty.isEmpty() || saving) {
            return;
        }
        saving = true;
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, Profile> entry : profiles.entrySet()) {
                String base = "players." + entry.getKey();
                Profile profile = entry.getValue();
                yaml.set(base + ".tier", profile.tier());
                yaml.set(base + ".path", profile.path());
                yaml.set(base + ".last-rebirth", profile.lastRebirth());
                yaml.set(base + ".total", profile.total());
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create the plugin data folder.");
            }
            yaml.save(file);
            dirty.clear();
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save players.yml: " + ex.getMessage());
        } finally {
            saving = false;
        }
    }
}
