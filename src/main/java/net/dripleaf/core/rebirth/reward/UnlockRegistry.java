package net.dripleaf.core.rebirth.reward;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.config.Cfg;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** {@code rebirth/unlocks.yml}: what each tier grants, and the commands that grant it. */
public final class UnlockRegistry {

    private static final String FILE = "rebirth/unlocks.yml";

    private final Services services;
    private final Map<Integer, List<Unlock>> byTier = new TreeMap<>();

    public UnlockRegistry(Services services) {
        this.services = services;
    }

    @SuppressWarnings("unchecked")
    public void load() {
        byTier.clear();
        services.configs().load(FILE);
        Cfg root = services.configs().view(FILE, "unlocks");

        for (String key : root.keys()) {
            int tier;
            try {
                tier = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                services.configs().log().add(FILE, "unlocks." + key,
                        "tier keys must be whole numbers, ignored");
                continue;
            }
            ConfigurationSection section = root.raw();
            if (section == null) {
                continue;
            }
            List<Unlock> unlocks = new ArrayList<>(4);
            for (Object entry : section.getList(key, List.of())) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                Object display = map.get("display");
                Object commands = map.get("commands");
                List<String> commandList = new ArrayList<>(2);
                if (commands instanceof List<?> list) {
                    for (Object command : list) {
                        commandList.add(String.valueOf(command));
                    }
                }
                unlocks.add(new Unlock(display == null ? "" : String.valueOf(display),
                        List.copyOf(commandList)));
            }
            byTier.put(tier, List.copyOf(unlocks));
        }
    }

    public List<Unlock> forTier(int tier) {
        return byTier.getOrDefault(tier, List.of());
    }

    /** Display strings only, for menus. */
    public List<String> displays(int tier) {
        List<Unlock> unlocks = forTier(tier);
        List<String> out = new ArrayList<>(unlocks.size());
        for (Unlock unlock : unlocks) {
            if (!unlock.display().isBlank()) {
                out.add(unlock.display());
            }
        }
        return out;
    }

    public int size() {
        return byTier.size();
    }
}
