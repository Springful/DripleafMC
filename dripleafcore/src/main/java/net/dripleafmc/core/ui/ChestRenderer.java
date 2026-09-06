package net.dripleafmc.core.ui;

import net.dripleafmc.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Chest-inventory fallback for Bedrock players and anyone who turned dialogs off.
 * Same menu model, different surface — no screen exists in only one of the two.
 */
public final class ChestRenderer {

    public static final int PAGE_SIZE = 45;   // six rows, bottom row reserved for nav

    /** Holder carries the click map so the listener needs no lookup table of its own. */
    public static final class Holder implements InventoryHolder {
        final Map<Integer, Consumer<Player>> actions = new HashMap<>(16);
        final Map<Integer, Consumer<Player>> secondary = new HashMap<>(4);
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private ChestRenderer() {}

    public static void open(Player player, Menu menu) {
        open(player, menu, 0);
    }

    public static void open(Player player, Menu menu, int page) {
        List<MenuButton> buttons = menu.buttons;
        int pages = Math.max(1, (buttons.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        int from = current * PAGE_SIZE;
        int to = Math.min(buttons.size(), from + PAGE_SIZE);

        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54, menu.title);
        holder.inventory = inv;

        for (int i = from; i < to; i++) {
            MenuButton b = buttons.get(i);
            int slot = i - from;
            inv.setItem(slot, icon(b));
            holder.actions.put(slot, b.onClick());
            if (b.onRightClick() != null) holder.secondary.put(slot, b.onRightClick());
        }

        if (current > 0) {
            inv.setItem(45, simple(Material.ARROW, Component.text("Previous page", NamedTextColor.GRAY)));
            int target = current - 1;
            holder.actions.put(45, p -> open(p, menu, target));
        }
        if (current < pages - 1) {
            inv.setItem(53, simple(Material.ARROW, Component.text("Next page", NamedTextColor.GRAY)));
            int target = current + 1;
            holder.actions.put(53, p -> open(p, menu, target));
        }
        if (menu.onBack != null) {
            inv.setItem(49, simple(Material.RED_STAINED_GLASS_PANE,
                    menu.backLabel == null ? Component.text("Back", NamedTextColor.RED) : menu.backLabel));
            holder.actions.put(49, menu.onBack);
        }
        if (!menu.body.isEmpty()) {
            ItemStack info = simple(Material.PAPER, menu.title);
            ItemMeta meta = info.getItemMeta();
            meta.lore(new ArrayList<>(menu.body));
            info.setItemMeta(meta);
            inv.setItem(48, info);
        }

        player.openInventory(inv);
    }

    /**
     * Chest fallback for a form. Booleans become toggles, a number becomes preset
     * buttons, text hands off to a chat prompt.
     */
    public static void open(Player player, MenuForm form, Prompt prompt) {
        Menu menu = new Menu(form.title);
        for (Component line : form.body) menu.body(line);

        if (form.textKey != null) {
            String key = form.textKey;
            menu.add(MenuButton.of(
                    Component.text("Type it in chat", NamedTextColor.WHITE),
                    form.textLabel, Material.NAME_TAG,
                    p -> {
                        p.closeInventory();
                        p.sendMessage(form.textLabel);
                        prompt.ask(p, input -> {
                            Map<String, Object> values = new HashMap<>(1);
                            values.put(key, input);
                            form.onConfirm.accept(p, new MenuValues(values));
                        });
                    }));
        }

        if (form.numberKey != null) {
            String key = form.numberKey;
            for (int preset : form.chestPresets) {
                if (preset < form.min || preset > form.max) continue;
                int value = preset;
                menu.add(MenuButton.of(
                        Component.text(String.valueOf(value), NamedTextColor.WHITE),
                        List.of(form.numberLabel), Material.PAPER, Math.min(64, Math.max(1, value)),
                        p -> {
                            Map<String, Object> values = new HashMap<>(1);
                            values.put(key, (float) value);
                            form.onConfirm.accept(p, new MenuValues(values));
                        }));
            }
            int maxValue = Math.round(form.max);
            menu.add(MenuButton.of(
                    Component.text("Max (" + maxValue + ")", NamedTextColor.GREEN),
                    form.numberLabel, Material.HOPPER,
                    p -> {
                        Map<String, Object> values = new HashMap<>(1);
                        values.put(key, (float) maxValue);
                        form.onConfirm.accept(p, new MenuValues(values));
                    }));
        }

        for (MenuForm.Bool b : form.bools) {
            boolean state = b.initial();
            menu.add(MenuButton.of(
                    b.label(),
                    Component.text(state ? "Enabled" : "Disabled",
                            state ? NamedTextColor.GREEN : NamedTextColor.RED),
                    state ? Material.LIME_DYE : Material.GRAY_DYE,
                    p -> {
                        Map<String, Object> values = new HashMap<>(form.bools.size());
                        for (MenuForm.Bool other : form.bools) values.put(other.key(), other.initial());
                        values.put(b.key(), !state);
                        form.onConfirm.accept(p, new MenuValues(values));
                    }));
        }

        if (form.onCancel != null) menu.back(form.cancelLabel, form.onCancel);
        open(player, menu, 0);
    }

    private static ItemStack icon(MenuButton b) {
        ItemStack item = new ItemStack(b.icon(), Math.max(1, Math.min(64, b.amount())));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(b.label());
            if (!b.lore().isEmpty()) meta.lore(new ArrayList<>(b.lore()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack simple(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Convenience for menus built from raw strings. */
    public static Component label(String miniMessage) {
        return Text.mm(miniMessage);
    }
}
