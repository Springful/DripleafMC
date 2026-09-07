package net.dripleaf.core.common.hook;

import java.util.List;

/**
 * Every third-party integration, built once and reported once.
 *
 * <p>{@link #report()} feeds both the start-up log line and the admin
 * Diagnostics screen, so "which soft dependencies did we actually find?" is
 * answerable without reading a stack trace.
 */
public final class Hooks {

    private final VaultBridge vault = new VaultBridge();
    private final PapiBridge papi = new PapiBridge();
    private final McMmoBridge mcmmo = new McMmoBridge(papi);
    private final LuckPermsBridge luckPerms = new LuckPermsBridge();
    private final CratesBridge crates = new CratesBridge();
    private final ClaimBridge claims = new ClaimBridge();
    private final FloodgateBridge floodgate = new FloodgateBridge();

    /** @param papiCacheMillis how long a resolved placeholder stays cached */
    public void connectAll(long papiCacheMillis) {
        vault.connect();
        papi.connect(papiCacheMillis);
        mcmmo.connect();
        luckPerms.connect();
        crates.connect();
        claims.connect();
        floodgate.connect();
    }

    public VaultBridge vault() {
        return vault;
    }

    public PapiBridge papi() {
        return papi;
    }

    public McMmoBridge mcmmo() {
        return mcmmo;
    }

    public LuckPermsBridge luckPerms() {
        return luckPerms;
    }

    public CratesBridge crates() {
        return crates;
    }

    public ClaimBridge claims() {
        return claims;
    }

    public FloodgateBridge floodgate() {
        return floodgate;
    }

    public List<Bridge> all() {
        return List.of(vault, papi, mcmmo, luckPerms, crates, claims, floodgate);
    }

    /** One line per hook: {@code Vault: EssentialsX Economy}. */
    public List<String> report() {
        return all().stream().map(bridge -> bridge.name() + ": " + bridge.detail()).toList();
    }

    public String summary() {
        long found = all().stream().filter(Bridge::available).count();
        return found + "/" + all().size() + " optional hooks connected";
    }
}
