package net.dripleaf.core.api;

/** A service whose behaviour is driven by config and can be re-read in place. */
public interface ReloadAware {

    void reload();
}
