package net.dripleafmc.rebirth.config;

import net.dripleafmc.rebirth.RebirthPlugin;
import net.dripleafmc.rebirth.util.Ctx;
import net.dripleafmc.rebirth.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * lang.yml. Every string is flattened into one map at load, so sending a
 * message is a hash lookup plus a single MiniMessage parse.
 */
public final class Lang {

    private final Map<String, String> messages = new HashMap<>(64);
    private String prefix = "";

    public Lang(RebirthPlugin plugin) {
        YamlConfiguration yaml = ConfigFile.load(plugin, "lang.yml");
        this.prefix = yaml.getString("prefix", "");
        for (String key : yaml.getKeys(true)) {
            Object value = yaml.get(key);
            if (value instanceof String string) {
                messages.put(key, string);
            }
        }
    }

    public String raw(String key) {
        return messages.getOrDefault(key, key);
    }

    public Component component(String key, Ctx ctx) {
        return Text.parse(prefix + raw(key), ctx.resolver());
    }

    public Component bare(String key, Ctx ctx) {
        return Text.parse(raw(key), ctx.resolver());
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
}
