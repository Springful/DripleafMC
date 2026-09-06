package net.dripleafmc.rebirth.requirement;

import net.dripleafmc.rebirth.RebirthPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a {@code requirements:} section into concrete {@link Requirement}
 * objects. Runs at load/reload only. Anything malformed is logged and skipped
 * rather than exploding the whole tier table.
 */
public final class RequirementFactory {

    private final RebirthPlugin plugin;

    public RequirementFactory(RebirthPlugin plugin) {
        this.plugin = plugin;
    }

    public List<Requirement> build(ConfigurationSection section, int tier) {
        if (section == null) {
            return List.of();
        }
        List<Requirement> out = new ArrayList<>(4);
        for (String key : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(key);
            if (node == null) {
                continue;
            }
            Requirement requirement = single(node, key, tier);
            if (requirement != null) {
                out.add(requirement);
            }
        }
        return List.copyOf(out);
    }

    private Requirement single(ConfigurationSection node, String key, int tier) {
        String type = node.getString("type", "STATISTIC").toUpperCase(Locale.ROOT);
        String display = node.getString("display", key);
        double amount = node.getDouble("amount", 0d);

        return switch (type) {
            case "STATISTIC" -> statistic(node, key, display, amount, tier);
            case "MCMMO_POWER", "MCMMO" ->
                    new McMmoPowerRequirement(key, display, amount, plugin.mcmmo());
            case "PLAYTIME" -> new PlaytimeRequirement(key, display, amount);
            case "LEVEL" -> new LevelRequirement(key, display, amount);
            case "PERMISSION" -> {
                String permission = node.getString("permission");
                if (permission == null) {
                    yield warn(tier, key, "missing 'permission'");
                }
                yield new PermissionRequirement(key, display, permission);
            }
            case "PLACEHOLDER" -> {
                String placeholder = node.getString("placeholder");
                if (placeholder == null) {
                    yield warn(tier, key, "missing 'placeholder'");
                }
                yield new PlaceholderRequirement(key, display, placeholder, amount, plugin.papi());
            }
            default -> warn(tier, key, "unknown type '" + type + "'");
        };
    }

    private Requirement statistic(ConfigurationSection node, String key, String display,
                                  double amount, int tier) {
        String rawStat = node.getString("statistic", "");
        Statistic statistic;
        try {
            statistic = Statistic.valueOf(rawStat.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return warn(tier, key, "unknown statistic '" + rawStat + "'");
        }

        Material material = null;
        EntityType entity = null;

        String rawMaterial = node.getString("material");
        if (rawMaterial != null) {
            material = Registry.MATERIAL.get(key(rawMaterial));
            if (material == null) {
                return warn(tier, key, "unknown material '" + rawMaterial + "'");
            }
        }

        String rawEntity = node.getString("entity");
        if (rawEntity != null) {
            entity = Registry.ENTITY_TYPE.get(key(rawEntity));
            if (entity == null) {
                return warn(tier, key, "unknown entity '" + rawEntity + "'");
            }
        }

        if (statistic.getType() != Statistic.Type.UNTYPED && material == null && entity == null) {
            return warn(tier, key, "statistic " + statistic + " needs a 'material' or 'entity'");
        }

        return new StatisticRequirement(key, display, statistic, material, entity, amount);
    }

    private static NamespacedKey key(String raw) {
        String cleaned = raw.toLowerCase(Locale.ROOT);
        return cleaned.indexOf(':') >= 0
                ? NamespacedKey.fromString(cleaned)
                : NamespacedKey.minecraft(cleaned);
    }

    private Requirement warn(int tier, String key, String reason) {
        plugin.getLogger().warning("tiers.yml -> tier " + tier + " -> requirement '"
                + key + "': " + reason + " (skipped)");
        return null;
    }
}
