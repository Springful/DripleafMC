package net.dripleaf.core.common.cooldown;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player, per-command cooldowns.
 *
 * <p>Held in memory only, deliberately: a cooldown that survives a restart is a
 * cooldown that punishes players for the server's problems. Kit cooldowns are
 * the exception and live in player data instead.
 *
 * <p>A cooldown starts when a command <em>succeeds</em> — never when it is
 * attempted and fails, and never when a warmup is cancelled. Getting that wrong
 * is the single most common complaint about this kind of plugin.
 *
 * <p>One map, entries evicted on quit. No task sweeps it: an expired entry costs
 * one timestamp comparison on the next read, which is cheaper than scanning.
 */
public final class CooldownService {

    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    /** @return remaining milliseconds, or 0 when ready */
    public long remaining(Player player, String key) {
        Map<String, Long> perPlayer = cooldowns.get(player.getUniqueId());
        if (perPlayer == null) {
            return 0L;
        }
        Long until = perPlayer.get(key);
        if (until == null) {
            return 0L;
        }
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0L) {
            perPlayer.remove(key);
            return 0L;
        }
        return remaining;
    }

    public boolean ready(Player player, String key) {
        return remaining(player, key) <= 0L;
    }

    /** Call this on success, and only on success. */
    public void start(Player player, String key, long seconds) {
        if (seconds <= 0L) {
            return;
        }
        cooldowns.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>(8))
                .put(key, System.currentTimeMillis() + seconds * 1000L);
    }

    public void clear(Player player, String key) {
        Map<String, Long> perPlayer = cooldowns.get(player.getUniqueId());
        if (perPlayer != null) {
            perPlayer.remove(key);
        }
    }

    public void forget(UUID uuid) {
        cooldowns.remove(uuid);
    }

    public Map<String, Long> snapshot(Player player) {
        Map<String, Long> perPlayer = cooldowns.get(player.getUniqueId());
        return perPlayer == null ? Map.of() : new HashMap<>(perPlayer);
    }
}
