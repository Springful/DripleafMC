package net.dripleaf.core.common.hook;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Vault, or nothing.
 *
 * <p>Money is the one economy DripleafCore never owns — it reads and writes
 * exclusively through this bridge. With no provider present {@link #available()}
 * is false and every money-priced feature refuses cleanly rather than handing
 * out free goods.
 */
public final class VaultBridge implements Bridge {

    private Economy economy;

    public void connect() {
        this.economy = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> provider =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider != null) {
            this.economy = provider.getProvider();
        }
    }

    @Override
    public String name() {
        return "Vault";
    }

    @Override
    public boolean available() {
        return economy != null;
    }

    @Override
    public String detail() {
        return economy == null ? "no economy provider" : economy.getName();
    }

    public BigDecimal balance(OfflinePlayer player) {
        if (economy == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(economy.getBalance(player));
    }

    public boolean has(OfflinePlayer player, BigDecimal amount) {
        return economy != null && economy.has(player, amount.doubleValue());
    }

    public boolean withdraw(OfflinePlayer player, BigDecimal amount) {
        if (economy == null || amount.signum() <= 0) {
            return economy != null;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount.doubleValue());
        return response != null && response.transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, BigDecimal amount) {
        if (economy == null || amount.signum() <= 0) {
            return economy != null;
        }
        EconomyResponse response = economy.depositPlayer(player, amount.doubleValue());
        return response != null && response.transactionSuccess();
    }

    /** Vault has no setter, so this is a read followed by the difference. */
    public boolean set(OfflinePlayer player, BigDecimal amount) {
        if (economy == null) {
            return false;
        }
        BigDecimal current = balance(player);
        BigDecimal delta = amount.subtract(current).setScale(2, RoundingMode.HALF_UP);
        if (delta.signum() == 0) {
            return true;
        }
        return delta.signum() > 0 ? deposit(player, delta) : withdraw(player, delta.negate());
    }
}
