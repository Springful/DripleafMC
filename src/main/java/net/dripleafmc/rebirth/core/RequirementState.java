package net.dripleafmc.rebirth.core;

import net.dripleafmc.rebirth.requirement.Requirement;

/** A requirement evaluated once, so the UI never re-queries it per lore line. */
public record RequirementState(Requirement requirement, double progress, double target, boolean met) {

    public static RequirementState of(Requirement requirement, org.bukkit.entity.Player player) {
        double progress = requirement.progress(player);
        double target = requirement.target();
        return new RequirementState(requirement, progress, target, progress >= target);
    }
}
