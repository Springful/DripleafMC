package net.dripleaf.core.core.admin;

import net.dripleaf.core.api.CurrencyType;
import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.common.config.ValidationLog;
import net.dripleaf.core.common.hook.Bridge;
import net.dripleaf.core.common.sound.SoundService;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.Glyphs;
import net.dripleaf.core.common.text.Palette;
import net.dripleaf.core.common.ui.ButtonStyle;
import net.dripleaf.core.common.ui.ChestLayout;
import net.dripleaf.core.common.ui.Screen;
import net.dripleaf.core.common.ui.ScreenButton;
import net.dripleaf.core.common.ui.ScreenInput;
import net.dripleaf.core.core.CoreModule;
import net.dripleaf.core.core.shop.ShopDefinition;
import net.dripleaf.core.core.warps.Warp;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code /dripleafcore admin} — the control panel.
 *
 * <p>Everything reachable here is also reachable by command, deliberately:
 * dialogs are the less reliable surface on Bedrock, and an admin who cannot
 * open a panel must still be able to do the job. The panel itself goes through
 * the same UI router as everything else, so it is a chest GUI for a Bedrock
 * admin without a second implementation.
 *
 * <p>Every mutating action goes through a confirmation screen showing the
 * before and after value, and every one writes a line to {@code logs/admin.log}.
 */
public final class AdminScreens {

    private final Services services;
    private final CoreModule core;

    public AdminScreens(Services services, CoreModule core) {
        this.services = services;
        this.core = core;
    }

    // ------------------------------------------------------------------ root

    public void openRoot(Player player) {
        long uptimeSeconds = (System.currentTimeMillis() - startedAt) / 1000L;

        Screen screen = Screen.of("admin-root",
                        Palette.brand(services.messages().raw("admin.root-title")))
                .line(stat("admin.stat-version",
                        services.plugin().getPluginMeta().getVersion()))
                .line(stat("admin.stat-uptime",
                        DripleafCommand.formatDuration(uptimeSeconds * 1000L)))
                .line(stat("admin.stat-online", Bukkit.getOnlinePlayers().size()
                        + "/" + Bukkit.getMaxPlayers()))
                .line(stat("admin.stat-tps", String.format("%.2f", Bukkit.getTPS()[0])))
                .line(stat("admin.stat-modules", moduleSummary()))
                .blank()
                .button(nav("modules", "admin.section-modules", Material.COMPARATOR,
                        this::openModules))
                .button(nav("players", "admin.section-players", Material.PLAYER_HEAD,
                        this::openPlayerSearch))
                .button(nav("economy", "admin.section-economy", Material.GOLD_INGOT,
                        this::openEconomy))
                .button(nav("rebirth", "admin.section-rebirth", Material.NETHER_STAR,
                        this::openRebirth))
                .button(nav("warps", "admin.section-warps", Material.ENDER_PEARL,
                        this::openWarps))
                .button(nav("commands", "admin.section-commands", Material.COMMAND_BLOCK,
                        this::openCommands))
                .button(nav("diagnostics", "admin.section-diagnostics", Material.SPYGLASS,
                        this::openDiagnostics))
                .layout(ChestLayout.chest(5))
                .build();

        services.sounds().play(player, SoundService.MENU_OPEN);
        services.ui().open(player, screen);
    }

    private static final long startedAt = System.currentTimeMillis();

    // --------------------------------------------------------------- modules

    public void openModules(Player player) {
        Screen screen = Screen.of("admin-modules",
                        Palette.brand(services.messages().raw("admin.section-modules")))
                .line(services.messages().raw("admin.modules-intro"))
                .blank()
                .button(ScreenButton.of("reload-core",
                                services.messages().raw("admin.reload-core"))
                        .style(ButtonStyle.PRIMARY)
                        .material(Material.REPEATER)
                        .action(clicker -> {
                            core.reload();
                            log(clicker, "reloaded core module data");
                            services.messages().send(clicker, "admin.reloaded",
                                    Ctx.of("what", "core"));
                        })
                        .build())
                .button(ScreenButton.of("reload-all",
                                services.messages().raw("admin.reload-all"))
                        .style(ButtonStyle.PRIMARY)
                        .material(Material.REDSTONE_TORCH)
                        .action(clicker -> clicker.performCommand("dripleafcore reload"))
                        .build())
                .button(back(this::openRoot))
                .build();
        services.ui().open(player, screen);
    }

    // --------------------------------------------------------------- players

    public void openPlayerSearch(Player player) {
        Screen screen = Screen.of("admin-players",
                        Palette.brand(services.messages().raw("admin.section-players")))
                .line(services.messages().raw("admin.players-intro"))
                .input(ScreenInput.text("name",
                        services.messages().raw("admin.player-name-label"),
                        services.messages().raw("admin.player-name-hint")))
                .onSubmit(services.messages().raw("admin.player-search"), (clicker, values) -> {
                    Player target = Bukkit.getPlayerExact(values.getOrDefault("name", ""));
                    if (target == null) {
                        services.messages().send(clicker, "errors.player-not-found",
                                Ctx.of("player", values.getOrDefault("name", "")));
                        openPlayerSearch(clicker);
                        return;
                    }
                    openPlayer(clicker, target);
                })
                .button(back(this::openRoot))
                .build();
        services.ui().open(player, screen);
    }

    public void openPlayer(Player admin, Player target) {
        var data = services.players().get(target);
        Screen.Builder builder = Screen.of("admin-player",
                        Palette.brand(target.getName()))
                .line(stat("admin.stat-uuid", target.getUniqueId().toString()))
                .line(stat("admin.stat-playtime",
                        DripleafCommand.formatDuration(data.playtimeMillis())))
                .line(stat("admin.stat-tier", String.valueOf(data.rebirthTier())))
                .line(stat("admin.stat-homes", String.valueOf(data.homes().size())))
                .line(stat("admin.stat-ui", services.ui().effectiveMode(target).name()))
                .blank();

        for (CurrencyType currency : CurrencyType.values()) {
            var service = services.currencies().get(currency);
            BigDecimal balance = service == null ? BigDecimal.ZERO : service.balance(target);
            builder.line(stat("admin.stat-balance", currency.id() + ": "
                    + services.amounts().formatExact(currency, balance)));
        }

        for (CurrencyType currency : CurrencyType.values()) {
            builder.button(ScreenButton.of("set-" + currency.id(),
                            Palette.brand(new Ctx().put("currency", currency.id())
                                    .applyRaw(services.messages().raw("admin.set-balance"))))
                    .style(ButtonStyle.PRIMARY)
                    .material(currency == CurrencyType.MONEY ? Material.GOLD_INGOT
                            : currency == CurrencyType.SHARDS ? Material.DIAMOND
                            : Material.NETHER_STAR)
                    .action(clicker -> openBalanceEdit(clicker, target, currency))
                    .build());
        }

        if (services.rebirth() != null) {
            builder.button(ScreenButton.of("set-tier",
                            Palette.soul(services.messages().raw("admin.set-tier")))
                    .style(ButtonStyle.SOUL)
                    .material(Material.NETHER_STAR)
                    .action(clicker -> openTierEdit(clicker, target))
                    .build());
        }
        builder.button(back(this::openPlayerSearch));
        services.ui().open(admin, builder.build());
    }

    private void openBalanceEdit(Player admin, Player target, CurrencyType currency) {
        var service = services.currencies().get(currency);
        BigDecimal before = service == null ? BigDecimal.ZERO : service.balance(target);

        Screen screen = Screen.of("admin-balance",
                        Palette.brand(services.messages().raw("admin.set-balance-title")))
                .line(stat("admin.stat-player", target.getName()))
                .line(stat("admin.stat-current",
                        services.amounts().formatExact(currency, before)))
                .input(ScreenInput.text("amount",
                        services.messages().raw("admin.amount-label"), "1k, 2.5m, all"))
                .onSubmit(services.messages().raw("ui.confirm"), (clicker, values) -> {
                    var parsed = services.amounts().parseFor(currency,
                            values.getOrDefault("amount", ""), before);
                    if (!parsed.ok()) {
                        services.messages().send(clicker, parsed.errorKey());
                        openBalanceEdit(clicker, target, currency);
                        return;
                    }
                    confirm(clicker, "admin.confirm-balance", new Ctx()
                                    .put("player", target.getName())
                                    .put("currency", currency.id())
                                    .put("before", services.amounts()
                                            .formatExact(currency, before))
                                    .put("after", services.amounts()
                                            .formatExact(currency, parsed.get())),
                            confirmer -> {
                                if (service != null) {
                                    service.set(target, parsed.get());
                                }
                                log(confirmer, "set " + currency.id() + " for "
                                        + target.getName() + " from " + before.toPlainString()
                                        + " to " + parsed.get().toPlainString());
                                services.sounds().play(confirmer, SoundService.ADMIN_APPLIED);
                                openPlayer(confirmer, target);
                            },
                            canceller -> openPlayer(canceller, target));
                })
                .button(back(clicker -> openPlayer(clicker, target)))
                .build();
        services.ui().open(admin, screen);
    }

    private void openTierEdit(Player admin, Player target) {
        int before = services.rebirth().tier(target);
        Screen screen = Screen.of("admin-tier",
                        Palette.soul(services.messages().raw("admin.set-tier-title")))
                .line(stat("admin.stat-player", target.getName()))
                .line(stat("admin.stat-tier", String.valueOf(before)))
                .input(ScreenInput.text("tier",
                        services.messages().raw("admin.tier-label"), "0-"
                                + services.rebirth().maxTier()))
                .onSubmit(services.messages().raw("ui.confirm"), (clicker, values) -> {
                    int tier;
                    try {
                        tier = Integer.parseInt(values.getOrDefault("tier", "").trim());
                    } catch (NumberFormatException ex) {
                        services.messages().send(clicker, "errors.not-a-number",
                                Ctx.of("input", values.getOrDefault("tier", "")));
                        openTierEdit(clicker, target);
                        return;
                    }
                    confirm(clicker, "admin.confirm-tier", new Ctx()
                                    .put("player", target.getName())
                                    .put("before", String.valueOf(before))
                                    .put("after", String.valueOf(tier)),
                            confirmer -> {
                                confirmer.performCommand("rebirth admin set "
                                        + target.getName() + " " + tier);
                                openPlayer(confirmer, target);
                            },
                            canceller -> openPlayer(canceller, target));
                })
                .button(back(clicker -> openPlayer(clicker, target)))
                .build();
        services.ui().open(admin, screen);
    }

    // --------------------------------------------------------------- economy

    public void openEconomy(Player player) {
        Screen.Builder builder = Screen.of("admin-economy",
                        Palette.brand(services.messages().raw("admin.section-economy")))
                .line(services.messages().raw("admin.economy-intro"))
                .blank();

        for (ShopDefinition shop : core.shops().shops()) {
            builder.line(stat("admin.stat-shop", shop.id() + ": " + shop.itemCount()
                    + " items, ×" + shop.globalSellMultiplier() + ", "
                    + shop.currency().id()));
        }
        for (CurrencyType currency : CurrencyType.values()) {
            builder.line(stat("admin.stat-currency-mode",
                    currency.id() + ": " + core.currencies().mode(currency)));
        }

        builder.button(ScreenButton.of("refresh-baltop",
                        services.messages().raw("admin.refresh-baltop"))
                .style(ButtonStyle.PRIMARY)
                .material(Material.CLOCK)
                .action(clicker -> {
                    core.leaderboards().refreshNow();
                    log(clicker, "triggered a leaderboard refresh");
                    services.messages().send(clicker, "admin.baltop-refreshing");
                })
                .build());
        builder.button(ScreenButton.of("transactions",
                        services.messages().raw("admin.transaction-tail"))
                .style(ButtonStyle.NEUTRAL)
                .material(Material.PAPER)
                .action(clicker -> services.schedulers().asyncThenSync(
                        () -> services.audit().tail("transactions.log", 15),
                        lines -> {
                            for (String line : lines) {
                                clicker.sendMessage(net.dripleaf.core.common.text.Text
                                        .parse(Palette.colour(Palette.MUTED, line)));
                            }
                            if (lines.isEmpty()) {
                                services.messages().send(clicker, "admin.log-empty");
                            }
                        }))
                .build());
        builder.button(back(this::openRoot));
        services.ui().open(player, builder.build());
    }

    // --------------------------------------------------------------- rebirth

    public void openRebirth(Player player) {
        if (services.rebirth() == null) {
            services.messages().send(player, "admin.rebirth-disabled");
            return;
        }
        Screen screen = Screen.of("admin-rebirth",
                        Palette.soul(services.messages().raw("admin.section-rebirth")))
                .line(stat("admin.stat-max-tier",
                        String.valueOf(services.rebirth().maxTier())))
                .blank()
                .button(ScreenButton.of("browse",
                                services.messages().raw("admin.rebirth-browse"))
                        .style(ButtonStyle.SOUL)
                        .material(Material.NETHER_STAR)
                        .action(clicker -> clicker.performCommand("rebirth info"))
                        .build())
                .button(ScreenButton.of("reload",
                                services.messages().raw("admin.rebirth-reload"))
                        .style(ButtonStyle.PRIMARY)
                        .material(Material.REPEATER)
                        .action(clicker -> clicker.performCommand("dripleafcore reload rebirth"))
                        .build())
                .button(back(this::openRoot))
                .build();
        services.ui().open(player, screen);
    }

    // ----------------------------------------------------------------- warps

    public void openWarps(Player player) {
        Screen.Builder builder = Screen.of("admin-warps",
                        Palette.brand(services.messages().raw("admin.section-warps")))
                .line(stat("admin.stat-warps", String.valueOf(core.warps().count())))
                .blank()
                .layout(ChestLayout.chest(6))
                .button(ScreenButton.of("create",
                                services.messages().raw("admin.warp-create"))
                        .style(ButtonStyle.PRIMARY)
                        .material(Material.ENDER_EYE)
                        .line(services.messages().raw("admin.warp-create-hint"))
                        .action(clicker -> openWarpCreate(clicker))
                        .build());

        for (Warp warp : core.warps().all()) {
            builder.button(ScreenButton.of("warp-" + warp.key(), Palette.brand(warp.display()))
                    .material(warp.icon())
                    .style(ButtonStyle.NEUTRAL)
                    .line(Palette.colour(Palette.MUTED, Glyphs.ITEM + " " + warp.key()))
                    .line(Palette.colour(Palette.FAILURE,
                            Glyphs.ITEM + " " + services.messages().raw("admin.warp-delete")))
                    .action(clicker -> confirm(clicker, "admin.confirm-warp-delete",
                            Ctx.of("name", warp.key()),
                            confirmer -> {
                                core.warps().delete(warp.key());
                                log(confirmer, "deleted warp " + warp.key());
                                openWarps(confirmer);
                            },
                            this::openWarps))
                    .build());
        }
        builder.button(back(this::openRoot));
        services.ui().open(player, builder.build());
    }

    private void openWarpCreate(Player player) {
        Screen screen = Screen.of("admin-warp-create",
                        Palette.brand(services.messages().raw("admin.warp-create")))
                .line(services.messages().raw("admin.warp-create-body"))
                .input(ScreenInput.text("name",
                        services.messages().raw("admin.warp-name-label"), "mine"))
                .onSubmit(services.messages().raw("ui.confirm"), (clicker, values) -> {
                    String name = values.getOrDefault("name", "").trim();
                    if (name.isEmpty()) {
                        openWarpCreate(clicker);
                        return;
                    }
                    core.warps().save(name, clicker.getLocation(), "general");
                    log(clicker, "created warp " + name);
                    services.messages().send(clicker, "warps.saved", Ctx.of("name", name));
                    openWarps(clicker);
                })
                .button(back(this::openWarps))
                .build();
        services.ui().open(player, screen);
    }

    // -------------------------------------------------------------- commands

    public void openCommands(Player player) {
        Screen.Builder builder = Screen.of("admin-commands",
                        Palette.brand(services.messages().raw("admin.section-commands")))
                .line(stat("admin.stat-commands", core.commands().registeredCount()
                        + "/" + core.commands().declaredCount()))
                .line(services.messages().raw("admin.commands-intro"))
                .blank()
                .layout(ChestLayout.chest(6));

        for (CommandSpec spec : core.commands().catalogue()) {
            builder.button(ScreenButton.of("cmd-" + spec.id(),
                            (spec.enabled() ? Palette.colour(Palette.SUCCESS, Glyphs.MET)
                                    : Palette.colour(Palette.FAILURE, Glyphs.UNMET))
                                    + " " + Palette.brand("/" + spec.id()))
                    .material(spec.enabled() ? Material.COMMAND_BLOCK : Material.BARRIER)
                    .style(spec.enabled() ? ButtonStyle.PRIMARY : ButtonStyle.LOCKED)
                    .enabled(true)
                    .line(Palette.colour(Palette.MUTED, Glyphs.ITEM + " " + spec.description()))
                    .line(Palette.colour(Palette.STRUCTURE,
                            Glyphs.ITEM + " " + spec.permission()))
                    .line(Palette.colour(Palette.STRUCTURE, Glyphs.ITEM + " cooldown "
                            + spec.cooldown() + "s, warmup " + spec.warmup() + "s"))
                    .line(Palette.colour(Palette.DEEP_MUTED,
                            services.messages().raw("admin.commands-edit-hint")))
                    .build());
        }
        builder.button(back(this::openRoot));
        services.ui().open(player, builder.build());
    }

    // ----------------------------------------------------------- diagnostics

    public void openDiagnostics(Player player) {
        Screen.Builder builder = Screen.of("admin-diagnostics",
                        Palette.brand(services.messages().raw("admin.section-diagnostics")))
                .line(header("admin.diag-threads"))
                .line(item(services.schedulers().threadCount()
                        + " plugin thread, " + services.schedulers().pendingAsyncTasks()
                        + " queued task(s)"))
                .blank()
                .line(header("admin.diag-icons"))
                .line(item(services.icons().resolvedCount() + " resolved, "
                        + services.icons().unresolved().size() + " fell back, "
                        + services.icons().overrideCount() + " overrides"))
                .blank()
                .line(header("admin.diag-hooks"))
                .layout(ChestLayout.chest(6));

        for (Bridge bridge : services.hooks().all()) {
            builder.line(item((bridge.available()
                    ? Palette.colour(Palette.SUCCESS, Glyphs.MET)
                    : Palette.colour(Palette.FAILURE, Glyphs.UNMET))
                    + " " + bridge.name() + " — " + bridge.detail()));
        }

        List<ValidationLog.Issue> issues = services.configs().log().issues();
        builder.blank().line(header("admin.diag-config"));
        if (issues.isEmpty()) {
            builder.line(item(Palette.colour(Palette.SUCCESS,
                    services.messages().raw("admin.diag-config-clean"))));
        } else {
            for (ValidationLog.Issue issue : issues.subList(0, Math.min(10, issues.size()))) {
                builder.line(item(Palette.colour(Palette.WARNING, issue.toString())));
            }
        }

        builder.button(back(this::openRoot));
        services.ui().open(player, builder.build());
    }

    // ---------------------------------------------------------------- shared

    /** Every mutating action goes through this: before, after, and two buttons. */
    private void confirm(Player player, String bodyKey, Ctx ctx,
                         Consumer<Player> onConfirm, Consumer<Player> onCancel) {
        Screen screen = Screen.of("admin-confirm",
                        Palette.colour(Palette.DANGER,
                                services.messages().raw("admin.confirm-title")))
                .line(ctx.applyRaw(services.messages().raw(bodyKey)))
                .button(ScreenButton.of("confirm", services.messages().raw("ui.confirm"))
                        .style(ButtonStyle.DANGER)
                        .material(Material.LIME_DYE)
                        .action(onConfirm)
                        .build())
                .button(ScreenButton.of("cancel", services.messages().raw("ui.cancel"))
                        .style(ButtonStyle.NEUTRAL)
                        .material(Material.ARROW)
                        .action(onCancel)
                        .build())
                .build();
        services.ui().open(player, screen);
    }

    private ScreenButton nav(String id, String labelKey, Material icon,
                             Consumer<Player> action) {
        return ScreenButton.of(id, Palette.brand(services.messages().raw(labelKey)))
                .style(ButtonStyle.PRIMARY)
                .material(icon)
                .action(action)
                .build();
    }

    private ScreenButton back(Consumer<Player> action) {
        return ScreenButton.of("back", services.messages().raw("ui.back"))
                .style(ButtonStyle.NEUTRAL)
                .material(Material.ARROW)
                .action(action)
                .build();
    }

    private String stat(String key, String value) {
        return Ctx.of("value", value).applyRaw(services.messages().raw(key));
    }

    private String header(String key) {
        return Palette.colour(Palette.STRUCTURE, Glyphs.HEADER + " ")
                + Palette.colour(Palette.BODY, services.messages().raw(key));
    }

    private String item(String text) {
        return Palette.colour(Palette.STRUCTURE, Glyphs.ITEM + " ")
                + Palette.colour(Palette.BODY, text);
    }

    private String moduleSummary() {
        StringBuilder sb = new StringBuilder(32);
        sb.append("core ").append(core.enabled() ? "on" : "off");
        sb.append(", rebirth ").append(services.rebirth() == null ? "off" : "on");
        return sb.toString();
    }

    private void log(Player actor, String what) {
        services.audit().admin(actor.getName() + " " + what);
    }
}
