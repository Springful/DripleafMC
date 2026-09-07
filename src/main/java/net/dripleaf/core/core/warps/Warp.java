package net.dripleaf.core.core.warps;

import org.bukkit.Location;
import org.bukkit.Material;

import java.math.BigDecimal;
import java.util.List;

/**
 * A server warp.
 *
 * @param permission blank means everyone
 * @param cost       money price; supports k/m/b/t in config
 * @param slot       fixed menu slot, or {@code -1} to auto-arrange
 */
public record Warp(String key, String display, Material icon, List<String> description,
                   Location location, String permission, BigDecimal cost, double warmup,
                   long cooldown, String category, int slot) {
}
