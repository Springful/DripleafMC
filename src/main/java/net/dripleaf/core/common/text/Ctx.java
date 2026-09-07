package net.dripleaf.core.common.text;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A render context — a small bag of {@code <key>} to value pairs.
 *
 * <p>Built once per screen and reused for every line in it, so a forty-line
 * lore block costs one resolver construction rather than forty string passes.
 * Resolution goes through MiniMessage's {@link TagResolver}, never string
 * concatenation, so a player whose name contains a {@code <} cannot inject
 * formatting into a message.
 */
public final class Ctx {

    private final Map<String, String> values = new LinkedHashMap<>(16);
    private TagResolver cached;

    public static Ctx of(String key, String value) {
        return new Ctx().put(key, value);
    }

    public Ctx put(String key, String value) {
        values.put(key, value == null ? "" : value);
        cached = null;
        return this;
    }

    public Ctx put(String key, Number value) {
        return put(key, String.valueOf(value));
    }

    public Ctx put(String key, boolean value) {
        return put(key, String.valueOf(value));
    }

    /** Copies every entry of {@code other} over this context. */
    public Ctx putAll(Ctx other) {
        if (other != null) {
            values.putAll(other.values);
            cached = null;
        }
        return this;
    }

    public String get(String key) {
        return values.getOrDefault(key, "");
    }

    public Map<String, String> values() {
        return values;
    }

    /** MiniMessage resolver for chat, lore and dialog rendering. */
    public TagResolver resolver() {
        TagResolver local = cached;
        if (local == null) {
            TagResolver.Builder builder = TagResolver.builder();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                builder.resolver(Placeholder.parsed(entry.getKey(), entry.getValue()));
            }
            local = builder.build();
            cached = local;
        }
        return local;
    }

    /**
     * Plain {@code <key>} substitution for strings that are not MiniMessage —
     * console commands, log lines, and anything else that never reaches a chat
     * serialiser.
     */
    public String applyRaw(String input) {
        if (input == null || input.indexOf('<') < 0) {
            return input;
        }
        String out = input;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            out = out.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return out;
    }
}
