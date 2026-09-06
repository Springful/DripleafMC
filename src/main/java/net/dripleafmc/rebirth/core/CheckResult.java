package net.dripleafmc.rebirth.core;

import net.dripleafmc.rebirth.tier.RebirthPath;
import net.dripleafmc.rebirth.tier.RebirthTier;

import java.util.List;

/**
 * The complete answer to "can this player take this path right now, and why not".
 * Computed once per menu/dialog open and handed to whichever front end is active.
 */
public record CheckResult(Status status,
                          RebirthPath path,
                          RebirthTier tier,
                          List<RequirementState> requirements,
                          double cost,
                          double balance,
                          boolean costMet,
                          long cooldownRemaining) {

    public enum Status {
        OK,
        MAXED,
        COOLDOWN,
        REQUIREMENTS_UNMET,
        INSUFFICIENT_FUNDS,
        NO_TIER_DEFINED
    }

    public boolean allowed() {
        return status == Status.OK;
    }

    /** True when only the money is missing - useful for a distinct denial message. */
    public boolean onlyMoneyMissing() {
        return status == Status.INSUFFICIENT_FUNDS;
    }
}
