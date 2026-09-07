package net.dripleaf.core.rebirth.requirement;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * A Bukkit {@link Statistic}, read natively.
 *
 * <p>The Skript read every one of these through a PlaceholderAPI string parse,
 * on every lore line, on every menu open. {@code player.getStatistic(...)} is a
 * field read.
 *
 * <p>Three shapes, because Bukkit has three: a plain statistic
 * ({@code FISH_CAUGHT}), a block/item statistic ({@code MINE_BLOCK} plus a
 * material) and an entity statistic ({@code KILL_ENTITY} plus an entity type).
 */
public final class StatisticRequirement implements Requirement {

    private final Statistic statistic;
    private final Material material;
    private final EntityType entity;
    private final double amount;
    private final String display;

    public StatisticRequirement(Statistic statistic, Material material, EntityType entity,
                                double amount, String display) {
        this.statistic = statistic;
        this.material = material;
        this.entity = entity;
        this.amount = amount;
        this.display = display;
    }

    @Override
    public String type() {
        return "stat";
    }

    @Override
    public String display() {
        return display;
    }

    @Override
    public double progress(Player player) {
        try {
            if (material != null) {
                return player.getStatistic(statistic, material);
            }
            if (entity != null) {
                return player.getStatistic(statistic, entity);
            }
            return player.getStatistic(statistic);
        } catch (IllegalArgumentException ex) {
            // A statistic/qualifier mismatch that slipped past validation. Report
            // zero rather than throwing out of a menu render.
            return 0d;
        }
    }

    @Override
    public double target() {
        return amount;
    }

    @Override
    public Material icon() {
        return material;
    }

    public Statistic statistic() {
        return statistic;
    }

    public EntityType entity() {
        return entity;
    }
}
