package net.dripleafmc.rebirth;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dripleafmc.rebirth.command.RebirthCommand;
import net.dripleafmc.rebirth.config.Lang;
import net.dripleafmc.rebirth.config.RewardRegistry;
import net.dripleafmc.rebirth.config.Settings;
import net.dripleafmc.rebirth.config.TierRegistry;
import net.dripleafmc.rebirth.core.RebirthService;
import net.dripleafmc.rebirth.data.ProfileStore;
import net.dripleafmc.rebirth.hook.McMmoBridge;
import net.dripleafmc.rebirth.hook.PapiBridge;
import net.dripleafmc.rebirth.hook.RebirthExpansion;
import net.dripleafmc.rebirth.requirement.RequirementFactory;
import net.dripleafmc.rebirth.reward.RewardExecutor;
import net.dripleafmc.rebirth.ui.RebirthUI;
import net.dripleafmc.rebirth.ui.Renderer;
import net.dripleafmc.rebirth.ui.dialog.DialogUI;
import net.dripleafmc.rebirth.ui.menu.MenuListener;
import net.dripleafmc.rebirth.ui.menu.MenuUI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DripleafRebirth.
 *
 * <p>The plugin is one engine with two interchangeable front ends:
 * <ul>
 *   <li>{@link MenuUI} - the chest GUI, driven by menus.yml</li>
 *   <li>{@link DialogUI} - native Paper dialogs, driven by dialogs.yml</li>
 * </ul>
 * Both are constructed at startup and both are always usable; config.yml
 * picks the default, and DIALOG falls back to MENU per-player when a client
 * cannot render dialogs.
 */
public final class RebirthPlugin extends JavaPlugin {

    private Settings settings;
    private Lang lang;
    private TierRegistry tiers;
    private RewardRegistry rewards;

    private ProfileStore store;
    private RebirthService service;
    private RewardExecutor rewardExecutor;
    private Renderer renderer;

    private PapiBridge papi;
    private McMmoBridge mcmmo;
    private Economy economy;

    private MenuUI menuUI;
    private DialogUI dialogUI;

    /**
     * Every yml shipped in the jar besides config.yml, which
     * {@link #saveDefaultConfig()} handles.
     */
    private static final java.util.List<String> BUNDLED_YMLS = java.util.List.of(
            "lang.yml", "tiers.yml", "rewards.yml", "menus.yml", "dialogs.yml");

    @Override
    public void onEnable() {
        saveBundledDefaults();

        if (!setupEconomy()) {
            getLogger().severe("No Vault economy provider found. Disabling DripleafRebirth.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.store = new ProfileStore(this);
        this.rewardExecutor = new RewardExecutor(this);
        this.service = new RebirthService(this);
        this.renderer = new Renderer(this);

        loadConfiguration();

        this.menuUI = new MenuUI(this);
        this.dialogUI = new DialogUI(this);

        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(store, this);

        LifecycleEventManager<org.bukkit.plugin.Plugin> manager = getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(RebirthCommand.build(this),
                        "Rebirth / prestige menu", java.util.List.of("prestige", "ascend")));

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new RebirthExpansion(this).register();
            getLogger().info("Registered the %rebirth_...% placeholder expansion.");
        }

        store.startAutoSave(settings.autoSaveMinutes());
        getLogger().info("Enabled in " + settings.uiMode() + " mode.");
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.flush();
        }
    }

    /** Full reload of every yml. Safe at runtime; nothing caches across the swap. */
    public void reloadEverything() {
        reloadConfig();
        loadConfiguration();
        if (menuUI != null) {
            menuUI.invalidate();
        }
        if (dialogUI != null) {
            dialogUI.invalidate();
        }
    }

    /**
     * Writes every bundled yml to the data folder on first run.
     *
     * <p>This runs before anything that can abort startup. The per-file loaders
     * extract lazily too, but they only run once the plugin is fully enabled -
     * so an admin whose server trips the economy check below would otherwise be
     * left with a folder holding config.yml and nothing to edit.
     */
    private void saveBundledDefaults() {
        saveDefaultConfig();
        for (String name : BUNDLED_YMLS) {
            if (!new java.io.File(getDataFolder(), name).exists()) {
                saveResource(name, false);
            }
        }
    }

    private void loadConfiguration() {
        this.settings = new Settings(getConfig());
        this.lang = new Lang(this);
        this.papi = new PapiBridge(settings.placeholderCacheMillis());
        this.mcmmo = new McMmoBridge(papi);
        this.rewards = new RewardRegistry(this);
        this.tiers = new TierRegistry(this, new RequirementFactory(this));
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> provider =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            return false;
        }
        this.economy = provider.getProvider();
        return economy != null;
    }

    /**
     * Picks the front end for this player.
     * <p>
     * Bedrock players joining through Floodgate are given a UUID whose most
     * significant bits are zero, and their client cannot render native dialogs.
     * With {@code ui.fallback-to-menu-for-unsupported} on they transparently get
     * the chest GUI instead, so a Geyser server can run DIALOG mode safely.
     */
    public RebirthUI ui(Player player) {
        if (settings.uiMode() != Settings.UiMode.DIALOG) {
            return menuUI;
        }
        if (settings.dialogFallbackToMenu() && player.getUniqueId().getMostSignificantBits() == 0L) {
            return menuUI;
        }
        return dialogUI;
    }

    public RebirthUI menu() { return menuUI; }
    public RebirthUI dialog() { return dialogUI; }

    public Settings settings() { return settings; }
    public Lang lang() { return lang; }
    public TierRegistry tiers() { return tiers; }
    public RewardRegistry rewards() { return rewards; }
    public ProfileStore store() { return store; }
    public RebirthService service() { return service; }
    public RewardExecutor rewardExecutor() { return rewardExecutor; }
    public Renderer renderer() { return renderer; }
    public PapiBridge papi() { return papi; }
    public McMmoBridge mcmmo() { return mcmmo; }
    public Economy economy() { return economy; }
}
