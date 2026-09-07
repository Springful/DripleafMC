package net.dripleaf.core.core.currency;

import net.dripleaf.core.api.CurrencyService;
import net.dripleaf.core.api.CurrencyType;
import net.dripleaf.core.common.hook.PapiBridge;
import net.dripleaf.core.common.text.Ctx;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;

/**
 * A currency owned by another plugin, driven by console commands.
 *
 * <p>This exists because souls are currently granted by
 * {@code souls give <player> <amount>} and there may be real balances in that
 * system already. Migrating player currency without an explicit decision is not
 * something a plugin should do on its own, so {@code command} mode is the
 * shipped default and {@code internal} is opt-in.
 *
 * <p><b>Known constraint.</b> Soul tokens are a secondary ExcellentEconomy
 * currency that Vault cannot see, so a balance read here goes through the
 * configured placeholder, never through Vault.
 *
 * <p>The consequences are honest rather than hidden: a balance is only as
 * fresh as the placeholder, an offline player cannot be read at all, and a
 * withdrawal is optimistic — the external plugin is the one that can refuse.
 * Every shop and reward path therefore re-checks the balance immediately before
 * spending.
 */
public final class CommandCurrency implements CurrencyService {

    private final CurrencyType type;
    private final PapiBridge papi;
    private final String giveCommand;
    private final String takeCommand;
    private final String balancePlaceholder;

    public CommandCurrency(CurrencyType type, PapiBridge papi, String giveCommand,
                           String takeCommand, String balancePlaceholder) {
        this.type = type;
        this.papi = papi;
        this.giveCommand = giveCommand;
        this.takeCommand = takeCommand;
        this.balancePlaceholder = balancePlaceholder;
    }

    @Override
    public CurrencyType type() {
        return type;
    }

    @Override
    public BigDecimal balance(OfflinePlayer player) {
        Player online = Bukkit.getPlayer(player.getUniqueId());
        if (online == null || balancePlaceholder.isBlank()) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(papi.resolveNumber(online, balancePlaceholder, 0d));
    }

    @Override
    public boolean has(OfflinePlayer player, BigDecimal amount) {
        return balance(player).compareTo(amount) >= 0;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return true;
        }
        if (!has(player, amount)) {
            return false;
        }
        return dispatch(takeCommand, player, amount);
    }

    @Override
    public boolean deposit(OfflinePlayer player, BigDecimal amount) {
        return amount.signum() <= 0 || dispatch(giveCommand, player, amount);
    }

    @Override
    public boolean set(OfflinePlayer player, BigDecimal amount) {
        BigDecimal current = balance(player);
        int comparison = amount.compareTo(current);
        if (comparison == 0) {
            return true;
        }
        return comparison > 0
                ? deposit(player, amount.subtract(current))
                : withdraw(player, current.subtract(amount));
    }

    @Override
    public boolean available() {
        return !giveCommand.isBlank() && !takeCommand.isBlank();
    }

    private boolean dispatch(String template, OfflinePlayer player, BigDecimal amount) {
        if (template.isBlank()) {
            return false;
        }
        String name = player.getName();
        if (name == null) {
            return false;
        }
        String command = new Ctx()
                .put("player", name)
                .put("amount", amount.stripTrailingZeros().toPlainString())
                .applyRaw(template);
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
