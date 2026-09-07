package net.dripleaf.core.core.teleport;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.config.Cfg;
import net.dripleaf.core.common.text.Ctx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Random teleport that will not strand, bury, drown or trespass on anyone.
 *
 * <p>Four rules, in the order they are cheapest to check:
 *
 * <ol>
 *   <li><b>Generated chunks only.</b> A candidate whose chunk has never been
 *       generated is rejected outright rather than generated on demand.
 *       Generating terrain on a player's command is the single most expensive
 *       thing an RTP can do — it is what turns {@code /rtp} into a lag spike
 *       and what quietly grows the world folder forever.</li>
 *   <li><b>Safe landing.</b> Solid ground that is not lava, fire, cactus,
 *       magma or powder snow; two passable blocks above it; inside the world's
 *       height limits; not underwater unless allowed.</li>
 *   <li><b>Nobody's land.</b> Rejected if GriefPrevention has a claim there, or
 *       if WorldGuard would not let this player build there.</li>
 *   <li><b>Preloaded before arrival.</b> The destination and its neighbours are
 *       loaded and held with plugin chunk tickets before the teleport happens,
 *       and released a few seconds afterwards, so the player lands in a world
 *       that is already there instead of in a grey void.</li>
 * </ol>
 *
 * <p>The search is asynchronous and bounded. Each candidate costs one async
 * chunk load and a short main-thread inspection; the attempt budget stops a
 * pathological world from spinning forever.
 */
public final class RandomTeleport {

    /** Ground a player must never be dropped onto. */
    private static final Set<Material> UNSAFE_GROUND = EnumSet.of(
            Material.LAVA, Material.MAGMA_BLOCK, Material.CACTUS, Material.FIRE,
            Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE,
            Material.POINTED_DRIPSTONE);

    private final Services services;

    private int radius = 5000;
    private int minRadius = 200;
    private int attempts = 24;
    private boolean allowWater;
    private boolean respectClaims = true;
    private boolean respectWorldGuard = true;
    private int preloadRadius = 1;
    private int ticketSeconds = 10;

    public RandomTeleport(Services services) {
        this.services = services;
    }

    public void configure(Cfg cfg) {
        this.radius = cfg.integer("rtp-radius", 5000, 100, 10_000_000);
        this.minRadius = cfg.integer("rtp-min-radius", 200, 0, 10_000_000);
        this.attempts = cfg.integer("rtp-attempts", 24, 1, 200);
        this.allowWater = cfg.bool("rtp-allow-water", false);
        this.respectClaims = cfg.bool("rtp-respect-claims", true);
        this.respectWorldGuard = cfg.bool("rtp-respect-worldguard", true);
        this.preloadRadius = cfg.integer("rtp-preload-radius", 1, 0, 4);
        this.ticketSeconds = cfg.integer("rtp-ticket-seconds", 10, 1, 120);
    }

    /**
     * Finds somewhere and teleports {@code player} there.
     *
     * @param whenDone true on success; false when the attempt budget ran out
     */
    public void teleport(Player player, Consumer<Boolean> whenDone) {
        search(player, player.getWorld(), 0, whenDone);
    }

    private void search(Player player, World world, int attempt, Consumer<Boolean> whenDone) {
        if (attempt >= attempts) {
            whenDone.accept(false);
            return;
        }
        if (!player.isOnline()) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        // Sample the ring between minRadius and radius so /rtp never lands
        // someone back on top of spawn.
        double angle = random.nextDouble() * Math.PI * 2d;
        double distance = minRadius + random.nextDouble() * Math.max(1, radius - minRadius);
        int x = (int) Math.round(Math.cos(angle) * distance);
        int z = (int) Math.round(Math.sin(angle) * distance);

        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        // Rule 1, and the cheapest check there is: never generate new terrain.
        if (!world.isChunkGenerated(chunkX, chunkZ)) {
            search(player, world, attempt + 1, whenDone);
            return;
        }

        world.getChunkAtAsync(chunkX, chunkZ, false).thenAccept(chunk -> {
            if (chunk == null) {
                search(player, world, attempt + 1, whenDone);
                return;
            }
            Location candidate = findSurface(world, x, z);
            if (candidate == null || !isSafe(player, candidate)) {
                search(player, world, attempt + 1, whenDone);
                return;
            }
            preloadAndGo(player, candidate, whenDone);
        }).exceptionally(error -> {
            search(player, world, attempt + 1, whenDone);
            return null;
        });
    }

    // ------------------------------------------------------------- landing

    /**
     * The standing position at {@code (x, z)}, or {@code null} when there isn't
     * one.
     *
     * <p>{@code getHighestBlockYAt} is useless in the Nether — it finds the
     * bedrock roof — so that dimension gets a downward scan for a real air
     * pocket instead.
     */
    private Location findSurface(World world, int x, int z) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            for (int y = 100; y > world.getMinHeight() + 1; y--) {
                Block ground = world.getBlockAt(x, y, z);
                if (ground.getType().isSolid()
                        && world.getBlockAt(x, y + 1, z).isPassable()
                        && world.getBlockAt(x, y + 2, z).isPassable()) {
                    return new Location(world, x + 0.5, y + 1, z + 0.5);
                }
            }
            return null;
        }

        int y = world.getHighestBlockYAt(x, z);
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 2) {
            return null;
        }
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    /** Rules 2 and 3. Main thread only — it reads blocks and asks the hooks. */
    private boolean isSafe(Player player, Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        Block ground = location.clone().subtract(0, 1, 0).getBlock();
        Block feet = location.getBlock();
        Block head = location.clone().add(0, 1, 0).getBlock();

        if (!ground.getType().isSolid() || UNSAFE_GROUND.contains(ground.getType())) {
            return false;
        }
        if (!feet.isPassable() || !head.isPassable()) {
            return false;
        }
        if (!allowWater
                && (feet.getType() == Material.WATER || ground.getType() == Material.WATER)) {
            return false;
        }
        if (feet.getType() == Material.LAVA || head.getType() == Material.LAVA) {
            return false;
        }

        if (respectClaims && services.hooks().claims().claimedAt(location)) {
            return false;
        }
        return !respectWorldGuard
                || services.hooks().worldGuard().canBuild(player, location);
    }

    // ------------------------------------------------------------ preload

    /**
     * Rule 4: hold the destination and its neighbours in memory, wait for them
     * to finish loading, then teleport — and release the tickets shortly after.
     */
    private void preloadAndGo(Player player, Location destination, Consumer<Boolean> whenDone) {
        World world = destination.getWorld();
        if (world == null) {
            whenDone.accept(false);
            return;
        }
        int centreX = destination.getBlockX() >> 4;
        int centreZ = destination.getBlockZ() >> 4;

        List<int[]> held = new java.util.ArrayList<>();
        List<java.util.concurrent.CompletableFuture<org.bukkit.Chunk>> loading =
                new java.util.ArrayList<>();

        for (int dx = -preloadRadius; dx <= preloadRadius; dx++) {
            for (int dz = -preloadRadius; dz <= preloadRadius; dz++) {
                int cx = centreX + dx;
                int cz = centreZ + dz;
                if (!world.isChunkGenerated(cx, cz)) {
                    continue;
                }
                world.addPluginChunkTicket(cx, cz, services.plugin());
                held.add(new int[] { cx, cz });
                loading.add(world.getChunkAtAsync(cx, cz, false));
            }
        }

        java.util.concurrent.CompletableFuture
                .allOf(loading.toArray(new java.util.concurrent.CompletableFuture[0]))
                .whenComplete((ignored, error) -> services.schedulers().sync(() -> {
                    if (!player.isOnline()) {
                        release(world, held);
                        return;
                    }
                    services.players().get(player).lastLocation(player.getLocation());
                    player.teleportAsync(destination).thenAccept(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            services.sounds().play(player,
                                    net.dripleaf.core.common.sound.SoundService
                                            .TELEPORT_COMPLETE);
                            services.messages().send(player, "teleport.rtp-success", new Ctx()
                                    .put("x", destination.getBlockX())
                                    .put("y", destination.getBlockY())
                                    .put("z", destination.getBlockZ()));
                        }
                        // Let the client settle before dropping the tickets;
                        // releasing immediately can unload the ground underneath.
                        services.schedulers().delaySync(() -> release(world, held),
                                ticketSeconds * 20L);
                        whenDone.accept(Boolean.TRUE.equals(success));
                    });
                }));
    }

    private void release(World world, List<int[]> held) {
        for (int[] chunk : held) {
            world.removePluginChunkTicket(chunk[0], chunk[1], services.plugin());
        }
    }
}
