package net.dripleaf.core;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandRegistry;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.common.config.ConfigManager;
import net.dripleaf.core.common.cooldown.CooldownService;
import net.dripleaf.core.common.cooldown.WarmupService;
import net.dripleaf.core.common.hook.Hooks;
import net.dripleaf.core.common.icon.IconService;
import net.dripleaf.core.common.log.AuditLog;
import net.dripleaf.core.common.money.AmountParser;
import net.dripleaf.core.common.scheduler.Schedulers;
import net.dripleaf.core.common.sound.SoundService;
import net.dripleaf.core.common.storage.PlayerDataStore;
import net.dripleaf.core.common.text.MessageService;
import net.dripleaf.core.common.ui.ChatPrompt;
import net.dripleaf.core.common.ui.ChestListener;
import net.dripleaf.core.common.ui.ChestRenderer;
import net.dripleaf.core.common.ui.DialogRenderer;
import net.dripleaf.core.common.ui.UiService;
import net.dripleaf.core.core.CoreModule;
import net.dripleaf.core.core.commands.CoreCommand;
import net.dripleaf.core.rebirth.RebirthMigration;
import net.dripleaf.core.rebirth.RebirthModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Bootstrap. Owns nothing.
 *
 * <p>Builds the shared foundations in a fixed order, then enables each module.
 * Everything else lives in {@code common/}, {@code core/} or {@code rebirth/}.
 *
 * <p>The plugin enables cleanly with every soft dependency absent — Vault,
 * PlaceholderAPI, LuckPerms, mcMMO, GriefPrevention, ExcellentCrates, Floodgate.
 * A missing hook logs a line, disables the feature that needed it, and the
 * server keeps running.
 */
public final class DripleafCore extends JavaPlugin {

    /** What a reload changed, for the {@code Reloaded: …} line. */
    public record ReloadReport(String summary) {
    }

    private final long startedAt = System.currentTimeMillis();

    private Schedulers schedulers;
    private Services services;
    private CoreModule core;
    private RebirthModule rebirth;
    private ChestRenderer chests;
    private CommandRegistry rootCommands;

    @Override
    public void onEnable() {
        // 1. Foundations, in dependency order.
        this.schedulers = new Schedulers(this);
        ConfigManager configs = new ConfigManager(this);
        saveDefaults(configs);

        MessageService messages = new MessageService();
        messages.load(configs.load("messages.yml"));

        Hooks hooks = new Hooks();
        hooks.connectAll((long) configs.view("config.yml")
                .number("performance.placeholder-cache-millis", 250d, 0d, 10_000d));

        PlayerDataStore players = new PlayerDataStore(this, schedulers);
        SoundService sounds = new SoundService();
        IconService icons = new IconService();
        AmountParser amounts = new AmountParser(
                configs.view("config.yml").string("economy.exact-format", "#,##0.##"),
                configs.view("config.yml").string("economy.compact-format", "0.##"));
        AuditLog audit = new AuditLog(this, schedulers);
        CooldownService cooldowns = new CooldownService();
        WarmupService warmups = new WarmupService(schedulers, messages, sounds);
        ChatPrompt prompts = new ChatPrompt(schedulers, messages);

        this.chests = new ChestRenderer(messages, prompts);
        UiService ui = new UiService(players, new net.dripleaf.core.common.ui.BedrockService(
                hooks.floodgate(), hooks.papi()), new DialogRenderer(messages), chests);

        this.services = new Services(this, schedulers, configs, messages, players, hooks,
                sounds, icons, amounts, audit, cooldowns, warmups, prompts, ui);

        applySharedConfig();

        // 2. Listeners — registered exactly once, never on reload.
        PluginManager manager = getServer().getPluginManager();
        manager.registerEvents(players, this);
        manager.registerEvents(new ChestListener(), this);
        manager.registerEvents(prompts, this);
        manager.registerEvents(ui, this);
        manager.registerEvents(warmups, this);
        warmups.start();

        // 3. Modules.
        this.core = new CoreModule(services);
        core.enable();

        this.rebirth = new RebirthModule(services, core.leaderboards());
        rebirth.enable();
        manager.registerEvents(new RebirthMigration(services, rebirth.service()), this);

        // 4. The root command, always registered.
        this.rootCommands = new CommandRegistry(services, "config.yml");
        rootCommands.declare(new CommandSpec("dripleafcore", true, "", List.of("dcore", "dlc"),
                        0L, 0d, "DripleafCore status, reload and admin panel"),
                (s, spec) -> new CoreCommand(s, spec, this));
        rootCommands.registerAll(this);

        // 5. Optional integrations and start-up reporting.
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new DripleafPlaceholders(this, services, core, rebirth).register();
            getLogger().info("Registered the %dripleaf_…% placeholder expansion.");
        }
        players.startAutosave(configs.view("config.yml")
                .integer("storage.autosave-minutes", 5, 1, 120));

        getLogger().info(icons.validate(referencedMaterials()));
        getLogger().info(hooks.summary());
        configs.log().report(getLogger());
        // Registration itself happens later, inside Paper's COMMANDS lifecycle
        // event, so this reports what was declared and what config enabled --
        // the count of live commands is logged by the registry when it fires.
        getLogger().info("DripleafCore " + getPluginMeta().getVersion() + " enabled. "
                + core.commands().declaredCount() + " core commands declared; UI default "
                + ui.javaMode() + " for Java, " + ui.bedrockMode() + " for Bedrock.");
    }

    @Override
    public void onDisable() {
        if (rebirth != null) {
            rebirth.disable();
        }
        if (core != null) {
            core.disable();
        }
        if (services != null) {
            services.players().flushAll();
        }
        if (schedulers != null) {
            schedulers.shutdown();
        }
    }

    /**
     * Reloads configuration data.
     *
     * <p>Nothing is re-registered. If parsing throws, the exception propagates to
     * the caller with the previous configuration still live and untouched.
     */
    public ReloadReport reload(String scope) {
        services.configs().log().clear();
        List<String> changed = new ArrayList<>(4);

        switch (scope) {
            case "messages" -> {
                services.messages().load(services.configs().reload("messages.yml"));
                changed.add("messages");
            }
            case "shops" -> {
                core.shops().load();
                changed.add(core.shops().shops().size() + " shops");
            }
            case "rebirth" -> {
                rebirth.reload();
                changed.add(rebirth.tiers().size() + " tiers");
            }
            case "core" -> {
                core.reload();
                changed.add("core data");
            }
            default -> {
                reloadConfig();
                services.configs().reload("config.yml");
                services.messages().load(services.configs().reload("messages.yml"));
                applySharedConfig();
                core.reload();
                rebirth.reload();
                changed.add("messages");
                changed.add(core.warps().count() + " warps");
                changed.add(core.kits().count() + " kits");
                changed.add(core.shops().shops().size() + " shops");
                changed.add(rebirth.tiers().size() + " tiers");
            }
        }

        // Any menu still open was built from configuration that no longer exists.
        for (var player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory()
                    .getHolder(false) instanceof net.dripleaf.core.common.ui.ChestHolder) {
                player.closeInventory();
                services.messages().send(player, "core.menu-closed-by-reload");
            }
        }

        services.icons().load(services.configs().reload("core/icons.yml"),
                services.configs().log());
        getLogger().info(services.icons().validate(referencedMaterials()));
        services.configs().log().report(getLogger());

        return new ReloadReport(String.join(", ", changed));
    }

    /** Config that belongs to the shared services rather than to either module. */
    private void applySharedConfig() {
        var config = services.configs().view("config.yml");
        services.ui().configure(services.configs().view("config.yml", "ui"));
        services.sounds().load(services.configs().view("config.yml", "sounds"));
        services.icons().load(services.configs().load("core/icons.yml"),
                services.configs().log());
        services.warmups().configure(
                config.number("warmup.cancel-on-move-blocks", 0.5d, 0d, 64d),
                config.bool("warmup.cancel-on-damage", true),
                config.integer("warmup.bar-cells", 20, 4, 40));
    }

    private List<Material> referencedMaterials() {
        List<Material> materials = new ArrayList<>(core == null
                ? List.of() : core.shops().referencedMaterials());
        if (core != null) {
            core.warps().all().forEach(warp -> materials.add(warp.icon()));
            core.kits().names().forEach(name -> {
                var kit = core.kits().kit(name);
                if (kit != null) {
                    materials.add(kit.icon());
                }
            });
        }
        if (rebirth != null) {
            rebirth.tiers().all().forEach(tier -> materials.add(tier.icon()));
        }
        return materials;
    }

    /** Writes every bundled yml on first run, before anything can abort start-up. */
    private void saveDefaults(ConfigManager configs) {
        saveDefaultConfig();
        for (String resource : List.of(
                "config.yml", "messages.yml",
                "core/commands.yml", "core/warps.yml", "core/kits.yml", "core/icons.yml",
                "core/shops/server-shop.yml", "core/shops/shard-shop.yml",
                "core/shops/soul-shop.yml",
                "rebirth/rebirth.yml", "rebirth/tiers.yml", "rebirth/unlocks.yml")) {
            configs.load(resource);
        }
    }

    public long startedAt() {
        return startedAt;
    }

    public Services services() {
        return services;
    }

    public CoreModule core() {
        return core;
    }

    public RebirthModule rebirth() {
        return rebirth;
    }
}
