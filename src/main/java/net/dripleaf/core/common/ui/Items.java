package net.dripleaf.core.common.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Small item-building helper, so the chest renderer stays readable. */
public final class Items {

    private Items() {
    }

    public static ItemStack build(Material material, Component name, List<Component> lore) {
        return build(material, name, lore, 1, false);
    }

    public static ItemStack build(Material material, Component name, List<Component> lore,
                                  int amount, boolean glint) {
        ItemStack stack = new ItemStack(material == null ? Material.STONE : material,
                Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (name != null) {
            meta.displayName(name);
        }
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore);
        }
        if (glint) {
            // setEnchantmentGlintOverride avoids a fake enchantment showing in the tooltip.
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    /** A decorative pane with a blank name, used to fill the border. */
    public static ItemStack filler(Material material) {
        return build(material, Component.empty(), List.of());
    }

    /** Copies {@code source} and re-labels it, leaving the original untouched. */
    public static ItemStack relabel(ItemStack source, Component name, List<Component> lore,
                                    boolean glint) {
        ItemStack stack = source.clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (name != null) {
            meta.displayName(name);
        }
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore);
        }
        if (glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }
}
