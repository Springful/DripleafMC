package net.dripleaf.core.common.ui;

import net.dripleaf.core.common.config.Cfg;
import net.dripleaf.core.common.storage.PlayerDataStore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * The router. Everything that opens a menu goes through here.
 *
 * <p>Resolution, in order:
 * <ol>
 *   <li>The player's own choice from {@code /uimode}, when
 *       {@code ui.allow-player-override} is on. This is the toggle: a player —
 *       or an admin testing on their behalf — can force every screen in the
 *       plugin to dialogs or every screen to chest GUIs and compare them
 *       directly, without a config edit or a restart.</li>
 *   <li>Otherwise the platform default: {@code ui.bedrock-mode} for a player
 *       {@link BedrockService} identifies as Bedrock, {@code ui.java-mode} for
 *       everyone else. Shipped as {@code CHEST} and {@code DIALOG}
 *       respectively.</li>
 * </ol>
 *
 * <p>The point of routing rather than branching at each call site is that there
 * is exactly one place where "which surface?" is decided, and every screen in
 * the plugin therefore honours the toggle for free.
 */
public final class UiService implements Listener {

    private final PlayerDataStore store;
    private final BedrockService bedrock;
    private final DialogRenderer dialogs;
    private final ChestRenderer chests;

    private UiMode javaMode = UiMode.DIALOG;
    private UiMode bedrockMode = UiMode.CHEST;
    private boolean allowOverride = true;

    public UiService(PlayerDataStore store, BedrockService bedrock,
                     DialogRenderer dialogs, ChestRenderer chests) {
        this.store = store;
        this.bedrock = bedrock;
        this.dialogs = dialogs;
        this.chests = chests;
    }

    /** Re-reads the {@code ui:} block. Data only — no re-registration. */
    public void configure(Cfg cfg) {
        this.javaMode = concrete(UiMode.from(cfg.string("java-mode", "DIALOG"), UiMode.DIALOG),
                UiMode.DIALOG);
        this.bedrockMode = concrete(UiMode.from(cfg.string("bedrock-mode", "CHEST"), UiMode.CHEST),
                UiMode.CHEST);
        this.allowOverride = cfg.bool("allow-player-override", true);
        bedrock.configure(cfg.childOrEmpty("bedrock-detection"));
    }

    /** Opens {@code screen} on whichever surface this player should get. */
    public void open(Player player, Screen screen) {
        if (effectiveMode(player) == UiMode.CHEST) {
            chests.open(player, screen);
        } else {
            dialogs.open(player, screen);
        }
    }

    /** Forces one surface regardless of preference — used only by {@code /uimode preview}. */
    public void openAs(Player player, Screen screen, UiMode mode) {
        if (mode == UiMode.CHEST) {
            chests.open(player, screen);
        } else {
            dialogs.open(player, screen);
        }
    }

    /** Closes whichever surface the player currently has open. */
    public void close(Player player) {
        player.closeInventory();
        player.closeDialog();
    }

    // ------------------------------------------------------------ resolution

    /** Never returns {@link UiMode#AUTO}. */
    public UiMode effectiveMode(Player player) {
        UiMode preference = preference(player);
        if (allowOverride && preference.concrete()) {
            return preference;
        }
        return platformMode(player);
    }

    /** What the server would give this player with no personal preference set. */
    public UiMode platformMode(Player player) {
        return bedrock.isBedrock(player) ? bedrockMode : javaMode;
    }

    /** The player's stored choice — {@link UiMode#AUTO} when they have none. */
    public UiMode preference(Player player) {
        String stored = store.get(player).uiPreference();
        return UiMode.from(stored, UiMode.AUTO);
    }

    /** @param mode {@link UiMode#AUTO} clears the override and returns the player to the default */
    public void preference(Player player, UiMode mode) {
        store.get(player).uiPreference(mode == UiMode.AUTO ? null : mode.name());
    }

    public boolean overrideAllowed() {
        return allowOverride;
    }

    public UiMode javaMode() {
        return javaMode;
    }

    public UiMode bedrockMode() {
        return bedrockMode;
    }

    public BedrockService bedrock() {
        return bedrock;
    }

    public ChestRenderer chests() {
        return chests;
    }

    private static UiMode concrete(UiMode mode, UiMode fallback) {
        return mode.concrete() ? mode : fallback;
    }

    // --------------------------------------------------------------- cleanup

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        bedrock.forget(uuid);
        chests.forget(uuid);
    }
}
