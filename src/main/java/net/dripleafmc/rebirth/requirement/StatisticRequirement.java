package net.dripleafmc.rebirth.requirement;

import org.bukkit.Statistic;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Reads a vanilla statistic straight off the player.
 * <p>
 * This is the single biggest win over the Skript version, which round-tripped
 * every statistic through a PlaceholderAPI string parse on every lore line.
 */
public final class StatisticRequirement implements Requirement {

    private final String id;
    private final String display;
    private final Statistic statistic;
    private final Material material;
    private final EntityType entity;
    private final double target;

    public StatisticRequirement(String id, String display, Statistic statistic,
                                Material material, EntityType entity, double target) {
        this.id = id;
        this.display = display;
        this.statistic = statistic;
        this.material = material;
        this.entity = entity;
        this.target = target;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String display() {
        return display;
    }

    @Override
    public double progress(Player player) {
        if (material != null) {
            return player.getStatistic(statistic, material);
        }
        if (entity != null) {
            return player.getStatistic(statistic, entity);
        }
        return player.getStatistic(statistic);
    }

    @Override
    public double target() {
        return target;
    }
}
