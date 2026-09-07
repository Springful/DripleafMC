package net.dripleaf.core.rebirth;

import net.dripleaf.core.rebirth.requirement.Requirement;
import org.bukkit.Material;

import java.math.BigDecimal;
import java.util.List;

/**
 * One tier, parsed once from {@code rebirth/tiers.yml} into an immutable record.
 *
 * <p>Nothing in Java hardcodes 25, iterates to 25, or assumes a maximum. Adding
 * tier 26 is a YAML block and a reload.
 *
 * @param number       tier number; tiers must be consecutive from 1
 * @param numeral      shown in menus; any text, conventionally a Roman numeral
 * @param cost         standard-path money cost
 * @param soulMultiplier soul-path cost is {@code cost × this}
 * @param requirements everything that must be true before either path opens
 * @param cash         starting cash injected after the wipe
 * @param keys         crate keys granted
 * @param multiplier   sell/jobs bonus in percent, e.g. {@code 140} for +140%
 * @param souls        soul tokens, granted on the soul path only
 * @param unlocks      display-only strings; the grants themselves are commands
 * @param commands     console commands run at completion
 * @param soulCommands extra console commands, soul path only
 * @param icon         menu icon, normally the requirement's material
 */
public record RebirthTier(int number, String numeral, BigDecimal cost, double soulMultiplier,
                          List<Requirement> requirements, BigDecimal cash, int keys,
                          double multiplier, int souls, List<String> unlocks,
                          List<String> commands, List<String> soulCommands, Material icon) {

    public BigDecimal cost(RebirthPath path) {
        return path == RebirthPath.SOUL
                ? cost.multiply(BigDecimal.valueOf(soulMultiplier))
                : cost;
    }

    /** Souls are the soul path's whole point; the standard path grants none. */
    public int souls(RebirthPath path) {
        return path == RebirthPath.SOUL ? souls : 0;
    }
}
