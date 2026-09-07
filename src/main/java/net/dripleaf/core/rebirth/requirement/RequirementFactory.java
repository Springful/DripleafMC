package net.dripleaf.core.rebirth.requirement;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.config.Cfg;
import net.dripleaf.core.common.config.ValidationLog;
import net.dripleaf.core.common.icon.IconService;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a tier's {@code requirements:} block into {@link Requirement}s.
 *
 * <p>Every failure is a logged line and a skipped requirement, never an
 * exception: a typo in one tier must not stop the other twenty-four from
 * loading.
 */
public final class RequirementFactory {

    private final Services services;

    public RequirementFactory(Services services) {
        this.services = services;
    }

    public List<Requirement> build(Cfg node, String file, String path, ValidationLog log) {
        List<Requirement> out = new ArrayList<>(4);
        if (node == null || !node.exists()) {
            return out;
        }

        for (String key : node.keys()) {
            String type = key.toLowerCase(Locale.ROOT);
            switch (type) {
                case "mcmmo-power", "mcmmo" -> {
                    int amount = node.integer(key, 0, 0, 1_000_000);
                    if (amount > 0) {
                        out.add(new McMmoRequirement(services.hooks().mcmmo(), amount,
                                services.messages().raw("rebirth.requirement-mcmmo")));
                    }
                }
                case "playtime", "playtime-hours" -> {
                    double hours = node.number(key, 0d, 0d, 100_000d);
                    if (hours > 0) {
                        out.add(new PlaytimeRequirement(services.players(), hours,
                                services.messages().raw("rebirth.requirement-playtime")));
                    }
                }
                case "permission" -> {
                    String value = node.string(key, "");
                    if (!value.isBlank()) {
                        out.add(new PermissionRequirement(value, value));
                    }
                }
                case "stat", "statistic" -> {
                    Requirement requirement = stat(node.child(key), file,
                            path + '.' + key, log);
                    if (requirement != null) {
                        out.add(requirement);
                    }
                }
                default -> log.add(file, path + '.' + key,
                        "unknown requirement type, ignored");
            }
        }
        return out;
    }

    private Requirement stat(Cfg node, String file, String path, ValidationLog log) {
        if (node == null) {
            return null;
        }
        String rawType = node.string("type", "");
        Statistic statistic;
        try {
            statistic = Statistic.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.add(file, path + ".type", '"' + rawType
                    + "\" is not a Bukkit Statistic, requirement skipped");
            return null;
        }

        double amount = node.number("amount", 0d, 0d, Double.MAX_VALUE);
        if (amount <= 0d) {
            log.add(file, path + ".amount", "must be greater than zero, requirement skipped");
            return null;
        }

        String display = node.string("display", rawType);
        Material material = null;
        EntityType entity = null;

        // Bukkit statistics come in three shapes; the qualifier decides which.
        switch (statistic.getType()) {
            case BLOCK, ITEM -> {
                material = IconService.material(node.string("material", ""));
                if (material == null) {
                    log.add(file, path + ".material",
                            "this statistic needs a material, requirement skipped");
                    return null;
                }
            }
            case ENTITY -> {
                String rawEntity = node.string("entity", node.string("material", ""));
                try {
                    entity = EntityType.valueOf(rawEntity.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    log.add(file, path + ".entity", '"' + rawEntity
                            + "\" is not an EntityType, requirement skipped");
                    return null;
                }
            }
            default -> {
                // UNTYPED — no qualifier, e.g. FISH_CAUGHT.
            }
        }
        return new StatisticRequirement(statistic, material, entity, amount, display);
    }
}
