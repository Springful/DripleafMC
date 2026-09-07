package net.dripleaf.core.core.teleport;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.sound.SoundService;
import net.dripleaf.core.common.text.Ctx;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Teleport requests, {@code /back} history and random teleport.
 *
 * <p>Requests live in memory only and expire on a timer. There is one sweep
 * task for the whole server rather than one per request — §14 again.
 */
public final class TeleportService implements Listener {

    /**
     * @param here true for {@code /tpahere}: the target travels to the requester
     */
    public record Request(UUID from, UUID to, boolean here, long expiresAt) {
    }

    private final Services services;
    /** Keyed by the player who must answer. */
    private final Map<UUID, Request> incoming = new ConcurrentHashMap<>();
    private final RandomTeleport randomTeleport;
    private long timeoutSeconds = 60L;

    public TeleportService(Services services) {
        this.services = services;
        this.randomTeleport = new RandomTeleport(services);
    }

    public void configure(net.dripleaf.core.common.config.Cfg teleport) {
        this.timeoutSeconds = (long) teleport.number(
                "request-timeout-seconds", 60d, 5d, 3600d);
        randomTeleport.configure(teleport);
    }

    /** One sweep for every pending request, every five seconds. */
    public void startExpiry() {
        services.schedulers().repeatSync(() -> {
            long now = System.currentTimeMillis();
            incoming.values().removeIf(request -> request.expiresAt() <= now);
        }, 100L, 100L);
    }

    public boolean request(Player from, Player to, boolean here) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            services.messages().send(from, "teleport.self");
            return false;
        }
        incoming.put(to.getUniqueId(), new Request(from.getUniqueId(), to.getUniqueId(), here,
                System.currentTimeMillis() + timeoutSeconds * 1000L));

        Ctx ctx = new Ctx().put("player", from.getName())
                .put("time", String.valueOf(timeoutSeconds));
        services.messages().send(from, here ? "teleport.sent-here" : "teleport.sent",
                Ctx.of("player", to.getName()));
        services.messages().send(to, here ? "teleport.received-here" : "teleport.received", ctx);
        return true;
    }

    public Request pending(Player player) {
        Request request = incoming.get(player.getUniqueId());
        if (request != null && request.expiresAt() <= System.currentTimeMillis()) {
            incoming.remove(player.getUniqueId());
            return null;
        }
        return request;
    }

    public boolean accept(Player player) {
        Request request = pending(player);
        if (request == null) {
            services.messages().send(player, "teleport.none-pending");
            return false;
        }
        incoming.remove(player.getUniqueId());
        Player requester = org.bukkit.Bukkit.getPlayer(request.from());
        if (requester == null) {
            services.messages().send(player, "teleport.requester-offline");
            return false;
        }

        Player traveller = request.here() ? player : requester;
        Player destination = request.here() ? requester : player;
        move(traveller, destination.getLocation(), "teleport.accepted");
        services.messages().send(requester, "teleport.accepted-by",
                Ctx.of("player", player.getName()));
        return true;
    }

    public boolean deny(Player player) {
        Request request = incoming.remove(player.getUniqueId());
        if (request == null) {
            services.messages().send(player, "teleport.none-pending");
            return false;
        }
        Player requester = org.bukkit.Bukkit.getPlayer(request.from());
        if (requester != null) {
            services.messages().send(requester, "teleport.denied-by",
                    Ctx.of("player", player.getName()));
        }
        services.messages().send(player, "teleport.denied");
        return true;
    }

    /** Cancels a request this player sent to someone else. */
    public boolean cancel(Player player) {
        boolean removed = incoming.values().removeIf(
                request -> request.from().equals(player.getUniqueId()));
        services.messages().send(player, removed ? "teleport.cancelled" : "teleport.none-sent");
        return removed;
    }

    /**
     * {@code /back}: the more recent of the last death location and the last
     * teleport origin. The death behaviour is gated separately by
     * {@code dripleaf.back.ondeath} so it can be a rank perk.
     */
    public Location backTarget(Player player) {
        var data = services.players().get(player);
        Location death = data.deathLocation();
        Location last = data.lastLocation();
        if (death != null && player.hasPermission("dripleaf.back.ondeath")) {
            return death;
        }
        return last;
    }

    public void move(Player player, Location destination, String messageKey) {
        services.players().get(player).lastLocation(player.getLocation());
        player.teleportAsync(destination).thenAccept(success -> {
            if (Boolean.TRUE.equals(success)) {
                services.sounds().play(player, SoundService.TELEPORT_COMPLETE);
                if (messageKey != null) {
                    services.messages().send(player, messageKey);
                }
            }
        });
    }

    /**
     * Random teleport.
     *
     * <p>Delegated to {@link RandomTeleport}, which only lands players in
     * already-generated chunks, checks the spot is actually survivable, keeps
     * out of GriefPrevention claims and WorldGuard regions, and preloads the
     * destination before the player arrives.
     */
    public void randomTeleport(Player player, java.util.function.Consumer<Boolean> whenDone) {
        randomTeleport.teleport(player, whenDone);
    }

    public RandomTeleport random() {
        return randomTeleport;
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        services.players().get(event.getEntity()).deathLocation(event.getEntity().getLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        incoming.remove(uuid);
        incoming.values().removeIf(request -> request.from().equals(uuid));
    }

    public int pendingCount() {
        return incoming.size();
    }
}
