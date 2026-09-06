package net.dripleafmc.rebirth.requirement;

import net.dripleafmc.rebirth.hook.McMmoBridge;
import org.bukkit.entity.Player;

/** mcMMO power level, read through the native API when mcMMO is present. */
public record McMmoPowerRequirement(String id, String display, double target,
                                    McMmoBridge bridge) implements Requirement {

    @Override
    public double progress(Player player) {
        return bridge.powerLevel(player);
    }
}
