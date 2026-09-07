package net.dripleaf.core.core.shop;

import org.bukkit.Material;

import java.util.List;

/**
 * A group of {@link ShopItem}s.
 *
 * @param order sort position on the root screen; ties fall back to the key
 */
public record ShopCategory(String key, String display, Material icon, List<String> description,
                           int order) {

    /** The bucket for items whose configured category does not exist. */
    public static ShopCategory uncategorised() {
        return new ShopCategory("uncategorised", "Uncategorised", Material.CHEST,
                List.of("Items whose category could not be found."), Integer.MAX_VALUE);
    }
}
