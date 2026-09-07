package net.dripleaf.core.common.text;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code messages.yml}, flattened.
 *
 * <p>Every player-visible string in the plugin comes from here — there are no
 * string literals in command classes. That is what lets staff retheme the
 * plugin without a rebuild.
 *
 * <p>The file is flattened into one map at load, so sending a message is a
 * hash lookup plus a single MiniMessage parse.
 */
public final class MessageService {

    private final Map<String, String> single = new HashMap<>(256);
    private final Map<String, List<String>> lists = new HashMap<>(32);
    private String prefix = "";

    /** Replaces every string in place. Callers hold the service, not its data. */
    public void load(YamlConfiguration yaml) {
        single.clear();
        lists.clear();
        this.prefix = yaml.getString("prefix", "");
        flatten(yaml, "");
    }

    private void flatten(ConfigurationSection section, String path) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            String full = path.isEmpty() ? key : path + '.' + key;
            if (value instanceof ConfigurationSection child) {
                flatten(child, full);
            } else if (value instanceof String string) {
                single.put(full, string);
            } else if (value instanceof List<?> list) {
                List<String> lines = new ArrayList<>(list.size());
                for (Object element : list) {
                    lines.add(String.valueOf(element));
                }
                lists.put(full, List.copyOf(lines));
            }
        }
    }

    /** The raw template, or the key itself when missing — a visible, greppable failure. */
    public String raw(String key) {
        String value = single.get(key);
        return value == null ? key : value;
    }

    public boolean has(String key) {
        return single.containsKey(key) || lists.containsKey(key);
    }

    public List<String> rawList(String key) {
        return lists.getOrDefault(key, List.of());
    }

    public String prefix() {
        return prefix;
    }

    /** Prefixed, resolved component. */
    public Component get(String key, Ctx ctx) {
        return Text.parse(prefix + raw(key), ctx.resolver());
    }

    /** Resolved component with no prefix — for lore, dialog bodies and item names. */
    public Component bare(String key, Ctx ctx) {
        return Text.item(raw(key), ctx.resolver());
    }

    public List<Component> block(String key, Ctx ctx) {
        List<String> raw = rawList(key);
        List<Component> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(Text.item(line, ctx.resolver()));
        }
        return out;
    }

    public void send(CommandSender target, String key, Ctx ctx) {
        String raw = raw(key);
        if (raw.isEmpty()) {
            return;
        }
        target.sendMessage(Text.parse(prefix + raw, ctx.resolver()));
    }

    public void send(CommandSender target, String key) {
        send(target, key, new Ctx());
    }

    /** Sends a {@code key} that holds a list, one chat line per entry, unprefixed. */
    public void sendBlock(CommandSender target, String key, Ctx ctx) {
        for (String line : rawList(key)) {
            target.sendMessage(Text.parse(line, ctx.resolver()));
        }
    }
}
