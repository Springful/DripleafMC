package net.dripleaf.core.core.teleport;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.sound.SoundService;
import net.dripleaf.core.common.text.Ctx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

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
    private long timeoutSeconds = 60L;
    private int rtpRadius = 5000;
    private int rtpAttempts = 24;

    public TeleportService(Services services) {
        this.services = services;
    }

    public void configure(long timeoutSeconds, int rtpRadius, int rtpAttempts) {
        this.timeoutSeconds = Math.max(5L, timeoutSeconds);
        this.rtpRadius = Math.max(100, rtpRadius);
        this.rtpAttempts = Math.max(1, rtpAttempts);
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
     * Random teleport. Candidate columns are picked at random and checked with
     * {@code getHighestBlockYAt}, which is chunk-loading, so the search runs
     * through Paper's async chunk API rather than blocking the main thread.
     */
    public void randomTeleport(Player player, java.util.function.Consumer<Boolean> whenDone) {
        World world = player.getWorld();
        attemptRtp(player, world, 0, whenDone);
    }

    private void attemptRtp(Player player, World world, int attempt,
                            java.util.function.Consumer<Boolean> whenDone) {
        if (attempt >= rtpAttempts) {
            whenDone.accept(false);
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = random.nextInt(-rtpRadius, rtpRadius + 1);
        int z = random.nextInt(-rtpRadius, rtpRadius + 1);

        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            int y = world.getHighestBlockYAt(x, z);
            Location candidate = new Location(world, x + 0.5, y + 1, z + 0.5);
            Material ground = world.getBlockAt(x, y, z).getType();
            if (!safe(ground)) {
                attemptRtp(player, world, attempt + 1, whenDone);
                return;
            }
            move(player, candidate, "teleport.rtp-success");
            whenDone.accept(true);
        });
    }

    private static boolean safe(Material ground) {
        return ground.isSolid()
                && ground != Material.LAVA
                && ground != Material.MAGMA_BLOCK
                && ground != Material.CACTUS
                && ground != Material.POWDER_SNOW;
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
