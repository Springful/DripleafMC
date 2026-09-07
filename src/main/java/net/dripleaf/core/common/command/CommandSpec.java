package net.dripleaf.core.common.command;

import net.dripleaf.core.common.config.Cfg;

import java.util.List;

/**
 * One command's entry in {@code core/commands.yml}.
 *
 * <p>Every command in the core module is individually toggleable, and
 * <b>disabled is the default for anything EssentialsX also provides</b>. A
 * disabled command is never registered at all, so it cannot collide with
 * whatever is already running — staff enable them deliberately, one at a time,
 * after disabling the Essentials equivalent.
 *
 * @param id       config key and cooldown key, e.g. {@code spawn}
 * @param enabled  whether to register it at all
 * @param permission node required to run it
 * @param aliases  alternative names
 * @param cooldown seconds after a successful run
 * @param warmup   seconds before it executes
 */
public record CommandSpec(String id, boolean enabled, String permission, List<String> aliases,
                          long cooldown, double warmup, String description) {

    /** Defaults used when the file has no entry for this command. */
    public static CommandSpec defaults(String id, String permission, String description) {
        return new CommandSpec(id, false, permission, List.of(), 0L, 0d, description);
    }

    /**
     * Reads {@code commands.<id>}, falling back to {@code fallback} for anything
     * missing so a partially filled entry still works.
     */
    public static CommandSpec read(Cfg section, CommandSpec fallback) {
        if (section == null || !section.exists()) {
            return fallback;
        }
        List<String> aliases = section.stringList("aliases");
        return new CommandSpec(
                fallback.id(),
                section.bool("enabled", fallback.enabled()),
                section.string("permission", fallback.permission()),
                aliases.isEmpty() ? fallback.aliases() : List.copyOf(aliases),
                (long) section.number("cooldown", fallback.cooldown(), 0d, 86_400d),
                section.number("warmup", fallback.warmup(), 0d, 600d),
                section.string("description", fallback.description()));
    }
}
