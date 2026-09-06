package net.dripleafmc.core.tp;

import net.dripleafmc.core.combat.Combat;
import net.dripleafmc.core.config.Cfg;
import net.dripleafmc.core.config.Lang;
import net.dripleafmc.core.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Warmup teleports.
 *
 * The movement check runs inside the warmup task itself (one task per pending
 * teleport, alive for a few seconds) rather than through a global PlayerMoveEvent
 * listener, so idle servers pay nothing for this feature.
 */
public final class Teleports {

    private final Map<UUID, BukkitTask> pending = new HashMap<>(8);
    private final Plugin plugin;
    private final Cfg cfg;
    private final Lang lang;
    private final Combat combat;

    public Teleports(Plugin plugin, Cfg cfg, Lang lang, Combat combat) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.lang = lang;
        this.combat = combat;
    }

    public void cancel(UUID uuid) {
        BukkitTask task = pending.remove(uuid);
        if (task != null) task.cancel();
    }

    public boolean busy(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    public void warp(Player player, Location destination, int seconds) {
        if (destination == null) {
            lang.send(player, "teleport.cancelled");
            return;
        }
        if (combat.deny(player)) return;
        cancel(player.getUniqueId());

        if (seconds <= 0 || player.hasPermission("dripleaf.teleport.instant")) {
            finish(player, destination);
            return;
        }

        final Location origin = player.getLocation().clone();
        final int[] remaining = {seconds};
        lang.send(player, "teleport.warmup", Text.p("seconds", String.valueOf(seconds)));

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel(player.getUniqueId());
                    return;
                }
                if (cfg.cancelOnMove && moved(origin, player.getLocation())) {
                    cancel(player.getUniqueId());
                    lang.send(player, "teleport.cancelled");
                    return;
                }
                if (--remaining[0] <= 0) {
                    cancel(player.getUniqueId());
                    finish(player, destination);
                    return;
                }
                player.sendActionBar(lang.get("teleport.warmup",
                        Text.p("seconds", String.valueOf(remaining[0]))));
            }
        }, 20L, 20L);

        pending.put(player.getUniqueId(), task);
    }

    private static boolean moved(Location from, Location to) {
        return from.getWorld() != to.getWorld() || from.distanceSquared(to) > 1.0;
    }

    private void finish(Player player, Location destination) {
        player.teleportAsync(destination).thenAccept(success -> {
            if (Boolean.TRUE.equals(success)) lang.send(player, "teleport.done");
        });
    }
}
