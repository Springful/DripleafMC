package net.dripleaf.core.rebirth;

import net.dripleaf.core.rebirth.requirement.RequirementState;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the menus need about one player's eligibility for one path,
 * evaluated once.
 *
 * <p>The Skript re-evaluated requirements per lore line. This is computed once
 * and passed around, which is both faster and the only way the "which
 * requirement is missing?" text on a locked button can be accurate.
 *
 * @param cooldownRemaining milliseconds left, or 0
 */
public record CheckResult(Status status, RebirthTier tier, RebirthPath path, BigDecimal cost,
                          BigDecimal balance, List<RequirementState> requirements,
                          long cooldownRemaining) {

    public enum Status {
        OK,
        MAXED,
        COOLDOWN,
        INSUFFICIENT_FUNDS,
        REQUIREMENTS_UNMET,
        /** Vault, or the configured currency, is not answering. */
        ECONOMY_UNAVAILABLE
    }

    public boolean allowed() {
        return status == Status.OK;
    }

    public boolean costMet() {
        return balance.compareTo(cost) >= 0;
    }

    /** The first unmet requirement, for the "you still need…" line. */
    public RequirementState firstUnmet() {
        for (RequirementState state : requirements) {
            if (!state.met()) {
                return state;
            }
        }
        return null;
    }

    public boolean requirementsMet() {
        return firstUnmet() == null;
    }
}
