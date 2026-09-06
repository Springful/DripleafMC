package net.dripleafmc.core.econ;

import net.dripleafmc.core.profile.Profile;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

/**
 * Vault bridge. DripleafCore never stores balances itself — ExcellentEconomy stays
 * the source of truth, so soul tokens and coins keep living in one place.
 */
public final class Money {

    private Economy economy;
    private final Logger log;

    public Money(Logger log) {
        this.log = log;
    }

    public boolean hook() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            log.warning("[DripleafCore] Vault not found — shop, sell and bounties are disabled.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            log.warning("[DripleafCore] No Vault economy provider registered.");
            return false;
        }
        economy = rsp.getProvider();
        log.info("[DripleafCore] economy provider: " + economy.getName());
        return true;
    }

    public boolean ready() {
        return economy != null;
    }

    public double balance(OfflinePlayer player) {
        return economy == null ? 0 : economy.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        return economy != null && economy.has(player, amount);
    }

    /** Returns false when the withdrawal failed; callers must not hand out goods first. */
    public boolean take(OfflinePlayer player, double amount, Profile profile) {
        if (economy == null || amount <= 0) return false;
        if (!economy.has(player, amount)) return false;
        if (!economy.withdrawPlayer(player, amount).transactionSuccess()) return false;
        if (profile != null) {
            profile.moneySpent += amount;
            profile.touch();
        }
        return true;
    }

    public void give(OfflinePlayer player, double amount, Profile profile) {
        if (economy == null || amount <= 0) return;
        economy.depositPlayer(player, amount);
        if (profile != null) {
            profile.moneyMade += amount;
            profile.touch();
        }
    }
}
