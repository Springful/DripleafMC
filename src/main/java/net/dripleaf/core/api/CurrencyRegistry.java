package net.dripleaf.core.api;

/**
 * Lookup for the three currency implementations.
 *
 * <p>The registry itself is owned by the core module; this interface is what
 * the rebirth module — and anything added later — depends on, so no module
 * reaches into another's internals.
 */
public interface CurrencyRegistry {

    CurrencyService get(CurrencyType type);

    default CurrencyService money() {
        return get(CurrencyType.MONEY);
    }

    default CurrencyService shards() {
        return get(CurrencyType.SHARDS);
    }

    default CurrencyService souls() {
        return get(CurrencyType.SOULS);
    }
}
