package net.dripleaf.core.common.money;

import net.dripleaf.core.api.CurrencyType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * The one place a monetary amount enters or leaves the plugin.
 *
 * <p>Every command argument, every config value and every shop price goes
 * through here. {@code BigDecimal} internally; {@code double} only at the Vault
 * boundary.
 *
 * <table>
 *   <caption>Accepted input</caption>
 *   <tr><td>{@code 1000}, {@code 1,000}</td><td>1000</td></tr>
 *   <tr><td>{@code 1k}, {@code 1K}</td><td>1 000</td></tr>
 *   <tr><td>{@code 2.5k}</td><td>2 500</td></tr>
 *   <tr><td>{@code 1m} / {@code 1b} / {@code 1t}</td><td>10^6 / 10^9 / 10^12</td></tr>
 *   <tr><td>{@code all}</td><td>the player's full balance</td></tr>
 *   <tr><td>{@code half}</td><td>half the player's balance</td></tr>
 * </table>
 *
 * <p>Formatting is the inverse and lives here too. {@link #format} is for
 * scoreboards and lore; {@link #formatExact} is for anything a player is about
 * to spend money on. Never show an abbreviated figure on a confirmation screen.
 */
public final class AmountParser {

    /** Hard ceiling. Above this an amount is a typo or an exploit attempt, not a value. */
    public static final BigDecimal MAX = new BigDecimal("1E+15");

    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final String[] SUFFIXES = { "", "K", "M", "B", "T", "Q" };

    private final DecimalFormat exact;
    private final DecimalFormat compact;

    /**
     * @param exactPattern   {@link DecimalFormat} pattern for full figures, e.g. {@code #,##0.##}
     * @param compactPattern pattern for the abbreviated mantissa, e.g. {@code 0.##}
     */
    public AmountParser(String exactPattern, String compactPattern) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        this.exact = new DecimalFormat(exactPattern, symbols);
        this.compact = new DecimalFormat(compactPattern, symbols);
    }

    public AmountParser() {
        this("#,##0.##", "0.##");
    }

    // ------------------------------------------------------------- parsing

    /**
     * Parses an amount with no {@code all}/{@code half} support — for config
     * values and any context with no player balance to reference.
     */
    public ParseResult parse(String input) {
        return parse(input, null);
    }

    /**
     * @param balance the player's current balance, enabling {@code all} and
     *                {@code half}; {@code null} rejects both
     */
    public ParseResult parse(String input, BigDecimal balance) {
        if (input == null || input.isBlank()) {
            return ParseResult.fail("errors.amount-empty");
        }
        String cleaned = input.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "");
        if (cleaned.startsWith("$")) {
            cleaned = cleaned.substring(1);
        }

        if (cleaned.equals("all") || cleaned.equals("*")) {
            return balance == null
                    ? ParseResult.fail("errors.amount-invalid")
                    : validate(balance);
        }
        if (cleaned.equals("half")) {
            return balance == null
                    ? ParseResult.fail("errors.amount-invalid")
                    : validate(balance.divide(TWO, 2, RoundingMode.DOWN));
        }

        BigDecimal multiplier = BigDecimal.ONE;
        if (!cleaned.isEmpty()) {
            char last = cleaned.charAt(cleaned.length() - 1);
            int power = switch (last) {
                case 'k' -> 3;
                case 'm' -> 6;
                case 'b' -> 9;
                case 't' -> 12;
                case 'q' -> 15;
                default -> -1;
            };
            if (power >= 0) {
                multiplier = BigDecimal.TEN.pow(power);
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
        }
        if (cleaned.isEmpty()) {
            return ParseResult.fail("errors.amount-invalid");
        }

        BigDecimal parsed;
        try {
            parsed = new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            // Catches NaN, Infinity, stray letters and empty mantissas alike.
            return ParseResult.fail("errors.amount-invalid");
        }
        return validate(parsed.multiply(multiplier));
    }

    /**
     * Parses for an integral currency. A fractional result is rejected with a
     * message rather than silently truncated — {@code /souls give Steve 1.5}
     * should tell the sender it means nothing, not quietly hand over one soul.
     */
    public ParseResult parseIntegral(String input, BigDecimal balance) {
        ParseResult result = parse(input, balance);
        if (!result.ok()) {
            return result;
        }
        BigDecimal value = result.get();
        if (value.stripTrailingZeros().scale() > 0) {
            return ParseResult.fail("errors.amount-not-whole");
        }
        return ParseResult.ok(value.setScale(0, RoundingMode.UNNECESSARY));
    }

    public ParseResult parseFor(CurrencyType currency, String input, BigDecimal balance) {
        return currency.integral() ? parseIntegral(input, balance) : parse(input, balance);
    }

    private static ParseResult validate(BigDecimal value) {
        if (value.signum() < 0) {
            return ParseResult.fail("errors.amount-negative");
        }
        if (value.compareTo(MAX) > 0) {
            return ParseResult.fail("errors.amount-too-large");
        }
        return ParseResult.ok(value);
    }

    // ---------------------------------------------------------- formatting

    /** Abbreviated: {@code 1.25B}. Scoreboards and lore only. */
    public synchronized String format(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal magnitude = value.abs();
        if (magnitude.compareTo(BigDecimal.valueOf(1000)) < 0) {
            return compact.format(value);
        }
        int index = 0;
        BigDecimal scaled = value;
        BigDecimal thousand = BigDecimal.valueOf(1000);
        while (scaled.abs().compareTo(thousand) >= 0 && index < SUFFIXES.length - 1) {
            scaled = scaled.divide(thousand, 4, RoundingMode.HALF_UP);
            index++;
        }
        return compact.format(scaled) + SUFFIXES[index];
    }

    /** Full figure with separators: {@code 1,250,000,000}. Confirmations and receipts. */
    public synchronized String formatExact(BigDecimal value) {
        return value == null ? "0" : exact.format(value);
    }

    public String format(CurrencyType currency, BigDecimal value) {
        return currency.symbol() + format(value);
    }

    public String formatExact(CurrencyType currency, BigDecimal value) {
        return currency.symbol() + formatExact(value);
    }

    /** Whole-number display for statistic progress, kill counts and the like. */
    public synchronized String formatWhole(double value) {
        return exact.format(BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP));
    }

    public static double toDouble(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }

    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value);
    }
}
