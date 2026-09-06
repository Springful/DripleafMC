package net.dripleafmc.rebirth.requirement;

import net.dripleafmc.rebirth.hook.PapiBridge;
import org.bukkit.entity.Player;

/**
 * Escape hatch for anything not covered natively (Jobs levels, quest counters,
 * a custom economy balance). Results are short-lived-cached by {@link PapiBridge}
 * so one menu open never parses the same placeholder twice.
 */
public record PlaceholderRequirement(String id, String display, String placeholder,
                                     double target, PapiBridge papi) implements Requirement {

    @Override
    public double progress(Player player) {
        return papi.number(player, placeholder);
    }
}
