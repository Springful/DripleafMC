package net.dripleaf.core.api;

/**
 * Lifecycle contract for a top-level feature module.
 *
 * <p>{@code DripleafCore} builds the shared services first, then enables each
 * module in a fixed order. Modules never reach into each other's internals —
 * the rebirth module talks to core exclusively through {@code api/}. That is
 * what makes {@code /dripleafcore reload} safe and what lets a third module be
 * bolted on later without touching either existing one.
 */
public interface DripleafModule {

    /** Stable identifier used in config, commands and log lines. */
    String name();

    /** Register listeners, commands and tasks. Called once per server start. */
    void enable();

    /** Release everything {@link #enable()} took. Must be idempotent. */
    void disable();

    /**
     * Swap configuration data in place.
     *
     * <p>Never re-registers commands or listeners — that path leaks. Data only.
     */
    void reload();
}
