package net.dripleaf.core.core.homes;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.sound.SoundService;
import net.dripleaf.core.common.storage.PlayerData;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.Glyphs;
import net.dripleaf.core.common.text.Palette;
import net.dripleaf.core.common.ui.ButtonStyle;
import net.dripleaf.core.common.ui.ChestLayout;
import net.dripleaf.core.common.ui.Screen;
import net.dripleaf.core.common.ui.ScreenButton;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Player homes.
 *
 * <p>Limits are permission-driven — {@code dripleaf.home.limit.<n>}, highest
 * matching node wins, with a config default for players holding none. That is
 * what lets a rebirth tier grant home slots without the rebirth module knowing
 * anything about homes: it grants a permission node and this reads it.
 */
public final class HomeService {

    private final Services services;
    private int defaultLimit = 1;

    public HomeService(Services services) {
        this.services = services;
    }

    public void configure(int defaultLimit) {
        this.defaultLimit = Math.max(0, defaultLimit);
    }

    /** Highest {@code dripleaf.home.limit.<n>} the player holds, else the config default. */
    public int limit(Player player) {
        if (player.hasPermission("dripleaf.home.limit.*")) {
            return Integer.MAX_VALUE;
        }
        int best = defaultLimit;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String node = info.getPermission();
            if (!node.startsWith("dripleaf.home.limit.")) {
                continue;
            }
            try {
                best = Math.max(best,
                        Integer.parseInt(node.substring("dripleaf.home.limit.".length())));
            } catch (NumberFormatException ignored) {
                // A malformed node is somebody else's problem; ignore it quietly.
            }
        }
        return best;
    }

    public int used(Player player) {
        return services.players().get(player).homes().size();
    }

    public List<String> names(Player player) {
        return new ArrayList<>(services.players().get(player).homes().keySet());
    }

    public Location home(Player player, String name) {
        return services.players().get(player).homes().get(name.toLowerCase(Locale.ROOT));
    }

    /** @return a {@code messages.yml} key describing the outcome */
    public String set(Player player, String rawName) {
        String name = rawName.toLowerCase(Locale.ROOT).trim();
        if (name.isEmpty() || !name.matches("[a-z0-9_-]{1,24}")) {
            return "homes.bad-name";
        }
        PlayerData data = services.players().get(player);
        boolean replacing = data.homes().containsKey(name);
        if (!replacing && data.homes().size() >= limit(player)) {
            return "homes.limit-reached";
        }
        data.homes().put(name, player.getLocation());
        data.markDirty();
        return replacing ? "homes.replaced" : "homes.set";
    }

    public boolean delete(Player player, String rawName) {
        PlayerData data = services.players().get(player);
        boolean removed = data.homes().remove(rawName.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            data.markDirty();
        }
        return removed;
    }

    public void teleport(Player player, String name) {
        Location location = home(player, name);
        if (location == null) {
            services.messages().send(player, "homes.unknown", Ctx.of("name", name));
            return;
        }
        services.ui().close(player);
        services.players().get(player).lastLocation(player.getLocation());
        player.teleportAsync(location).thenAccept(success -> {
            if (Boolean.TRUE.equals(success)) {
                services.sounds().play(player, SoundService.TELEPORT_COMPLETE);
                services.messages().send(player, "homes.teleported", Ctx.of("name", name));
            }
        });
    }

    // ------------------------------------------------------------------ menu

    /** The picker shown by {@code /home} with no argument and by {@code /homes}. */
    public void openPicker(Player player) {
        PlayerData data = services.players().get(player);
        int limit = limit(player);

        Screen.Builder builder = Screen.of("homes",
                        Palette.brand(services.messages().raw("homes.menu-title")))
                .line(applied("homes.menu-subtitle", new Ctx()
                        .put("used", String.valueOf(data.homes().size()))
                        .put("max", limit == Integer.MAX_VALUE ? "∞" : String.valueOf(limit))))
                .blank()
                .layout(ChestLayout.chest(6));

        if (data.homes().isEmpty()) {
            builder.line(services.messages().raw("homes.none"));
        }

        for (var entry : data.homes().entrySet()) {
            Location location = entry.getValue();
            String world = location.getWorld() == null
                    ? "?" : location.getWorld().getName();
            builder.button(ScreenButton.of("home-" + entry.getKey(),
                            Palette.brand(entry.getKey()))
                    .material(worldIcon(location))
                    .style(ButtonStyle.PRIMARY)
                    .line(Palette.colour(Palette.MUTED, Glyphs.ITEM + " " + world))
                    .line(Palette.colour(Palette.MUTED, String.format(
                            Glyphs.ITEM + " %.0f, %.0f, %.0f",
                            location.getX(), location.getY(), location.getZ())))
                    .line(Palette.colour(Palette.STRUCTURE, Glyphs.ITEM + " "
                            + location.getBlock().getBiome().getKey().getKey()
                            .replace('_', ' ')))
                    .action(clicker -> teleport(clicker, entry.getKey()))
                    .build());
        }

        services.sounds().play(player, SoundService.MENU_OPEN);
        services.ui().open(player, builder.build());
    }

    private static Material worldIcon(Location location) {
        if (location.getWorld() == null) {
            return Material.PAPER;
        }
        return switch (location.getWorld().getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
    }

    private String applied(String key, Ctx ctx) {
        return ctx.applyRaw(services.messages().raw(key));
    }
}
