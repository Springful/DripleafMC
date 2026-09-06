package net.dripleafmc.rebirth.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Single shared MiniMessage instance. Never construct a new one per render. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component parse(String raw, TagResolver resolver) {
        return MM.deserialize(raw, resolver);
    }

    public static Component parse(String raw) {
        return MM.deserialize(raw);
    }

    /** Item names/lore: kills the vanilla italic without touching anything else. */
    public static Component item(String raw, TagResolver resolver) {
        return MM.deserialize(raw, resolver)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String plain(String raw) {
        return MM.stripTags(raw);
    }
}
