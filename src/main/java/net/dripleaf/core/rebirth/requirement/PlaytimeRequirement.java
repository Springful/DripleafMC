package net.dripleaf.core.rebirth.requirement;

import net.dripleaf.core.common.storage.PlayerDataStore;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Hours played, from DripleafCore's own tracking.
 *
 * <p>Not used by any shipped tier — it is here as the worked example for
 * "adding a new requirement type", referenced from {@code docs/REBIRTH.md}.
 */
public final class PlaytimeRequirement implements Requirement {

    private final PlayerDataStore store;
    private final double hours;
    private final String display;

    public PlaytimeRequirement(PlayerDataStore store, double hours, String display) {
        this.store = store;
        this.hours = hours;
        this.display = display;
    }

    @Override
    public String type() {
        return "playtime";
    }

    @Override
    public String display() {
        return display;
    }

    @Override
    public double progress(Player player) {
        return store.get(player).playtimeMillis() / 3_600_000d;
    }

    @Override
    public double target() {
        return hours;
    }

    @Override
    public Material icon() {
        return Material.CLOCK;
    }
}
