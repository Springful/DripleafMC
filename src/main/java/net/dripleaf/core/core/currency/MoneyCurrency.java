package net.dripleaf.core.core.currency;

import net.dripleaf.core.api.CurrencyService;
import net.dripleaf.core.api.CurrencyType;
import net.dripleaf.core.common.hook.VaultBridge;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;

/**
 * Money, on Vault.
 *
 * <p>DripleafCore never writes to the money economy directly. Whatever provider
 * is registered owns the balances; this is a thin, honest adapter over it.
 */
public final class MoneyCurrency implements CurrencyService {

    private final VaultBridge vault;

    public MoneyCurrency(VaultBridge vault) {
        this.vault = vault;
    }

    @Override
    public CurrencyType type() {
        return CurrencyType.MONEY;
    }

    @Override
    public BigDecimal balance(OfflinePlayer player) {
        return vault.balance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, BigDecimal amount) {
        return vault.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, BigDecimal amount) {
        return vault.withdraw(player, amount);
    }

    @Override
    public boolean deposit(OfflinePlayer player, BigDecimal amount) {
        return vault.deposit(player, amount);
    }

    @Override
    public boolean set(OfflinePlayer player, BigDecimal amount) {
        return vault.set(player, amount);
    }

    @Override
    public boolean available() {
        return vault.available();
    }
}
