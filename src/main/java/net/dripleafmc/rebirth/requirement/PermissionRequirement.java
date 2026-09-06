package net.dripleafmc.rebirth.requirement;

import org.bukkit.entity.Player;

/** A pass/fail gate. Progress is 1 when held, 0 otherwise. */
public record PermissionRequirement(String id, String display, String node) implements Requirement {

    @Override
    public double progress(Player player) {
        return player.hasPermission(node) ? 1d : 0d;
    }

    @Override
    public double target() {
        return 1d;
    }
}
