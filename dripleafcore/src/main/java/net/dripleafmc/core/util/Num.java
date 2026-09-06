package net.dripleafmc.core.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Number formatting and parsing. Formatters are not thread-safe, so they are thread-local. */
public final class Num {

    private static final ThreadLocal<DecimalFormat> PLAIN =
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)));
    private static final ThreadLocal<DecimalFormat> SHORT =
            ThreadLocal.withInitial(() -> new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US)));

    private static final String[] SUFFIX = {"", "K", "M", "B", "T", "Q"};

    private Num() {}

    public static String money(double v) {
        return PLAIN.get().format(v);
    }

    /** 1234567 -> "1.23M". Used anywhere space is tight (action bars, tab list, buttons). */
    public static String compact(double v) {
        boolean neg = v < 0;
        v = Math.abs(v);
        int i = 0;
        while (v >= 1000 && i < SUFFIX.length - 1) {
            v /= 1000;
            i++;
        }
        return (neg ? "-" : "") + SHORT.get().format(v) + SUFFIX[i];
    }

    /** Accepts "1000", "1k", "2.5m", "1,000". Returns -1 when unparseable. */
    public static double parse(String input) {
        if (input == null || input.isEmpty()) return -1;
        String s = input.trim().replace(",", "").replace("$", "").toLowerCase(Locale.ROOT);
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        switch (last) {
            case 'k' -> mult = 1_000d;
            case 'm' -> mult = 1_000_000d;
            case 'b' -> mult = 1_000_000_000d;
            case 't' -> mult = 1_000_000_000_000d;
            default -> { }
        }
        if (mult != 1) s = s.substring(0, s.length() - 1);
        try {
            double v = Double.parseDouble(s) * mult;
            return (Double.isNaN(v) || Double.isInfinite(v) || v < 0) ? -1 : v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 3725 -> "1h 2m 5s". */
    public static String duration(long seconds) {
        if (seconds <= 0) return "0s";
        long d = seconds / 86400, h = (seconds % 86400) / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        StringBuilder sb = new StringBuilder(16);
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.isEmpty()) sb.append(s).append('s');
        return sb.toString().trim();
    }
}
