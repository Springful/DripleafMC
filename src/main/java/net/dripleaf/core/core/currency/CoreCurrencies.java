package net.dripleaf.core.core.currency;

import net.dripleaf.core.api.CurrencyRegistry;
import net.dripleaf.core.api.CurrencyService;
import net.dripleaf.core.api.CurrencyType;
import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.config.Cfg;
import net.dripleaf.core.common.storage.PlayerData;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Builds the three currency implementations from {@code config.yml}.
 *
 * <p>Money is always Vault. Shards and souls each pick {@code internal} or
 * {@code command} per the {@code currencies:} block, so a server with live
 * balances in another plugin can run in {@code command} mode until a migration
 * is agreed, and switch with a config edit and a reload afterwards.
 */
public final class CoreCurrencies implements CurrencyRegistry {

    private final Services services;
    private final Map<CurrencyType, CurrencyService> implementations =
            new EnumMap<>(CurrencyType.class);
    private final Map<CurrencyType, String> modes = new EnumMap<>(CurrencyType.class);

    public CoreCurrencies(Services services) {
        this.services = services;
    }

    /** Rebuilt on reload; callers always go through {@link #get}, never cache. */
    public void load(Cfg cfg) {
        implementations.clear();
        modes.clear();

        implementations.put(CurrencyType.MONEY, new MoneyCurrency(services.hooks().vault()));
        modes.put(CurrencyType.MONEY, "vault");

        build(CurrencyType.SHARDS, cfg.childOrEmpty("shards"),
                PlayerData::shards, PlayerData::shards);
        build(CurrencyType.SOULS, cfg.childOrEmpty("souls"),
                PlayerData::souls, PlayerData::souls);
    }

    private void build(CurrencyType type, Cfg cfg,
                       Function<PlayerData, BigDecimal> getter,
                       BiConsumer<PlayerData, BigDecimal> setter) {
        String mode = cfg.string("mode", "internal").trim().toLowerCase(Locale.ROOT);
        if ("command".equals(mode)) {
            implementations.put(type, new CommandCurrency(type, services.hooks().papi(),
                    cfg.string("give-command", ""),
                    cfg.string("take-command", ""),
                    cfg.string("balance-placeholder", "")));
            modes.put(type, "command");
            return;
        }
        implementations.put(type,
                new InternalCurrency(type, services.players(), getter, setter));
        modes.put(type, "internal");
    }

    @Override
    public CurrencyService get(CurrencyType type) {
        return implementations.get(type);
    }

    /** {@code internal}, {@code command} or {@code vault} — for the Diagnostics screen. */
    public String mode(CurrencyType type) {
        return modes.getOrDefault(type, "unset");
    }
}
