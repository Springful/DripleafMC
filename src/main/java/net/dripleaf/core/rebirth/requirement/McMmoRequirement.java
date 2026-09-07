package net.dripleaf.core.rebirth.requirement;

import net.dripleaf.core.common.hook.McMmoBridge;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * mcMMO power level.
 *
 * <p>The one requirement that genuinely needs a third-party read. It goes
 * through {@link McMmoBridge}, which prefers the native API and falls back to
 * the placeholder — and returns zero when mcMMO is absent, so the requirement
 * simply never passes rather than breaking the menu.
 */
public final class McMmoRequirement implements Requirement {

    private final McMmoBridge mcmmo;
    private final int amount;
    private final String display;

    public McMmoRequirement(McMmoBridge mcmmo, int amount, String display) {
        this.mcmmo = mcmmo;
        this.amount = amount;
        this.display = display;
    }

    @Override
    public String type() {
        return "mcmmo-power";
    }

    @Override
    public String display() {
        return display;
    }

    @Override
    public double progress(Player player) {
        return mcmmo.powerLevel(player);
    }

    @Override
    public double target() {
        return amount;
    }

    @Override
    public Material icon() {
        return Material.EXPERIENCE_BOTTLE;
    }
}
