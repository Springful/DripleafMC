package net.dripleaf.core.core.kits;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * A kit.
 *
 * @param cooldown seconds; persisted in player data, unlike command cooldowns,
 *                 because a daily kit must survive a restart
 * @param oneTime  claimable once ever
 */
public record Kit(String key, String display, Material icon, List<String> description,
                  List<ItemStack> items, List<String> commands, String permission,
                  long cooldown, boolean oneTime) {
}
