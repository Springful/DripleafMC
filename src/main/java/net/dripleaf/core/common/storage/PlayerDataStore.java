package net.dripleaf.core.common.storage;

import net.dripleaf.core.common.scheduler.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Flat-file player storage: {@code data/players/<uuid>.yml}.
 *
 * <p>Loaded on join, held in memory, flushed on quit, on a staggered autosave,
 * and on disable. Every write goes through {@link AtomicYaml}. Every read and
 * write of the disk happens on the shared worker (§14) — the main thread never
 * blocks on I/O.
 *
 * <p>Staggering matters: without it every online player serialises on the same
 * tick every five minutes, which on a full server is a visible hitch.
 */
public final class PlayerDataStore implements Listener {

    private final Plugin plugin;
    private final Schedulers schedulers;
    private final Path directory;

    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    /** Name to UUID for offline lookups, populated from what we have seen. */
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();

    public PlayerDataStore(Plugin plugin, Schedulers schedulers) {
        this.plugin = plugin;
        this.schedulers = schedulers;
        this.directory = plugin.getDataFolder().toPath().resolve("data").resolve("players");
    }

    // ------------------------------------------------------------- lifecycle

    /** One autosave task for the whole server, sweeping a slice of players per pass. */
    public void startAutosave(int minutes) {
        long period = Math.max(1, minutes) * 60L;
        schedulers.repeatAsync(this::flushDirty, period, period, TimeUnit.SECONDS);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        sessionStart.put(uuid, System.currentTimeMillis());
        nameIndex.put(player.getName().toLowerCase(java.util.Locale.ROOT), uuid);

        // The player is already in-world; the profile arrives a tick or two later.
        // Every consumer treats a missing profile as defaults, so nothing races.
        schedulers.asyncThenSync(() -> read(uuid), data -> {
            long now = System.currentTimeMillis();
            if (data.firstSeen() == 0L) {
                data.firstSeen(now);
            }
            data.lastKnownName(player.getName());
            data.lastSeen(now);
            cache.put(uuid, data);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerData data = cache.remove(uuid);
        Long started = sessionStart.remove(uuid);
        if (data == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (started != null) {
            data.addPlaytime(now - started);
        }
        data.lastSeen(now);
        data.lastLocation(event.getPlayer().getLocation());
        schedulers.async(() -> persist(data));
    }

    /** Blocking, ordered flush. {@code onDisable} only. */
    public void flushAll() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : sessionStart.entrySet()) {
            PlayerData data = cache.get(entry.getKey());
            if (data != null) {
                data.addPlaytime(now - entry.getValue());
            }
        }
        sessionStart.clear();
        for (PlayerData data : cache.values()) {
            persist(data);
        }
    }

    private void flushDirty() {
        for (PlayerData data : cache.values()) {
            if (data.dirty()) {
                persist(data);
            }
        }
    }

    // ---------------------------------------------------------------- access

    /** Cached profile for an online player, creating an empty one if the load is still in flight. */
    public PlayerData get(Player player) {
        return get(player.getUniqueId());
    }

    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, PlayerData::new);
    }

    /** Non-creating peek — {@code null} when this player has no profile in memory. */
    public PlayerData peek(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * Loads an offline player's profile. Blocking, so call it from the shared
     * worker via {@link Schedulers#asyncThenSync}. An online player short-circuits
     * to the cached instance so an admin edit is not lost on their next save.
     */
    public PlayerData loadOffline(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        return cached != null ? cached : read(uuid);
    }

    /**
     * Persists a profile belonging to an offline player. A no-op for someone
     * online, whose in-memory copy is authoritative and flushes on quit.
     */
    public void saveOffline(PlayerData data) {
        if (Bukkit.getPlayer(data.uuid()) != null) {
            data.markDirty();
            return;
        }
        schedulers.async(() -> persist(data));
    }

    public java.util.Collection<PlayerData> online() {
        return cache.values();
    }

    public UUID lookupName(String name) {
        return nameIndex.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    /** Every stored uuid. Off-thread only — this touches the directory listing. */
    public List<UUID> allStoredIds() {
        List<UUID> out = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return out;
        }
        try (var stream = Files.list(directory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        try {
                            out.add(UUID.fromString(name.substring(0, name.length() - 4)));
                        } catch (IllegalArgumentException ignored) {
                            // Not one of ours; leave it alone.
                        }
                    });
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Could not list player data", ex);
        }
        return out;
    }

    // --------------------------------------------------------- serialisation

    private Path fileFor(UUID uuid) {
        return directory.resolve(uuid + ".yml");
    }

    PlayerData read(UUID uuid) {
        PlayerData data = new PlayerData(uuid);
        YamlConfiguration yaml = AtomicYaml.read(fileFor(uuid), plugin.getLogger());
        if (yaml.getKeys(false).isEmpty()) {
            data.clearDirty();
            return data;
        }

        data.lastKnownName(yaml.getString("name", ""));
        data.firstSeen(yaml.getLong("first-seen"));
        data.lastSeen(yaml.getLong("last-seen"));
        data.playtimeMillis(yaml.getLong("playtime-millis"));

        data.shards(decimal(yaml.getString("currencies.shards", "0")));
        data.souls(decimal(yaml.getString("currencies.souls", "0")));

        data.rebirthTier(yaml.getInt("rebirth.tier"));
        data.rebirthTotal(yaml.getInt("rebirth.total"));
        data.rebirthLast(yaml.getLong("rebirth.last"));
        data.rebirthFirst(yaml.getLong("rebirth.first"));
        data.rebirthSpent(decimal(yaml.getString("rebirth.spent", "0")));
        data.soulsEarned(decimal(yaml.getString("rebirth.souls-earned", "0")));
        data.lastPath(yaml.getString("rebirth.last-path", ""));

        ConfigurationSection dates = yaml.getConfigurationSection("rebirth.dates");
        if (dates != null) {
            for (String key : dates.getKeys(false)) {
                try {
                    data.tierDates().put(Integer.parseInt(key), dates.getLong(key));
                } catch (NumberFormatException ignored) {
                    // A hand-edited key that is not a tier number. Skip it.
                }
            }
        }

        ConfigurationSection homes = yaml.getConfigurationSection("homes");
        if (homes != null) {
            for (String key : homes.getKeys(false)) {
                Location location = readLocation(homes.getConfigurationSection(key));
                if (location != null) {
                    data.homes().put(key, location);
                }
            }
        }

        ConfigurationSection kits = yaml.getConfigurationSection("kit-uses");
        if (kits != null) {
            for (String key : kits.getKeys(false)) {
                data.kitUses().put(key, kits.getLong(key));
            }
        }

        data.lastLocation(readLocation(yaml.getConfigurationSection("last-location")));
        data.deathLocation(readLocation(yaml.getConfigurationSection("death-location")));

        String ui = yaml.getString("ui-mode", "");
        data.uiPreference(ui.isBlank() ? null : ui);

        data.nickname(yaml.getString("nickname", ""));
        data.flightSeconds(yaml.getLong("flight-seconds"));

        ConfigurationSection quickBuys = yaml.getConfigurationSection("quick-buys");
        if (quickBuys != null) {
            for (String shop : quickBuys.getKeys(false)) {
                ConfigurationSection slots = quickBuys.getConfigurationSection(shop);
                if (slots == null) {
                    continue;
                }
                for (String slot : slots.getKeys(false)) {
                    String value = slots.getString(slot, "");
                    if (!value.isBlank()) {
                        data.quickBuys().put(shop + '/' + slot, value);
                    }
                }
            }
        }
        for (String raw : yaml.getStringList("ignored")) {
            try {
                data.ignored().add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // Hand-edited nonsense; drop it rather than failing the load.
            }
        }

        data.clearDirty();
        return data;
    }

    private void persist(PlayerData data) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", data.lastKnownName());
        yaml.set("first-seen", data.firstSeen());
        yaml.set("last-seen", data.lastSeen());
        yaml.set("playtime-millis", data.playtimeMillis());

        yaml.set("currencies.shards", data.shards().toPlainString());
        yaml.set("currencies.souls", data.souls().toPlainString());

        yaml.set("rebirth.tier", data.rebirthTier());
        yaml.set("rebirth.total", data.rebirthTotal());
        yaml.set("rebirth.last", data.rebirthLast());
        yaml.set("rebirth.first", data.rebirthFirst());
        yaml.set("rebirth.spent", data.rebirthSpent().toPlainString());
        yaml.set("rebirth.souls-earned", data.soulsEarned().toPlainString());
        yaml.set("rebirth.last-path", data.lastPath());
        for (Map.Entry<Integer, Long> entry : data.tierDates().entrySet()) {
            yaml.set("rebirth.dates." + entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, Location> entry : data.homes().entrySet()) {
            writeLocation(yaml, "homes." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Long> entry : data.kitUses().entrySet()) {
            yaml.set("kit-uses." + entry.getKey(), entry.getValue());
        }
        writeLocation(yaml, "last-location", data.lastLocation());
        writeLocation(yaml, "death-location", data.deathLocation());

        yaml.set("ui-mode", data.uiPreference() == null ? "" : data.uiPreference());
        yaml.set("nickname", data.nickname());
        yaml.set("flight-seconds", data.flightSeconds());
        for (Map.Entry<String, String> entry : data.quickBuys().entrySet()) {
            // Stored nested so the file stays readable by hand.
            yaml.set("quick-buys." + entry.getKey().replace('/', '.'), entry.getValue());
        }
        yaml.set("ignored", data.ignored().stream().map(UUID::toString).toList());

        try {
            AtomicYaml.write(fileFor(data.uuid()), yaml);
            data.clearDirty();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not save player data for " + data.uuid(), ex);
        }
    }

    private static BigDecimal decimal(String raw) {
        try {
            return new BigDecimal(raw == null || raw.isBlank() ? "0" : raw);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    /** Serialises a location by world <em>name</em>, so an unloaded world round-trips. */
    public static void writeLocation(ConfigurationSection section, String path, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        section.set(path + ".world", location.getWorld().getName());
        section.set(path + ".x", location.getX());
        section.set(path + ".y", location.getY());
        section.set(path + ".z", location.getZ());
        section.set(path + ".yaw", location.getYaw());
        section.set(path + ".pitch", location.getPitch());
    }

    public static Location readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world", "");
        if (worldName.isBlank()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world,
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    /** Cached instances belong to online players only; nothing here outlives a session. */
    public Map<UUID, PlayerData> snapshot() {
        return new HashMap<>(cache);
    }
}
