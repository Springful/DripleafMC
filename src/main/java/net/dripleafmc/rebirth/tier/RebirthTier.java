package net.dripleafmc.rebirth.tier;

import net.dripleafmc.rebirth.requirement.Requirement;

import java.util.List;
import java.util.Map;

/**
 * One immutable rung of the ladder, fully resolved at load time.
 * Nothing here is parsed again while the server runs.
 *
 * @param level        1-based tier number
 * @param roman        display numeral from tiers.yml
 * @param requirements pre-compiled requirement objects
 * @param values       reward values (cash, keys, multiplier, souls, custom)
 * @param unlocks      cosmetic "what this unlocks" lines
 * @param paths        per-path cost + reward-set references
 */
public record RebirthTier(int level,
                          String roman,
                          List<Requirement> requirements,
                          Map<String, Double> values,
                          List<String> unlocks,
                          Map<RebirthPath, PathSpec> paths) {

    public PathSpec path(RebirthPath path) {
        return paths.get(path);
    }

    public double cost(RebirthPath path) {
        PathSpec spec = paths.get(path);
        return spec == null ? 0d : spec.cost();
    }

    public double value(String key) {
        return values.getOrDefault(key, 0d);
    }
}
