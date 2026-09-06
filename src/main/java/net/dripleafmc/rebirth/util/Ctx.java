package net.dripleafmc.rebirth.util;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A render context: a small bag of {@code <key>} to value pairs.
 * <p>
 * Built once per menu/dialog open and reused for every line, so a 40-line
 * lore block costs one resolver construction rather than forty string passes.
 */
public final class Ctx {

    private final Map<String, String> values = new LinkedHashMap<>(16);
    private TagResolver cached;

    public Ctx put(String key, String value) {
        values.put(key, value);
        cached = null;
        return this;
    }

    public Ctx put(String key, Number value) {
        return put(key, String.valueOf(value));
    }

    public String get(String key) {
        return values.getOrDefault(key, "");
    }

    public Map<String, String> values() {
        return values;
    }

    /** MiniMessage resolver for chat/lore/dialog rendering. */
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
     * Plain {@code <key>} substitution for strings that are NOT MiniMessage,
     * i.e. commands dispatched to the console.
     */
    public String applyRaw(String input) {
        if (input.indexOf('<') < 0) {
            return input;
        }
        String out = input;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            out = out.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return out;
    }
}
