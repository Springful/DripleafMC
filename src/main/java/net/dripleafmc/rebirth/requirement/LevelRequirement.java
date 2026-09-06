package net.dripleafmc.rebirth.requirement;

import org.bukkit.entity.Player;

/** Vanilla experience levels. */
public record LevelRequirement(String id, String display, double target) implements Requirement {

    @Override
    public double progress(Player player) {
        return player.getLevel();
    }
}
