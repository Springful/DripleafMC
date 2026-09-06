package net.dripleafmc.rebirth.config;

import net.dripleafmc.rebirth.RebirthPlugin;
import net.dripleafmc.rebirth.reward.RewardAction;
import net.dripleafmc.rebirth.tier.RebirthPath;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * rewards.yml: the only place in the plugin that knows what commands exist.
 * Sets are parsed into {@link RewardAction}s once, and resolved combinations
 * are memoised so a rebirth never re-parses a line.
 */
public final class RewardRegistry {

    private final Map<String, List<RewardAction>> sets = new HashMap<>(16);
    private final Map<String, List<RewardAction>> resolved = new HashMap<>(8);
    private final Map<RebirthPath, List<String>> broadcasts = new EnumMap<>(RebirthPath.class);

    private final List<RewardAction> sacrifice;
    private final boolean wipeBalance;

    public RewardRegistry(RebirthPlugin plugin) {
        YamlConfiguration yaml = ConfigFile.load(plugin, "rewards.yml");

        this.wipeBalance = yaml.getBoolean("sacrifice.wipe-balance", true);
        this.sacrifice = parseList(yaml.getStringList("sacrifice.commands"));

        ConfigurationSection setSection = yaml.getConfigurationSection("reward-sets");
        if (setSection != null) {
            for (String name : setSection.getKeys(false)) {
                sets.put(name, parseList(setSection.getStringList(name)));
            }
        }

        for (RebirthPath path : RebirthPath.values()) {
            broadcasts.put(path, List.copyOf(yaml.getStringList("broadcasts." + path.id())));
        }

        plugin.getLogger().info("Loaded " + sets.size() + " reward-sets from rewards.yml.");
    }

    private static List<RewardAction> parseList(List<String> raw) {
        List<RewardAction> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            RewardAction action = RewardAction.parse(line);
            if (action != null) {
                out.add(action);
            }
        }
        return List.copyOf(out);
    }

    /** Flattens a list of set names into one ordered action list, memoised. */
    public List<RewardAction> resolve(List<String> names) {
        if (names.isEmpty()) {
            return List.of();
        }
        String key = String.join("|", names);
        List<RewardAction> cached = resolved.get(key);
        if (cached != null) {
            return cached;
        }
        List<RewardAction> combined = new ArrayList<>(8);
        for (String name : names) {
            List<RewardAction> set = sets.get(name);
            if (set != null) {
                combined.addAll(set);
            }
        }
        List<RewardAction> immutable = List.copyOf(combined);
        resolved.put(key, immutable);
        return immutable;
    }

    public boolean has(String name) {
        return sets.containsKey(name);
    }

    public List<RewardAction> sacrifice() {
        return sacrifice;
    }

    public boolean wipeBalance() {
        return wipeBalance;
    }

    public List<String> broadcast(RebirthPath path) {
        return broadcasts.getOrDefault(path, List.of());
    }
}
