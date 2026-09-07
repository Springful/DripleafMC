package net.dripleaf.core.api;

import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;

/**
 * One currency, one implementation.
 *
 * <p>Money is Vault. Shards and souls are either DripleafCore player data
 * ({@code internal} mode) or an external plugin driven by console commands and
 * read through a placeholder ({@code command} mode). Callers never care which.
 *
 * <p>All methods are main-thread only unless stated otherwise. Balances are
 * {@link BigDecimal} internally and only narrowed to {@code double} at the
 * Vault boundary.
 */
public interface CurrencyService {

    CurrencyType type();

    /** Current balance, or {@link BigDecimal#ZERO} when unknown. */
    BigDecimal balance(OfflinePlayer player);

    boolean has(OfflinePlayer player, BigDecimal amount);

    /** @return true when the full amount was taken; false leaves the balance untouched. */
    boolean withdraw(OfflinePlayer player, BigDecimal amount);

    /** @return true when the deposit landed. */
    boolean deposit(OfflinePlayer player, BigDecimal amount);

    /** @return true when the balance was overwritten. */
    boolean set(OfflinePlayer player, BigDecimal amount);

    /**
     * True when this currency can be read and written right now. A shard shop
     * on a server with no provider should refuse to open rather than silently
     * hand out free items.
     */
    boolean available();
}
