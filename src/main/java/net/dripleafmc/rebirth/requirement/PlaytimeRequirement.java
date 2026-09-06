package net.dripleafmc.rebirth.requirement;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;

/** Hours played, taken from PLAY_ONE_MINUTE (which is measured in ticks). */
public record PlaytimeRequirement(String id, String display, double target) implements Requirement {

    private static final double TICKS_PER_HOUR = 20d * 60d * 60d;

    @Override
    public double progress(Player player) {
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE) / TICKS_PER_HOUR;
    }
}
