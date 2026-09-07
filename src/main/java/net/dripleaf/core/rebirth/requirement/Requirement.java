package net.dripleaf.core.rebirth.requirement;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * One condition a player must satisfy to rebirth.
 *
 * <p>Adding a new type later — playtime, quests completed, kills of a custom
 * mob — is a new class implementing this and a new {@code type:} key in config.
 * Nothing else changes.
 */
public interface Requirement {

    /** Config key, e.g. {@code mcmmo-power} or {@code stat}. */
    String type();

    /** What players see, e.g. {@code Stone Mined}. */
    String display();

    /** Evaluated value for this player. */
    double progress(Player player);

    /** The value needed. */
    double target();

    /** Icon suggestion for the tier browser; {@code null} to leave it to the tier. */
    default Material icon() {
        return null;
    }

    default RequirementState evaluate(Player player) {
        return new RequirementState(this, progress(player), target());
    }
}
