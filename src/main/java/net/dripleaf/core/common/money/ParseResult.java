package net.dripleaf.core.common.money;

import java.math.BigDecimal;

/**
 * The outcome of parsing a player-supplied amount.
 *
 * <p>A failure carries a {@code messages.yml} key rather than a sentence, so
 * the wording stays in config with everything else.
 *
 * @param value     the parsed amount, or {@code null} on failure
 * @param errorKey  {@code messages.yml} key describing the failure, or {@code null}
 */
public record ParseResult(BigDecimal value, String errorKey) {

    public static ParseResult ok(BigDecimal value) {
        return new ParseResult(value, null);
    }

    public static ParseResult fail(String errorKey) {
        return new ParseResult(null, errorKey);
    }

    public boolean ok() {
        return value != null;
    }

    /** Only valid when {@link #ok()}. */
    public BigDecimal get() {
        return value;
    }
}
