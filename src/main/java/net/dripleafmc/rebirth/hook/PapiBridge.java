package net.dripleafmc.rebirth.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI access with a very short TTL cache.
 * <p>
 * Opening a menu can touch the same placeholder in a dozen lore lines; without
 * this, each of those is a full expansion lookup and string parse.
 */
public final class PapiBridge {

    private record Entry(double value, long expiresAt) {
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final long ttlMillis;

    public PapiBridge(long ttlMillis) {
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        this.ttlMillis = Math.max(0L, ttlMillis);
    }

    public boolean enabled() {
        return enabled;
    }

    public String text(Player player, String raw) {
        if (!enabled || raw == null || raw.indexOf('%') < 0) {
            return raw == null ? "" : raw;
        }
        return PlaceholderAPI.setPlaceholders(player, raw);
    }

    public double number(Player player, String placeholder) {
        if (!enabled) {
            return 0d;
        }
        UUID id = player.getUniqueId();
        String key = id + "|" + placeholder;
        long now = System.currentTimeMillis();

        Entry cached = cache.get(key);
        if (cached != null && cached.expiresAt() > now) {
            return cached.value();
        }

        double parsed = parse(PlaceholderAPI.setPlaceholders(player, placeholder));
        if (ttlMillis > 0L) {
            cache.put(key, new Entry(parsed, now + ttlMillis));
        }
        return parsed;
    }

    private static double parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0d;
        }
        StringBuilder digits = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isDigit(c) || c == '.' || (c == '-' && digits.isEmpty())) {
                digits.append(c);
            }
        }
        try {
            return digits.isEmpty() ? 0d : Double.parseDouble(digits.toString());
        } catch (NumberFormatException ex) {
            return 0d;
        }
    }

    public void invalidate(UUID id) {
        cache.keySet().removeIf(key -> key.startsWith(id.toString()));
    }

    public void clear() {
        cache.clear();
    }
}
