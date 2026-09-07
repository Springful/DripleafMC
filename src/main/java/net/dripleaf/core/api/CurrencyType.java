package net.dripleaf.core.api;

import java.util.Locale;

/** The three currencies the plugin knows about. */
public enum CurrencyType {

    /** Vault-backed, fractional. */
    MONEY("money", false, "$"),
    /** DripleafCore player data, integral. */
    SHARDS("shards", true, "◆ "),
    /** DripleafCore player data or an external command bridge, integral. */
    SOULS("souls", true, "✦ ");

    private final String id;
    private final boolean integral;
    private final String symbol;

    CurrencyType(String id, boolean integral, String symbol) {
        this.id = id;
        this.integral = integral;
        this.symbol = symbol;
    }

    public String id() {
        return id;
    }

    /** True when fractional amounts are a config/user error rather than a value. */
    public boolean integral() {
        return integral;
    }

    public String symbol() {
        return symbol;
    }

    public static CurrencyType from(String raw, CurrencyType fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "money", "cash", "vault" -> MONEY;
            case "shards", "shard" -> SHARDS;
            case "souls", "soul", "soul_token", "soul-token" -> SOULS;
            default -> fallback;
        };
    }
}
