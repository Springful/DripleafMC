package net.dripleafmc.core.config;

import net.dripleafmc.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * messages.yml, flattened once at load into a plain map so lookups are a single
 * hash probe instead of a YAML path walk on every message.
 */
public final class Lang {

    private final Map<String, String> raw = new HashMap<>(128);
    private Component prefix = Component.empty();

    public void load(File file) {
        raw.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        flatten(yml, "");
        prefix = Text.mm(raw.getOrDefault("prefix", ""));
    }

    private void flatten(ConfigurationSection section, String path) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            String full = path.isEmpty() ? key : path + '.' + key;
            if (value instanceof ConfigurationSection child) {
                flatten(child, full);
            } else if (value != null) {
                raw.put(full, String.valueOf(value));
            }
        }
    }

    public String rawOr(String key, String fallback) {
        return raw.getOrDefault(key, fallback);
    }

    public Component get(String key, TagResolver... resolvers) {
        String s = raw.get(key);
        return s == null ? Component.text(key) : Text.mm(s, resolvers);
    }

    public Component prefixed(String key, TagResolver... resolvers) {
        return prefix.append(get(key, resolvers));
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        to.sendMessage(prefixed(key, resolvers));
    }

    /** No prefix — for action bars and menu text. */
    public void sendPlain(CommandSender to, String key, TagResolver... resolvers) {
        to.sendMessage(get(key, resolvers));
    }
}
