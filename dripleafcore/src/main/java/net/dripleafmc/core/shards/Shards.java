package net.dripleafmc.core.shards;

import net.dripleafmc.core.config.Cfg;
import net.dripleafmc.core.config.Lang;
import net.dripleafmc.core.profile.Profile;
import net.dripleafmc.core.profile.Profiles;
import net.dripleafmc.core.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Shard earning.
 *
 * Activity is sampled inside the earn task itself rather than by listening to
 * PlayerMoveEvent — move fires several times per player per tick, and all we need to
 * know is whether someone changed position since the last payout. One comparison
 * every two minutes replaces tens of thousands of event calls.
 */
public final class Shards {

    private final Map<UUID, Location> lastSample = new HashMap<>(32);
    private final Map<UUID, Long> lastActivity = new HashMap<>(32);
    private final Map<Long, Long> killCooldown = new HashMap<>(64);

    private final Plugin plugin;
    private final Cfg cfg;
    private final Lang lang;
    private final Profiles profiles;
    private BukkitTask task;

    public Shards(Plugin plugin, Cfg cfg, Lang lang, Profiles profiles) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.lang = lang;
        this.profiles = profiles;
    }

    public void start() {
        if (!cfg.shardsEnabled) return;
        long ticks = cfg.shardTickSeconds * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, ticks, ticks);
    }

    public void stop() {
        if (task != null) task.cancel();
        lastSample.clear();
        lastActivity.clear();
        killCooldown.clear();
    }

    /** Called from block break/place and damage handlers — cheap map write, no allocation. */
    public void markActive(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void forget(UUID uuid) {
        lastSample.remove(uuid);
        lastActivity.remove(uuid);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long window = TimeUnit.SECONDS.toMillis(cfg.shardActiveWindow);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Location current = player.getLocation();

            if (cfg.shardWorldBlacklist.contains(current.getWorld().getName())) {
                lastSample.put(uuid, current);
                continue;
            }

            Location previous = lastSample.put(uuid, current);
            boolean moved = previous == null
                    || previous.getWorld() != current.getWorld()
                    || previous.distanceSquared(current) > 4.0;
            Long recent = lastActivity.get(uuid);
            boolean active = moved || (recent != null && now - recent < window);
            if (!active) continue;

            Profile profile = profiles.get(uuid);
            if (profile == null) continue;

            long amount = (long) cfg.shardsPerTick * (profile.boosterActive() ? cfg.shardBooster : 1);
            if (amount <= 0) continue;
            profile.addShards(amount);
            player.sendActionBar(lang.get("shards.received",
                    Text.p("amount", String.valueOf(amount)),
                    Text.p("plural", amount == 1 ? "" : "s")));
        }
    }

    /** Kill reward with a per killer-victim pair cooldown, which is what stops alt farming. */
    public void onKill(Player killer, Player victim) {
        if (!cfg.shardsEnabled || cfg.shardsPerKill <= 0) return;
        long pair = ((long) killer.getUniqueId().hashCode() << 32) ^ (victim.getUniqueId().hashCode() & 0xffffffffL);
        long now = System.currentTimeMillis();
        Long until = killCooldown.get(pair);
        if (until != null && until > now) return;
        killCooldown.put(pair, now + TimeUnit.SECONDS.toMillis(cfg.shardKillCooldown));

        Profile profile = profiles.get(killer.getUniqueId());
        if (profile == null) return;
        long amount = (long) cfg.shardsPerKill * (profile.boosterActive() ? cfg.shardBooster : 1);
        profile.addShards(amount);
        killer.sendActionBar(lang.get("shards.received",
                Text.p("amount", String.valueOf(amount)),
                Text.p("plural", amount == 1 ? "" : "s")));

        if (killCooldown.size() > 4096) killCooldown.entrySet().removeIf(e -> e.getValue() < now);
    }

    public void grantBooster(Profile profile, int hours) {
        long base = Math.max(profile.boosterUntil, System.currentTimeMillis());
        profile.boosterUntil = base + TimeUnit.HOURS.toMillis(hours);
        profile.touch();
    }

    public boolean take(Profile profile, long amount) {
        if (profile.shards < amount) return false;
        profile.addShards(-amount);
        return true;
    }
}
