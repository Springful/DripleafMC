package net.dripleaf.core.common.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI, with a per-player result cache.
 *
 * <p>PAPI resolution is not free and the requirement screens ask for the same
 * value several times per render. Results are cached for a short window
 * (default one tick's worth) and dropped when a player quits, so the map cannot
 * grow without bound.
 */
public final class PapiBridge implements Bridge {

    private record Entry(String value, long expiry) {
    }

    private final Map<UUID, Map<String, Entry>> cache = new ConcurrentHashMap<>();
    private boolean present;
    private long ttlMillis = 250L;

    public void connect(long ttlMillis) {
        this.present = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        this.ttlMillis = Math.max(0L, ttlMillis);
        cache.clear();
    }

    @Override
    public String name() {
        return "PlaceholderAPI";
    }

    @Override
    public boolean available() {
        return present;
    }

    /** Resolves {@code raw}, returning it unchanged when PAPI is absent. */
    public String set(Player player, String raw) {
        if (!present || raw == null || raw.indexOf('%') < 0) {
            return raw == null ? "" : raw;
        }
        return PlaceholderAPI.setPlaceholders(player, raw);
    }

    /** Cached single-placeholder resolution, e.g. {@code %mcmmo_power_level%}. */
    public String resolve(Player player, String placeholder) {
        if (!present || placeholder == null || placeholder.isBlank()) {
            return "";
        }
        if (ttlMillis <= 0L) {
            return PlaceholderAPI.setPlaceholders(player, placeholder);
        }
        long now = System.currentTimeMillis();
        Map<String, Entry> perPlayer =
                cache.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>(8));
        Entry entry = perPlayer.get(placeholder);
        if (entry != null && entry.expiry() > now) {
            return entry.value();
        }
        String value = PlaceholderAPI.setPlaceholders(player, placeholder);
        perPlayer.put(placeholder, new Entry(value, now + ttlMillis));
        return value;
    }

    /** Parses a placeholder result as a number, returning {@code fallback} on anything else. */
    public double resolveNumber(Player player, String placeholder, double fallback) {
        String raw = resolve(player, placeholder);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.replace(",", "").replace("%", "").trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** Bounded: every cache in the plugin is evicted on quit. */
    public void forget(UUID uuid) {
        cache.remove(uuid);
    }

    public void clear() {
        cache.clear();
    }
}
