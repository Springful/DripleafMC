package net.dripleafmc.core.worth;

import net.dripleafmc.core.config.Cfg;
import net.dripleafmc.core.profile.Profile;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The single price table. Shop, /sell, sell wands and item lore all read from here,
 * so a price can never mean two different things in two different places.
 *
 * Backed by an EnumMap: lookup is an array index off the material ordinal, which
 * matters because /sell walks a double chest of stacks in one tick.
 */
public final class Worth {

    private final Map<Material, Double> buy = new EnumMap<>(Material.class);
    private final Cfg cfg;
    private final Logger log;
    private double defaultPrice;

    public Worth(Cfg cfg, Logger log) {
        this.cfg = cfg;
        this.log = log;
    }

    public void load(File file) {
        buy.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        defaultPrice = yml.getDouble("settings.default", 0);
        ConfigurationSection items = yml.getConfigurationSection("items");
        if (items == null) {
            log.warning("[DripleafCore] worth.yml has no 'items' section.");
            return;
        }
        int bad = 0;
        for (String key : items.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                bad++;
                continue;
            }
            buy.put(material, items.getDouble(key));
        }
        if (bad > 0) log.warning("[DripleafCore] worth.yml: " + bad + " unknown material(s) skipped.");
        log.info("[DripleafCore] loaded " + buy.size() + " item prices.");
    }

    public Map<Material, Double> all() {
        return Collections.unmodifiableMap(buy);
    }

    /** Base buy price for one item. */
    public double buyPrice(Material material) {
        Double v = buy.get(material);
        return v == null ? defaultPrice : v;
    }

    public boolean priced(Material material) {
        return buy.containsKey(material) || defaultPrice > 0;
    }

    /** Base sell price for one item, before the player multiplier. */
    public double sellPrice(Material material) {
        return buyPrice(material) * cfg.sellRatio;
    }

    /**
     * What a player actually gets for a stack. Damaged and enchanted items are priced
     * at their material value scaled by remaining durability, so a nearly-broken
     * netherite pickaxe can't be sold as a fresh one.
     */
    public double sellValue(ItemStack stack, double multiplier) {
        if (stack == null || stack.getType().isAir()) return 0;
        double unit = sellPrice(stack.getType());
        if (unit <= 0) return 0;
        double value = unit * stack.getAmount() * multiplier;

        if (stack.getItemMeta() instanceof Damageable damageable && damageable.hasDamage()) {
            int max = stack.getType().getMaxDurability();
            if (max > 0) {
                double remaining = 1.0 - ((double) damageable.getDamage() / max);
                value *= Math.max(0.1, remaining);
            }
        }
        return value;
    }

    /** 1.0 plus the rebirth bonus, capped. Read from permissions so LuckPerms stays in charge. */
    public double multiplier(Player player, Profile profile) {
        double m = 1.0;
        for (int level = 10; level >= 1; level--) {
            if (player.hasPermission("dripleaf.rebirth." + level)) {
                m += cfg.rebirthStep * level;
                break;
            }
        }
        if (profile != null && profile.boosterActive()) {
            // Boosters affect shards, not coins — left here deliberately as a no-op hook.
            m += 0;
        }
        return Math.min(cfg.maxMultiplier, m);
    }
}
