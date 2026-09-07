package net.dripleaf.core.rebirth.requirement;

/**
 * A requirement evaluated against one player at one moment.
 *
 * @param requirement what was checked
 * @param progress    the player's current value
 * @param target      the value needed
 */
public record RequirementState(Requirement requirement, double progress, double target) {

    public boolean met() {
        return progress >= target;
    }

    public double fraction() {
        return target <= 0d ? 1d : Math.min(1d, progress / target);
    }
}
