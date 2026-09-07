package net.dripleaf.core.core.currency;

import net.dripleaf.core.api.CurrencyService;
import net.dripleaf.core.api.CurrencyType;
import net.dripleaf.core.common.storage.PlayerData;
import net.dripleaf.core.common.storage.PlayerDataStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Shards and souls held in DripleafCore's own player data.
 *
 * <p>This is the target state for both. Balances live in
 * {@code data/players/<uuid>.yml}, are saved atomically, and are only ever
 * touched on the main thread — an integral currency with a lost update is a
 * support ticket that cannot be resolved from logs.
 *
 * <p>An offline player's balance is read and written through the store's
 * offline path, so an admin can adjust someone who is not connected without
 * that change being clobbered when they next log in.
 */
public final class InternalCurrency implements CurrencyService {

    private final CurrencyType type;
    private final PlayerDataStore store;
    private final Function<PlayerData, BigDecimal> getter;
    private final BiConsumer<PlayerData, BigDecimal> setter;

    public InternalCurrency(CurrencyType type, PlayerDataStore store,
                            Function<PlayerData, BigDecimal> getter,
                            BiConsumer<PlayerData, BigDecimal> setter) {
        this.type = type;
        this.store = store;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public CurrencyType type() {
        return type;
    }

    @Override
    public BigDecimal balance(OfflinePlayer player) {
        return getter.apply(data(player));
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
        PlayerData data = data(player);
        BigDecimal current = getter.apply(data);
        if (current.compareTo(amount) < 0) {
            return false;
        }
        setter.accept(data, current.subtract(amount));
        flush(player, data);
        return true;
    }

    @Override
    public boolean deposit(OfflinePlayer player, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return true;
        }
        PlayerData data = data(player);
        setter.accept(data, getter.apply(data).add(amount));
        flush(player, data);
        return true;
    }

    @Override
    public boolean set(OfflinePlayer player, BigDecimal amount) {
        PlayerData data = data(player);
        setter.accept(data, amount.max(BigDecimal.ZERO));
        flush(player, data);
        return true;
    }

    @Override
    public boolean available() {
        return true;
    }

    private PlayerData data(OfflinePlayer player) {
        return Bukkit.getPlayer(player.getUniqueId()) != null
                ? store.get(player.getUniqueId())
                : store.loadOffline(player.getUniqueId());
    }

    private void flush(OfflinePlayer player, PlayerData data) {
        if (Bukkit.getPlayer(player.getUniqueId()) == null) {
            store.saveOffline(data);
        }
    }
}
