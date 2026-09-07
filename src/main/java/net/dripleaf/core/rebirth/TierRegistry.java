package net.dripleaf.core.rebirth;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.config.Cfg;
import net.dripleaf.core.common.config.ValidationLog;
import net.dripleaf.core.common.icon.IconService;
import net.dripleaf.core.common.money.ParseResult;
import net.dripleaf.core.rebirth.requirement.Requirement;
import net.dripleaf.core.rebirth.requirement.RequirementFactory;
import org.bukkit.Material;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code rebirth/tiers.yml}, parsed once into immutable records.
 *
 * <p>This is the single most valuable change from the Skript version: tier
 * count and tier content are pure config. Staff add tier 26 by copying a YAML
 * block, and nothing in Java needs to know.
 *
 * <p>Tiers must be consecutive from 1. A gap is a loud warning rather than a
 * silent hole, because a gap means a player can reach a tier they can never
 * leave.
 */
public final class TierRegistry {

    private static final String FILE = "rebirth/tiers.yml";

    private final Services services;
    private final RequirementFactory factory;
    private final Map<Integer, RebirthTier> tiers = new TreeMap<>();

    public TierRegistry(Services services, RequirementFactory factory) {
        this.services = services;
        this.factory = factory;
    }

    public void load() {
        tiers.clear();
        services.configs().load(FILE);
        ValidationLog log = services.configs().log();
        Cfg root = services.configs().view(FILE, "tiers");

        for (String key : root.keys()) {
            int number;
            try {
                number = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                log.add(FILE, "tiers." + key, "tier keys must be whole numbers, ignored");
                continue;
            }
            Cfg node = root.child(key);
            if (node == null) {
                continue;
            }

            BigDecimal cost = amount(node.string("cost", "0"), "tiers." + key + ".cost", log);
            if (cost == null) {
                continue;
            }
            Cfg rewards = node.childOrEmpty("rewards");
            BigDecimal cash = amount(rewards.string("cash", "0"),
                    "tiers." + key + ".rewards.cash", log);

            List<Requirement> requirements = factory.build(node.child("requirements"), FILE,
                    "tiers." + key + ".requirements", log);

            Material icon = Material.LIME_DYE;
            for (Requirement requirement : requirements) {
                if (requirement.icon() != null) {
                    icon = requirement.icon();
                    break;
                }
            }
            icon = IconService.material(node.string("icon", ""), icon);

            tiers.put(number, new RebirthTier(
                    number,
                    node.string("numeral", String.valueOf(number)),
                    cost,
                    node.number("soul-cost-multiplier", 2d, 0d, 1000d),
                    List.copyOf(requirements),
                    cash == null ? BigDecimal.ZERO : cash,
                    rewards.integer("keys", 0, 0, 1000),
                    rewards.number("multiplier", 0d, 0d, 100_000d),
                    rewards.integer("souls", 0, 0, 100_000),
                    List.copyOf(node.stringList("unlocks")),
                    List.copyOf(node.stringList("commands")),
                    List.copyOf(node.stringList("soul-commands")),
                    icon));
        }

        verifyConsecutive(log);
    }

    private BigDecimal amount(String raw, String path, ValidationLog log) {
        ParseResult result = services.amounts().parse(raw);
        if (!result.ok()) {
            log.add(FILE, path, '"' + raw + "\" is not a valid amount, tier skipped");
            return null;
        }
        return result.get();
    }

    private void verifyConsecutive(ValidationLog log) {
        int expected = 1;
        for (Integer number : tiers.keySet()) {
            if (number != expected) {
                log.add(FILE, "tiers",
                        "tiers must be consecutive from 1; expected " + expected
                                + " but found " + number);
                break;
            }
            expected++;
        }
    }

    /** {@code null} for a tier that does not exist. */
    public RebirthTier get(int number) {
        return tiers.get(number);
    }

    public int max() {
        return tiers.isEmpty() ? 0 : tiers.keySet().stream().max(Integer::compareTo).orElse(0);
    }

    public List<RebirthTier> all() {
        return new ArrayList<>(tiers.values());
    }

    public int size() {
        return tiers.size();
    }
}
