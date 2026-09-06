package net.dripleafmc.rebirth.config;

import net.dripleafmc.rebirth.RebirthPlugin;
import net.dripleafmc.rebirth.requirement.Requirement;
import net.dripleafmc.rebirth.requirement.RequirementFactory;
import net.dripleafmc.rebirth.tier.PathSpec;
import net.dripleafmc.rebirth.tier.RebirthPath;
import net.dripleafmc.rebirth.tier.RebirthTier;
import net.dripleafmc.rebirth.util.Numbers;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * tiers.yml, compiled once into immutable {@link RebirthTier} records.
 * <p>
 * The Skript version rebuilt the whole tier table from a 25-branch if-chain on
 * every single menu open, every confirmation, and every reward payout. This
 * happens exactly once per (re)load.
 */
public final class TierRegistry {

    private final Map<Integer, RebirthTier> tiers = new HashMap<>(32);
    private final int highest;

    public TierRegistry(RebirthPlugin plugin, RequirementFactory factory) {
        YamlConfiguration yaml = ConfigFile.load(plugin, "tiers.yml");

        Map<RebirthPath, Double> defaultMultipliers = new EnumMap<>(RebirthPath.class);
        Map<RebirthPath, List<String>> defaultSets = new EnumMap<>(RebirthPath.class);
        for (RebirthPath path : RebirthPath.values()) {
            defaultMultipliers.put(path,
                    yaml.getDouble("defaults.cost-multipliers." + path.id(),
                            path == RebirthPath.SOUL ? 2.0d : 1.0d));
            defaultSets.put(path,
                    List.copyOf(yaml.getStringList("defaults.reward-sets." + path.id())));
        }

        ConfigurationSection root = yaml.getConfigurationSection("tiers");
        int max = 0;

        if (root == null) {
            plugin.getLogger().severe("tiers.yml has no 'tiers:' section - no rebirths will be available.");
        } else {
            for (String key : root.getKeys(false)) {
                int level;
                try {
                    level = Integer.parseInt(key);
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("tiers.yml: '" + key + "' is not a tier number (skipped).");
                    continue;
                }
                ConfigurationSection node = root.getConfigurationSection(key);
                if (node == null) {
                    continue;
                }
                tiers.put(level, build(plugin, factory, node, level, defaultMultipliers, defaultSets));
                max = Math.max(max, level);
            }
        }

        this.highest = max;
        plugin.getLogger().info("Loaded " + tiers.size() + " rebirth tiers (highest: " + highest + ").");
    }

    private RebirthTier build(RebirthPlugin plugin,
                              RequirementFactory factory,
                              ConfigurationSection node,
                              int level,
                              Map<RebirthPath, Double> defaultMultipliers,
                              Map<RebirthPath, List<String>> defaultSets) {

        String roman = node.getString("roman", Numbers.roman(level));
        double baseCost = node.getDouble("cost", 0d);

        List<Requirement> requirements =
                factory.build(node.getConfigurationSection("requirements"), level);

        Map<String, Double> values = new HashMap<>(6);
        ConfigurationSection valueSection = node.getConfigurationSection("rewards.values");
        if (valueSection != null) {
            for (String key : valueSection.getKeys(false)) {
                values.put(key, valueSection.getDouble(key));
            }
        }

        List<String> unlocks = List.copyOf(node.getStringList("rewards.unlocks"));

        Map<RebirthPath, PathSpec> paths = new EnumMap<>(RebirthPath.class);
        for (RebirthPath path : RebirthPath.values()) {
            double multiplier = node.getDouble("cost-multipliers." + path.id(),
                    defaultMultipliers.get(path));
            double cost = node.getDouble("costs." + path.id(), baseCost * multiplier);

            List<String> sets = node.contains("rewards.reward-sets." + path.id())
                    ? List.copyOf(node.getStringList("rewards.reward-sets." + path.id()))
                    : defaultSets.get(path);

            for (String set : sets) {
                if (!plugin.rewards().has(set)) {
                    plugin.getLogger().warning("tiers.yml -> tier " + level + " -> path " + path.id()
                            + " references unknown reward-set '" + set + "'.");
                }
            }
            paths.put(path, new PathSpec(cost, sets));
        }

        return new RebirthTier(level, roman, requirements, Map.copyOf(values), unlocks, Map.copyOf(paths));
    }

    public RebirthTier get(int level) {
        return tiers.get(level);
    }

    public boolean exists(int level) {
        return tiers.containsKey(level);
    }

    public int highest() {
        return highest;
    }

    public int size() {
        return tiers.size();
    }
}
