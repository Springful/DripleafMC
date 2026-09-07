package net.dripleaf.core.common.text;

/**
 * Unicode progress bars, built from {@link Glyphs#BAR_FULL} and
 * {@link Glyphs#BAR_EMPTY}.
 *
 * <p>Used on requirement lines and on the warmup action bar. The strings are
 * cheap to build but they are rebuilt per render, so nothing here allocates
 * more than the one {@link StringBuilder}.
 */
public final class ProgressBar {

    private ProgressBar() {
    }

    /**
     * @param progress current value, clamped at zero
     * @param target   goal value; a non-positive target reads as complete
     * @param cells    bar width in characters
     * @return e.g. {@code ▰▰▰▰▰▰▱▱▱▱}, uncoloured
     */
    public static String bar(double progress, double target, int cells) {
        return bar(fraction(progress, target), cells);
    }

    /** @param fraction 0.0 to 1.0 */
    public static String bar(double fraction, int cells) {
        int width = Math.max(1, cells);
        int filled = (int) Math.round(clamp(fraction) * width);
        StringBuilder sb = new StringBuilder(width);
        for (int i = 0; i < width; i++) {
            sb.append(i < filled ? Glyphs.BAR_FULL : Glyphs.BAR_EMPTY);
        }
        return sb.toString();
    }

    /**
     * A two-tone coloured bar: filled cells in {@code filledHex}, the remainder
     * in {@link Palette#DEEP_MUTED}. Returned as MiniMessage source.
     */
    public static String coloured(double progress, double target, int cells, String filledHex) {
        int width = Math.max(1, cells);
        int filled = (int) Math.round(clamp(fraction(progress, target)) * width);
        StringBuilder sb = new StringBuilder(width + 32);
        sb.append('<').append(filledHex).append('>');
        sb.append(String.valueOf(Glyphs.BAR_FULL).repeat(filled));
        sb.append('<').append(Palette.DEEP_MUTED).append('>');
        sb.append(String.valueOf(Glyphs.BAR_EMPTY).repeat(width - filled));
        return sb.toString();
    }

    /** Whole percent, 0-100. */
    public static int percent(double progress, double target) {
        return (int) Math.round(clamp(fraction(progress, target)) * 100d);
    }

    private static double fraction(double progress, double target) {
        if (target <= 0d) {
            return 1d;
        }
        return progress / target;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || value < 0d) {
            return 0d;
        }
        return Math.min(1d, value);
    }
}
