package net.dripleafmc.rebirth.ui.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;

/** Small item helper. Keeps MenuUI readable. */
public final class Items {

    private Items() {
    }

    public static Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String cleaned = raw.toLowerCase(Locale.ROOT);
        NamespacedKey key = cleaned.indexOf(':') >= 0
                ? NamespacedKey.fromString(cleaned)
                : NamespacedKey.minecraft(cleaned);
        Material material = key == null ? null : Registry.MATERIAL.get(key);
        return material == null ? fallback : material;
    }

    public static ItemStack build(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
