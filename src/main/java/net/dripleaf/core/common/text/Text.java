package net.dripleaf.core.common.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * The single shared MiniMessage instance.
 *
 * <p>Constructing a {@link MiniMessage} is not free and there is no reason to
 * hold more than one. No legacy {@code §} codes and no {@code ChatColor}
 * anywhere in this plugin.
 */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component parse(String raw, TagResolver resolver) {
        return MM.deserialize(raw == null ? "" : raw, resolver);
    }

    public static Component parse(String raw) {
        return MM.deserialize(raw == null ? "" : raw);
    }

    /**
     * Item names and lore. Identical to {@link #parse} except it clears the
     * vanilla italic that Minecraft applies to every custom item name.
     */
    public static Component item(String raw, TagResolver resolver) {
        return parse(raw, resolver)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Component item(String raw) {
        return item(raw, TagResolver.empty());
    }

    /** Strips tags for log lines and console output. */
    public static String plain(String raw) {
        return raw == null ? "" : MM.stripTags(raw);
    }

    public static MiniMessage mm() {
        return MM;
    }
}
