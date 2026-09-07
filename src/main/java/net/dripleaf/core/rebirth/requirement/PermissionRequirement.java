package net.dripleaf.core.rebirth.requirement;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Holding a permission node. Binary: 1 when held, 0 when not. */
public final class PermissionRequirement implements Requirement {

    private final String node;
    private final String display;

    public PermissionRequirement(String node, String display) {
        this.node = node;
        this.display = display;
    }

    @Override
    public String type() {
        return "permission";
    }

    @Override
    public String display() {
        return display;
    }

    @Override
    public double progress(Player player) {
        return player.hasPermission(node) ? 1d : 0d;
    }

    @Override
    public double target() {
        return 1d;
    }

    @Override
    public Material icon() {
        return Material.NAME_TAG;
    }
}
