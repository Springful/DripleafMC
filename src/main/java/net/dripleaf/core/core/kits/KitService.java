package net.dripleaf.core.core.kits;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.common.config.Cfg;
import net.dripleaf.core.common.icon.IconService;
import net.dripleaf.core.common.sound.SoundService;
import net.dripleaf.core.common.storage.PlayerData;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.Glyphs;
import net.dripleaf.core.common.text.Palette;
import net.dripleaf.core.common.ui.ButtonStyle;
import net.dripleaf.core.common.ui.ChestLayout;
import net.dripleaf.core.common.ui.Screen;
import net.dripleaf.core.common.ui.ScreenButton;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kits, their menu and their cooldowns.
 *
 * <p>Kit cooldowns live in player data rather than memory: a daily kit that
 * resets because the server restarted is a daily kit nobody trusts.
 *
 * <p>Items are stored through Bukkit's own {@link ItemStack} serialisation, so
 * enchantments, custom names, lore and component data round-trip without a
 * bespoke format to maintain.
 */
public final class KitService {

    private static final String RESOURCE = "core/kits.yml";

    private final Services services;
    private final Map<String, Kit> kits = new LinkedHashMap<>(16);

    public KitService(Services services) {
        this.services = services;
    }

    @SuppressWarnings("unchecked")
    public void load() {
        kits.clear();
        services.configs().load(RESOURCE);
        Cfg root = services.configs().view(RESOURCE, "kits");

        for (String key : root.keys()) {
            Cfg node = root.child(key);
            if (node == null || node.raw() == null) {
                continue;
            }
            List<ItemStack> items = new ArrayList<>(16);
            for (Object entry : node.raw().getList("items", List.of())) {
                if (entry instanceof ItemStack stack) {
                    items.add(stack);
                }
            }
            kits.put(key.toLowerCase(Locale.ROOT), new Kit(
                    key.toLowerCase(Locale.ROOT),
                    node.string("display", IconService.pretty(
                            IconService.material(key, Material.CHEST))),
                    IconService.material(node.string("icon", ""), Material.CHEST),
                    List.copyOf(node.stringList("description")),
                    List.copyOf(items),
                    List.copyOf(node.stringList("commands")),
                    node.string("permission", ""),
                    (long) node.number("cooldown", 0d, 0d, 31_536_000d),
                    node.bool("one-time", false)));
        }
    }

    public Kit kit(String name) {
        return kits.get(name.toLowerCase(Locale.ROOT));
    }

    public List<String> names() {
        return new ArrayList<>(kits.keySet());
    }

    public int count() {
        return kits.size();
    }

    /** @return remaining cooldown in milliseconds, or 0 when claimable */
    public long remaining(Player player, Kit kit) {
        PlayerData data = services.players().get(player);
        Long last = data.kitUses().get(kit.key());
        if (last == null) {
            return 0L;
        }
        if (kit.oneTime()) {
            return Long.MAX_VALUE;
        }
        long ready = last + kit.cooldown() * 1000L;
        return Math.max(0L, ready - System.currentTimeMillis());
    }

    /** @return true when the kit was handed over */
    public boolean claim(Player player, Kit kit) {
        if (!kit.permission().isBlank() && !player.hasPermission(kit.permission())) {
            services.messages().send(player, "kits.no-access", Ctx.of("kit", kit.display()));
            return false;
        }
        long remaining = remaining(player, kit);
        if (remaining == Long.MAX_VALUE) {
            services.messages().send(player, "kits.one-time-used", Ctx.of("kit", kit.display()));
            return false;
        }
        if (remaining > 0L) {
            services.messages().send(player, "kits.cooldown", new Ctx()
                    .put("kit", kit.display())
                    .put("time", DripleafCommand.formatDuration(remaining)));
            return false;
        }

        int needed = kit.items().size();
        if (freeSlots(player) < needed) {
            services.messages().send(player, "kits.inventory-full",
                    Ctx.of("slots", String.valueOf(needed)));
            return false;
        }

        for (ItemStack stack : kit.items()) {
            player.getInventory().addItem(stack.clone());
        }
        Ctx ctx = new Ctx().put("player", player.getName()).put("kit", kit.key());
        for (String command : kit.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), ctx.applyRaw(command));
        }

        PlayerData data = services.players().get(player);
        data.kitUses().put(kit.key(), System.currentTimeMillis());
        data.markDirty();

        services.sounds().play(player, SoundService.PURCHASE_SUCCESS);
        services.messages().send(player, "kits.claimed", Ctx.of("kit", kit.display()));
        return true;
    }

    public void openMenu(Player player) {
        Screen.Builder builder = Screen.of("kits",
                        Palette.brand(services.messages().raw("kits.menu-title")))
                .line(services.messages().raw("kits.menu-subtitle"))
                .blank()
                .layout(ChestLayout.chest(6));

        for (Kit kit : kits.values()) {
            ScreenButton.Builder button = ScreenButton.of("kit-" + kit.key(),
                            Palette.brand(kit.display()))
                    .material(kit.icon())
                    .style(ButtonStyle.PRIMARY);
            for (String line : kit.description()) {
                button.line(Palette.colour(Palette.MUTED, Glyphs.ITEM + ' ' + line));
            }
            button.line(Palette.colour(Palette.STRUCTURE,
                    Glyphs.ITEM + " " + kit.items().size() + " items"));

            if (!kit.permission().isBlank() && !player.hasPermission(kit.permission())) {
                button.locked(services.messages().raw("kits.locked"));
            } else {
                long remaining = remaining(player, kit);
                if (remaining == Long.MAX_VALUE) {
                    button.locked(services.messages().raw("kits.one-time-used-short"));
                } else if (remaining > 0L) {
                    button.locked(new Ctx().put("time", DripleafCommand.formatDuration(remaining))
                            .applyRaw(services.messages().raw("kits.cooldown-short")));
                } else {
                    button.action(clicker -> {
                        if (claim(clicker, kit)) {
                            clicker.closeInventory();
                            clicker.closeDialog();
                        }
                    });
                }
            }
            builder.button(button.build());
        }

        services.sounds().play(player, SoundService.MENU_OPEN);
        services.ui().open(player, builder.build());
    }

    private static int freeSlots(Player player) {
        int free = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                free++;
            }
        }
        return free;
    }
}
