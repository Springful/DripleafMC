package net.dripleafmc.rebirth.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Money and progress formatting. DecimalFormat is not thread-safe, hence the guarded access. */
public final class Numbers {

    private static final String[] SUFFIX = { "", "K", "M", "B", "T", "Q" };

    private final DecimalFormat plain;
    private final DecimalFormat compact;
    private final boolean useCompact;

    public Numbers(String pattern, boolean useCompact) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        this.plain = new DecimalFormat(pattern, symbols);
        this.compact = new DecimalFormat("0.##", symbols);
        this.useCompact = useCompact;
    }

    /** Full form, e.g. 48,750,000. */
    public synchronized String full(double value) {
        return plain.format(value);
    }

    /** Short form when enabled, e.g. 48.75M. Falls back to full form otherwise. */
    public synchronized String display(double value) {
        if (!useCompact || Math.abs(value) < 1000d) {
            return plain.format(value);
        }
        int index = 0;
        double scaled = value;
        while (Math.abs(scaled) >= 1000d && index < SUFFIX.length - 1) {
            scaled /= 1000d;
            index++;
        }
        return compact.format(scaled) + SUFFIX[index];
    }

    /** Requirement progress: whole numbers stay whole. */
    public synchronized String progress(double value) {
        return value == Math.rint(value) ? plain.format(value) : compact.format(value);
    }

    public static String roman(int value) {
        if (value <= 0) {
            return String.valueOf(value);
        }
        int[] nums = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
        String[] lit = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < nums.length; i++) {
            while (value >= nums[i]) {
                value -= nums[i];
                sb.append(lit[i]);
            }
        }
        return sb.toString();
    }
}
