package net.dripleaf.core.common.text;

/**
 * The Dripleaf colour vocabulary, in one place.
 *
 * <p>These are the same values documented in {@code docs/CORE.md} and used by
 * every shipped yml. Java-built components use the constants; config authors
 * write the equivalent MiniMessage. Changing a colour here and in
 * {@code messages.yml} rethemes the whole plugin.
 */
public final class Palette {

    /** Dripleaf brand gradient, light end. */
    public static final String BRAND_FROM = "#dce35b";
    /** Dripleaf brand gradient, deep end. */
    public static final String BRAND_TO = "#45b649";

    /** Soul / ascension gradient. */
    public static final String SOUL_FROM = "#AA00FF";
    public static final String SOUL_TO = "#FF55FF";

    /** Shard gradient. */
    public static final String SHARD_FROM = "#00CFFF";
    public static final String SHARD_TO = "#7FE9FF";

    public static final String SUCCESS = "#55FF55";
    public static final String FAILURE = "#FF5555";
    public static final String DANGER = "#FF3333";
    public static final String DANGER_DEEP = "#800000";

    public static final String BODY = "#DDDDDD";
    public static final String MUTED = "#AAAAAA";
    /** Structural glyphs, separators and rules. */
    public static final String STRUCTURE = "#555555";
    /** Parenthetical asides that should almost disappear. */
    public static final String DEEP_MUTED = "#333333";

    public static final String WARNING = "#FFAA00";

    private Palette() {
    }

    /** Wraps {@code text} in the brand gradient. */
    public static String brand(String text) {
        return gradient(BRAND_FROM, BRAND_TO, text);
    }

    public static String soul(String text) {
        return gradient(SOUL_FROM, SOUL_TO, text);
    }

    public static String shard(String text) {
        return gradient(SHARD_FROM, SHARD_TO, text);
    }

    public static String gradient(String from, String to, String text) {
        return "<gradient:" + from + ':' + to + '>' + text + "</gradient>";
    }

    /** {@code <#RRGGBB>text</#RRGGBB>} — a flat colour span. */
    public static String colour(String hex, String text) {
        return '<' + hex + '>' + text + "</" + hex + '>';
    }
}
