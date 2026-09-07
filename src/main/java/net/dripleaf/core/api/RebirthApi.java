package net.dripleaf.core.api;

import org.bukkit.entity.Player;

/**
 * What the core module is allowed to know about rebirth.
 *
 * <p>Exactly one thing matters across the boundary: the sell multiplier, which
 * the shop engine applies to every sale. Everything else — tiers, requirements,
 * rewards — stays inside the rebirth module.
 */
public interface RebirthApi {

    /** Current tier number, 0 for a player who has never rebirthed. */
    int tier(Player player);

    /**
     * Sell-price multiplier for this player, as a factor: {@code 1.0} at tier 0,
     * {@code 2.4} for the +140% granted at tier 20. The shop multiplies by this
     * on top of its own global multiplier.
     */
    double sellMultiplier(Player player);

    /** Highest tier defined in {@code tiers.yml}. */
    int maxTier();
}
