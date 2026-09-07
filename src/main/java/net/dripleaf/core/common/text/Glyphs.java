package net.dripleaf.core.common.text;

/**
 * The shared glyph set. Every surface uses these and only these, so a lore
 * block in the shop reads the same as one in the rebirth menu.
 *
 * <p>All are in the default Minecraft font — no resource pack is assumed.
 */
public final class Glyphs {

    /** Section header bullet. Always {@link Palette#STRUCTURE}. */
    public static final String HEADER = "■";
    /** Line item under a header. Always {@link Palette#STRUCTURE}. */
    public static final String ITEM = "➥";
    /** Requirement met. Always {@link Palette#SUCCESS}. */
    public static final String MET = "✓";
    /** Requirement unmet. Always {@link Palette#FAILURE}. */
    public static final String UNMET = "✗";
    /** Decorative, soul / ascension context. */
    public static final String SOUL = "✦";
    /** Call to action — "click to…". */
    public static final String ACTION = "↪";
    /** Warning, gold. */
    public static final String WARNING = "⚠";
    /** Separator inside broadcast lines. */
    public static final String SEPARATOR = "┃";

    /** Filled cell of a progress bar. */
    public static final char BAR_FULL = '▰';
    /** Empty cell of a progress bar. */
    public static final char BAR_EMPTY = '▱';

    private Glyphs() {
    }
}
