package net.dripleaf.core.core.warps;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.config.Cfg;
import net.dripleaf.core.common.icon.IconService;
import net.dripleaf.core.common.money.ParseResult;
import net.dripleaf.core.common.sound.SoundService;
import net.dripleaf.core.common.storage.AtomicYaml;
import net.dripleaf.core.common.storage.PlayerDataStore;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.Glyphs;
import net.dripleaf.core.common.text.Palette;
import net.dripleaf.core.common.ui.ButtonStyle;
import net.dripleaf.core.common.ui.ChestLayout;
import net.dripleaf.core.common.ui.Screen;
import net.dripleaf.core.common.ui.ScreenButton;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Server warps, their menu, and the teleport itself.
 *
 * <p>Warps a player cannot use are shown greyed with the reason — "Requires
 * Rebirth V" — rather than hidden. A visible goal drives progression; an empty
 * grid is just an empty grid.
 */
public final class WarpService {

    private static final String RESOURCE = "core/warps.yml";

    private final Services services;
    private final Map<String, Warp> warps = new LinkedHashMap<>(32);

    public WarpService(Services services) {
        this.services = services;
    }

    public void load() {
        warps.clear();
        services.configs().load(RESOURCE);
        Cfg root = services.configs().view(RESOURCE, "warps");

        for (String key : root.keys()) {
            Cfg node = root.child(key);
            if (node == null) {
                continue;
            }
            Location location = PlayerDataStore.readLocation(
                    node.raw() == null ? null : node.raw().getConfigurationSection("location"));
            if (location == null) {
                services.configs().log().add(RESOURCE, "warps." + key + ".location",
                        "world is missing or not loaded, warp skipped");
                continue;
            }
            BigDecimal cost = BigDecimal.ZERO;
            String rawCost = node.string("cost", "0");
            ParseResult parsed = services.amounts().parse(rawCost);
            if (parsed.ok()) {
                cost = parsed.get();
            } else {
                services.configs().log().add(RESOURCE, "warps." + key + ".cost",
                        '"' + rawCost + "\" is not a valid amount, treated as free");
            }
            warps.put(key.toLowerCase(Locale.ROOT), new Warp(
                    key.toLowerCase(Locale.ROOT),
                    node.string("display", Palette.brand(prettyName(key))),
                    IconService.material(node.string("icon", ""), Material.ENDER_PEARL),
                    List.copyOf(node.stringList("description")),
                    location,
                    node.string("permission", ""),
                    cost,
                    node.number("warmup", 0d, 0d, 600d),
                    (long) node.number("cooldown", 0d, 0d, 86_400d),
                    node.string("category", "").toLowerCase(Locale.ROOT),
                    node.integer("slot", -1, -1, 53)));
        }
    }

    public Warp warp(String name) {
        return warps.get(name.toLowerCase(Locale.ROOT));
    }

    public java.util.Collection<Warp> all() {
        return warps.values();
    }

    public List<String> names() {
        return new ArrayList<>(warps.keySet());
    }

    public int count() {
        return warps.size();
    }

    // ------------------------------------------------------------------ menu

    public void openMenu(Player player) {
        Map<String, List<Warp>> grouped = new TreeMap<>();
        for (Warp warp : warps.values()) {
            grouped.computeIfAbsent(warp.category().isBlank() ? "general" : warp.category(),
                    key -> new ArrayList<>()).add(warp);
        }

        Screen.Builder builder = Screen.of("warps",
                        Palette.brand(services.messages().raw("warps.menu-title")))
                .line(services.messages().raw("warps.menu-subtitle"))
                .blank()
                .layout(ChestLayout.chest(6));

        for (Map.Entry<String, List<Warp>> entry : grouped.entrySet()) {
            List<Warp> group = entry.getValue();
            group.sort(Comparator.comparing(Warp::key));
            for (Warp warp : group) {
                builder.button(warpButton(player, warp, entry.getKey()));
            }
        }
        services.sounds().play(player, SoundService.MENU_OPEN);
        services.ui().open(player, builder.build());
    }

    private ScreenButton warpButton(Player player, Warp warp, String category) {
        ScreenButton.Builder button = ScreenButton.of("warp-" + warp.key(),
                        Palette.brand(warp.display()))
                .material(warp.icon())
                .style(ButtonStyle.PRIMARY)
                .slot(warp.slot());

        for (String line : warp.description()) {
            button.line(Palette.colour(Palette.MUTED, Glyphs.ITEM + ' ' + line));
        }
        button.line(Palette.colour(Palette.STRUCTURE, Glyphs.ITEM + " Category: " + category));
        if (warp.cost().signum() > 0) {
            button.line(applied("warps.cost-line", Ctx.of("cost",
                    services.amounts().formatExact(warp.cost()))));
        }

        if (!warp.permission().isBlank() && !player.hasPermission(warp.permission())) {
            button.locked(applied("warps.locked",
                    Ctx.of("permission", warp.permission())));
            return button.build();
        }
        button.action(clicker -> teleport(clicker, warp));
        return button.build();
    }

    // -------------------------------------------------------------- teleport

    /** @return true when the teleport started, so the caller may begin a cooldown */
    public boolean teleport(Player player, Warp warp) {
        if (!warp.permission().isBlank() && !player.hasPermission(warp.permission())) {
            services.messages().send(player, "warps.no-access");
            return false;
        }
        long remaining = services.cooldowns().remaining(player, "warp." + warp.key());
        if (remaining > 0 && !player.hasPermission("dripleaf.bypass.cooldown")) {
            services.messages().send(player, "errors.cooldown", Ctx.of("time",
                    net.dripleaf.core.common.command.DripleafCommand.formatDuration(remaining)));
            return false;
        }

        BigDecimal cost = warp.cost();
        boolean charged = cost.signum() > 0 && !player.hasPermission("dripleaf.bypass.cost");
        if (charged && !services.currencies().money().has(player, cost)) {
            services.messages().send(player, "warps.cannot-afford", Ctx.of("cost",
                    services.amounts().formatExact(cost)));
            return false;
        }

        services.ui().close(player);
        double warmup = player.hasPermission("dripleaf.bypass.warmup") ? 0d : warp.warmup();
        services.warmups().begin(player, warp.display(), warmup, () -> {
            // Re-check at the moment of travel: the warmup gave them time to spend it.
            if (charged && !services.currencies().money().withdraw(player, cost)) {
                services.messages().send(player, "warps.cannot-afford", Ctx.of("cost",
                        services.amounts().formatExact(cost)));
                return;
            }
            services.players().get(player).lastLocation(player.getLocation());
            player.teleportAsync(warp.location()).thenAccept(success -> {
                if (Boolean.TRUE.equals(success)) {
                    services.sounds().play(player, SoundService.TELEPORT_COMPLETE);
                    services.messages().send(player, "warps.teleported",
                            Ctx.of("warp", warp.display()));
                    services.cooldowns().start(player, "warp." + warp.key(), warp.cooldown());
                }
            });
        });
        return true;
    }

    // ------------------------------------------------------------ mutation

    /** Creates or replaces a warp at {@code location} and writes the file atomically. */
    public void save(String name, Location location, String category) {
        String key = name.toLowerCase(Locale.ROOT);
        Path path = services.plugin().getDataFolder().toPath().resolve(RESOURCE);
        YamlConfiguration yaml = AtomicYaml.read(path, services.plugin().getLogger());
        ConfigurationSection section = yaml.getConfigurationSection("warps");
        if (section == null) {
            section = yaml.createSection("warps");
        }
        ConfigurationSection node = section.getConfigurationSection(key);
        if (node == null) {
            node = section.createSection(key);
            // The warp's own name, not a material lookup of it. Looking the key
            // up as a Material meant every /setwarp produced "Ender Pearl".
            node.set("display", Palette.brand(prettyName(key)));
            node.set("icon", "ENDER_PEARL");
            node.set("permission", "");
            node.set("cost", 0);
            node.set("warmup", 0);
            node.set("cooldown", 0);
            node.set("category", category);
        }
        PlayerDataStore.writeLocation(node, "location", location);
        write(path, yaml);
        load();
    }

    public boolean delete(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!warps.containsKey(key)) {
            return false;
        }
        Path path = services.plugin().getDataFolder().toPath().resolve(RESOURCE);
        YamlConfiguration yaml = AtomicYaml.read(path, services.plugin().getLogger());
        ConfigurationSection section = yaml.getConfigurationSection("warps");
        if (section != null) {
            section.set(key, null);
        }
        write(path, yaml);
        load();
        return true;
    }

    private void write(Path path, YamlConfiguration yaml) {
        services.schedulers().async(() -> {
            try {
                AtomicYaml.write(path, yaml);
            } catch (Exception ex) {
                services.plugin().getLogger().log(java.util.logging.Level.SEVERE,
                        "Could not save warps.yml", ex);
            }
        });
    }

    private String applied(String key, Ctx ctx) {
        return ctx.applyRaw(services.messages().raw(key));
    }

    /** {@code nether_hub} to {@code Nether Hub}, for a warp created in game. */
    static String prettyName(String key) {
        String spaced = key.replace('_', ' ').replace('-', ' ');
        StringBuilder sb = new StringBuilder(spaced.length());
        boolean capitalise = true;
        for (int i = 0; i < spaced.length(); i++) {
            char c = spaced.charAt(i);
            sb.append(capitalise ? Character.toUpperCase(c) : c);
            capitalise = c == ' ';
        }
        return sb.toString();
    }
}
