package net.dripleafmc.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.format.TextDecoration;

/** MiniMessage helpers. Deserialisation is not free, so callers should cache static components. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {}

    public static Component mm(String raw) {
        return MM.deserialize(raw).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Component mm(String raw, TagResolver... resolvers) {
        return MM.deserialize(raw, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static TagResolver p(String key, String value) {
        return Placeholder.unparsed(key, value);
    }

    public static TagResolver p(String key, long value) {
        return Placeholder.unparsed(key, Long.toString(value));
    }

    public static TagResolver c(String key, Component value) {
        return Placeholder.component(key, value);
    }

    /** Title-cases a MATERIAL_NAME into "Material Name". Cheap enough for menu building. */
    public static String pretty(Enum<?> e) {
        String s = e.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder(s.length());
        boolean up = true;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            sb.append(up ? Character.toUpperCase(ch) : ch);
            up = ch == ' ';
        }
        return sb.toString();
    }
}
