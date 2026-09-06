package net.dripleafmc.core.tp;

import net.dripleafmc.core.config.Cfg;
import net.dripleafmc.core.config.Lang;
import net.dripleafmc.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Random teleport.
 *
 * Candidate chunks are loaded with getChunkAtAsync so a search never stalls the main
 * thread, and the search gives up after a bounded number of attempts instead of
 * looping until it finds something.
 */
public final class Rtp {

    public record Region(String id, String display, String world, int minRadius, int maxRadius, Material icon) {}

    private static final java.util.Set<Material> UNSAFE = java.util.EnumSet.of(
            Material.LAVA, Material.WATER, Material.FIRE, Material.CACTUS,
            Material.MAGMA_BLOCK, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.VOID_AIR);

    private final List<Region> regions = new ArrayList<>(4);
    private final Map<UUID, Long> cooldowns = new HashMap<>(32);
    private final Plugin plugin;
    private final Cfg cfg;
    private final Lang lang;
    private final Teleports teleports;

    public Rtp(Plugin plugin, Cfg cfg, Lang lang, Teleports teleports) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.lang = lang;
        this.teleports = teleports;
    }

    public void load(FileConfiguration config) {
        regions.clear();
        ConfigurationSection root = config.getConfigurationSection("rtp.regions");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            Material icon = Material.matchMaterial(sec.getString("icon", "GRASS_BLOCK"));
            regions.add(new Region(id,
                    sec.getString("display", id),
                    sec.getString("world", "world"),
                    sec.getInt("min-radius", 500),
                    sec.getInt("max-radius", 5000),
                    icon == null ? Material.GRASS_BLOCK : icon));
        }
    }

    public List<Region> regions() {
        return regions;
    }

    public long cooldownLeft(Player player) {
        Long until = cooldowns.get(player.getUniqueId());
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    public void go(Player player, Region region) {
        long left = cooldownLeft(player);
        if (left > 0 && !player.hasPermission("dripleaf.rtp.nocooldown")) {
            lang.send(player, "cooldown", Text.p("seconds", String.valueOf(left)));
            return;
        }
        World world = Bukkit.getWorld(region.world());
        if (world == null) {
            player.sendMessage(lang.get("teleport.cancelled"));
            return;
        }
        cooldowns.put(player.getUniqueId(),
                System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cfg.rtpCooldown));
        attempt(player, world, region, 0);
    }

    private void attempt(Player player, World world, Region region, int tries) {
        if (tries >= cfg.rtpAttempts) {
            lang.send(player, "teleport.cancelled");
            cooldowns.remove(player.getUniqueId());
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int radius = random.nextInt(region.minRadius(), Math.max(region.minRadius() + 1, region.maxRadius()));
        double angle = random.nextDouble(Math.PI * 2);
        int x = (int) (Math.cos(angle) * radius);
        int z = (int) (Math.sin(angle) * radius);

        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Block reads must happen on the main thread; the chunk is loaded by now.
                    if (!player.isOnline()) return;
                    Location safe = findSafe(world, x, z);
                    if (safe == null) {
                        attempt(player, world, region, tries + 1);
                        return;
                    }
                    teleports.warp(player, safe, cfg.rtpWarmup);
                }));
    }

    private Location findSafe(World world, int x, int z) {
        int top = world.getEnvironment() == World.Environment.NETHER ? 120 : world.getMaxHeight() - 1;
        int bottom = world.getMinHeight() + 1;
        for (int y = top; y > bottom; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isAir()) continue;
            if (UNSAFE.contains(block.getType())) return null;
            if (!block.getType().isSolid()) continue;
            Block above = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);
            if (!above.getType().isAir() || !head.getType().isAir()) return null;
            return new Location(world, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }
}
