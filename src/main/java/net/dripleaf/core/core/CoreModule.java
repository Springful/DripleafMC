package net.dripleaf.core.core;

import net.dripleaf.core.api.CurrencyType;
import net.dripleaf.core.api.DripleafModule;
import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandRegistry;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.core.admin.AdminScreens;
import net.dripleaf.core.core.commands.ChatCommands;
import net.dripleaf.core.core.commands.EconomyCommands;
import net.dripleaf.core.core.commands.HomeCommands;
import net.dripleaf.core.core.commands.KitCommands;
import net.dripleaf.core.core.commands.TeleportCommands;
import net.dripleaf.core.core.commands.UiModeCommand;
import net.dripleaf.core.core.commands.UtilityCommands;
import net.dripleaf.core.core.commands.WarpCommands;
import net.dripleaf.core.core.currency.CoreCurrencies;
import net.dripleaf.core.core.homes.HomeService;
import net.dripleaf.core.core.kits.KitService;
import net.dripleaf.core.core.leaderboard.LeaderboardService;
import net.dripleaf.core.core.shop.ShopService;
import net.dripleaf.core.core.social.SocialService;
import net.dripleaf.core.core.teleport.TeleportService;
import net.dripleaf.core.core.warps.WarpService;
import org.bukkit.event.inventory.InventoryType;

import java.util.List;

/**
 * Everything that is not rebirth: currencies, shops, warps, homes, kits,
 * teleportation, the command catalogue and the admin panel.
 *
 * <p>Commands are <em>declared</em> here with their shipped defaults and built
 * only if {@code core/commands.yml} enables them. Anything EssentialsX also
 * provides ships disabled, so dropping this jar onto a running server changes
 * nothing until staff opt in, one command at a time.
 */
public final class CoreModule implements DripleafModule {

    private final Services services;

    private final CoreCurrencies currencies;
    private final ShopService shops;
    private final WarpService warps;
    private final HomeService homes;
    private final KitService kits;
    private final TeleportService teleports;
    private final SocialService social;
    private final LeaderboardService leaderboards;
    private final AdminScreens admin;
    private final CommandRegistry commands;

    private boolean enabled;

    public CoreModule(Services services) {
        this.services = services;
        this.currencies = new CoreCurrencies(services);
        this.shops = new ShopService(services);
        this.warps = new WarpService(services);
        this.homes = new HomeService(services);
        this.kits = new KitService(services);
        this.teleports = new TeleportService(services);
        this.social = new SocialService(services);
        this.leaderboards = new LeaderboardService(services);
        this.admin = new AdminScreens(services, this);
        this.commands = new CommandRegistry(services, "core/commands.yml");
    }

    @Override
    public String name() {
        return "core";
    }

    @Override
    public void enable() {
        services.currencies(currencies);
        reload();

        var manager = services.plugin().getServer().getPluginManager();
        manager.registerEvents(teleports, services.plugin());
        manager.registerEvents(social, services.plugin());

        teleports.startExpiry();
        social.startAfkSweep();
        leaderboards.start(services.configs().view("config.yml")
                .integer("leaderboards.refresh-minutes", 5, 1, 1440));

        declareCommands();
        commands.registerAll(services.plugin());
        enabled = true;
    }

    @Override
    public void disable() {
        enabled = false;
    }

    /** Data only. Listeners and commands are registered exactly once, at enable. */
    @Override
    public void reload() {
        currencies.load(services.configs().view("config.yml", "currencies"));
        shops.load();
        warps.load();
        kits.load();

        var config = services.configs().view("config.yml");
        homes.configure(config.integer("homes.default-limit", 1, 0, 1000));
        teleports.configure(
                (long) config.number("teleport.request-timeout-seconds", 60d, 5d, 3600d),
                config.integer("teleport.rtp-radius", 5000, 100, 10_000_000),
                config.integer("teleport.rtp-attempts", 24, 1, 200));
        social.configure((long) config.number("social.afk-after-seconds", 300d, 30d, 86_400d));
    }

    public boolean enabled() {
        return enabled;
    }

    // ------------------------------------------------------------ catalogue

    /**
     * The command catalogue.
     *
     * <p>Every entry names its default permission and description; whether it is
     * registered at all is {@code core/commands.yml}'s decision.
     */
    private void declareCommands() {
        // The UI toggle is the one command that ships enabled by default: it is
        // how a Bedrock player recovers from a menu that will not draw for them.
        commands.declare(new CommandSpec("uimode", true, "dripleaf.uimode",
                        List.of("ui", "menumode"), 0L, 0d,
                        "Switch between dialog and chest-GUI menus"),
                UiModeCommand::new);

        // -- teleportation ------------------------------------------------
        declare("spawn", "dripleaf.spawn", List.of(), 30L, 5d, "Teleport to spawn",
                (s, spec) -> new TeleportCommands.Spawn(s, spec, this));
        declare("setspawn", "dripleaf.spawn.set", List.of(), 0L, 0d, "Set the spawn point",
                TeleportCommands.SetSpawn::new);
        declare("tpa", "dripleaf.tpa", List.of("call"), 15L, 0d,
                "Request to teleport to a player",
                (s, spec) -> new TeleportCommands.TpaRequest(s, spec, this, false));
        declare("tpahere", "dripleaf.tpahere", List.of(), 15L, 0d,
                "Request a player teleport to you",
                (s, spec) -> new TeleportCommands.TpaRequest(s, spec, this, true));
        declare("tpaccept", "dripleaf.tpa", List.of("tpyes"), 0L, 5d,
                "Accept a teleport request",
                (s, spec) -> new TeleportCommands.TpAccept(s, spec, this));
        declare("tpdeny", "dripleaf.tpa", List.of("tpno"), 0L, 0d,
                "Deny a teleport request",
                (s, spec) -> new TeleportCommands.TpDeny(s, spec, this));
        declare("tpacancel", "dripleaf.tpa", List.of(), 0L, 0d,
                "Cancel your outgoing teleport request",
                (s, spec) -> new TeleportCommands.TpaCancel(s, spec, this));
        declare("back", "dripleaf.back", List.of("return"), 60L, 3d,
                "Return to your previous location",
                (s, spec) -> new TeleportCommands.Back(s, spec, this));
        declare("rtp", "dripleaf.rtp", List.of("wild"), 300L, 5d, "Teleport somewhere random",
                (s, spec) -> new TeleportCommands.Rtp(s, spec, this));
        declare("tp", "dripleaf.tp.other", List.of(), 0L, 0d, "Teleport to or between players",
                (s, spec) -> new TeleportCommands.Tp(s, spec, this));
        declare("tphere", "dripleaf.tp.here", List.of(), 0L, 0d, "Bring a player to you",
                (s, spec) -> new TeleportCommands.TpHere(s, spec, this));
        declare("top", "dripleaf.top", List.of(), 10L, 0d, "Teleport to the surface",
                (s, spec) -> new TeleportCommands.Top(s, spec, this));

        // -- homes ---------------------------------------------------------
        declare("home", "dripleaf.home", List.of(), 0L, 0d, "Teleport to a home",
                (s, spec) -> new HomeCommands.Home(s, spec, this));
        declare("sethome", "dripleaf.home.set", List.of(), 0L, 0d, "Set a home",
                (s, spec) -> new HomeCommands.SetHome(s, spec, this));
        declare("delhome", "dripleaf.home.delete", List.of(), 0L, 0d, "Delete a home",
                (s, spec) -> new HomeCommands.DelHome(s, spec, this));
        declare("homes", "dripleaf.home", List.of(), 0L, 0d, "List your homes",
                (s, spec) -> new HomeCommands.Homes(s, spec, this));

        // -- warps ---------------------------------------------------------
        declare("warp", "dripleaf.warp", List.of(), 0L, 0d, "Teleport to a warp",
                (s, spec) -> new WarpCommands.WarpTo(s, spec, this));
        declare("warps", "dripleaf.warp", List.of(), 0L, 0d, "Open the warp menu",
                (s, spec) -> new WarpCommands.Warps(s, spec, this));
        declare("setwarp", "dripleaf.warp.set", List.of(), 0L, 0d, "Create a warp here",
                (s, spec) -> new WarpCommands.SetWarp(s, spec, this));
        declare("delwarp", "dripleaf.warp.delete", List.of(), 0L, 0d, "Delete a warp",
                (s, spec) -> new WarpCommands.DelWarp(s, spec, this));

        // -- kits ----------------------------------------------------------
        declare("kit", "dripleaf.kit", List.of(), 0L, 0d, "Claim a kit",
                (s, spec) -> new KitCommands.KitClaim(s, spec, this));
        declare("kits", "dripleaf.kit", List.of(), 0L, 0d, "Open the kit menu",
                (s, spec) -> new KitCommands.Kits(s, spec, this));

        // -- economy -------------------------------------------------------
        declare("balance", "dripleaf.balance", List.of("bal", "money"), 0L, 0d,
                "Check a balance", EconomyCommands.Balance::new);
        declare("baltop", "dripleaf.baltop", List.of(), 0L, 0d, "Richest players",
                (s, spec) -> new EconomyCommands.Baltop(s, spec, this));
        declare("pay", "dripleaf.pay", List.of(), 0L, 0d, "Pay another player",
                EconomyCommands.Pay::new);
        declare("shop", "dripleaf.shop", List.of("store"), 0L, 0d, "Open the server shop",
                (s, spec) -> new EconomyCommands.OpenShop(s, spec, this, "server"));
        declare("shardshop", "dripleaf.shop.shard", List.of(), 0L, 0d, "Open the shard shop",
                (s, spec) -> new EconomyCommands.OpenShop(s, spec, this, "shard"));
        declare("soulshop", "dripleaf.shop.soul", List.of(), 0L, 0d, "Open the soul shop",
                (s, spec) -> new EconomyCommands.OpenShop(s, spec, this, "soul"));
        declare("sell", "dripleaf.sell", List.of(), 0L, 0d, "Sell items",
                (s, spec) -> new EconomyCommands.Sell(s, spec, this));
        declare("worth", "dripleaf.worth", List.of(), 0L, 0d, "Check an item's sale value",
                (s, spec) -> new EconomyCommands.Worth(s, spec, this));
        declare("eco", "dripleaf.eco.admin", List.of(), 0L, 0d, "Manage money balances",
                (s, spec) -> new EconomyCommands.CurrencyAdmin(s, spec, CurrencyType.MONEY));
        declare("shards", "dripleaf.shards.admin", List.of(), 0L, 0d, "Manage shard balances",
                (s, spec) -> new EconomyCommands.CurrencyAdmin(s, spec, CurrencyType.SHARDS));
        declare("souls", "dripleaf.souls.admin", List.of(), 0L, 0d, "Manage soul balances",
                (s, spec) -> new EconomyCommands.CurrencyAdmin(s, spec, CurrencyType.SOULS));

        // -- chat and social ------------------------------------------------
        declare("msg", "dripleaf.msg", List.of("w", "tell"), 0L, 0d, "Private message",
                (s, spec) -> new ChatCommands.Msg(s, spec, this));
        declare("reply", "dripleaf.msg", List.of("r"), 0L, 0d, "Reply to the last message",
                (s, spec) -> new ChatCommands.Reply(s, spec, this));
        declare("ignore", "dripleaf.ignore", List.of(), 0L, 0d, "Ignore a player",
                (s, spec) -> new ChatCommands.Ignore(s, spec, this));
        declare("socialspy", "dripleaf.socialspy", List.of("ss"), 0L, 0d,
                "See private messages",
                (s, spec) -> new ChatCommands.SocialSpy(s, spec, this));
        declare("nick", "dripleaf.nick", List.of(), 0L, 0d, "Set a nickname",
                (s, spec) -> new ChatCommands.Nick(s, spec, this));
        declare("realname", "dripleaf.realname", List.of(), 0L, 0d,
                "Find the account behind a nickname",
                (s, spec) -> new ChatCommands.RealName(s, spec, this));

        // -- workstations ---------------------------------------------------
        declare("enderchest", "dripleaf.enderchest", List.of("ec"), 0L, 0d,
                "Open your ender chest",
                (s, spec) -> new UtilityCommands.Workstation(s, spec, null));
        workstation("workbench", "dripleaf.workbench", List.of("wb", "craft"),
                InventoryType.CRAFTING, "Open a crafting table");
        workstation("anvil", "dripleaf.anvil", List.of(), InventoryType.ANVIL, "Open an anvil");
        workstation("grindstone", "dripleaf.grindstone", List.of(),
                InventoryType.GRINDSTONE, "Open a grindstone");
        workstation("cartography", "dripleaf.cartography", List.of(),
                InventoryType.CARTOGRAPHY, "Open a cartography table");
        workstation("stonecutter", "dripleaf.stonecutter", List.of(),
                InventoryType.STONECUTTER, "Open a stonecutter");
        workstation("loom", "dripleaf.loom", List.of(), InventoryType.LOOM, "Open a loom");
        workstation("smithing", "dripleaf.smithing", List.of(),
                InventoryType.SMITHING, "Open a smithing table");

        // -- utility ---------------------------------------------------------
        declare("disposal", "dripleaf.disposal", List.of("trash"), 0L, 0d,
                "Open a disposal bin", UtilityCommands.Disposal::new);
        declare("hat", "dripleaf.hat", List.of(), 0L, 0d, "Wear the held item",
                UtilityCommands.Hat::new);
        declare("repair", "dripleaf.repair", List.of("fix"), 0L, 0d, "Repair items",
                UtilityCommands.Repair::new);
        declare("condense", "dripleaf.condense", List.of("compact"), 0L, 0d,
                "Compact your inventory", UtilityCommands.Condense::new);
        declare("recipe", "dripleaf.recipe", List.of(), 0L, 0d, "Look up a recipe",
                UtilityCommands.RecipeLookup::new);
        declare("heal", "dripleaf.heal", List.of(), 0L, 0d, "Restore health",
                (s, spec) -> new UtilityCommands.Restore(s, spec, true));
        declare("feed", "dripleaf.feed", List.of(), 0L, 0d, "Restore hunger",
                (s, spec) -> new UtilityCommands.Restore(s, spec, false));
        declare("god", "dripleaf.god", List.of(), 0L, 0d, "Toggle invulnerability",
                (s, spec) -> new UtilityCommands.God(s, spec, this));
        declare("fly", "dripleaf.fly", List.of(), 0L, 0d, "Toggle flight",
                UtilityCommands.Fly::new);
        declare("speed", "dripleaf.speed", List.of(), 0L, 0d, "Set movement speed",
                UtilityCommands.Speed::new);
        declare("vanish", "dripleaf.vanish", List.of("v"), 0L, 0d, "Toggle vanish",
                (s, spec) -> new UtilityCommands.Vanish(s, spec, this));
        declare("invsee", "dripleaf.invsee", List.of(), 0L, 0d, "View a player's inventory",
                UtilityCommands.InvSee::new);
        declare("near", "dripleaf.near", List.of(), 0L, 0d, "List nearby players",
                UtilityCommands.Near::new);
        declare("seen", "dripleaf.seen", List.of(), 0L, 0d, "When a player was last online",
                UtilityCommands.Seen::new);
        declare("playtime", "dripleaf.playtime", List.of("pt"), 0L, 0d, "Show playtime",
                UtilityCommands.Playtime::new);
        declare("ping", "dripleaf.ping", List.of(), 0L, 0d, "Show latency",
                UtilityCommands.Ping::new);
        declare("afk", "dripleaf.afk", List.of(), 0L, 0d, "Toggle AFK",
                (s, spec) -> new UtilityCommands.Afk(s, spec, this));
        declare("sudo", "dripleaf.sudo", List.of(), 0L, 0d, "Run a command as a player",
                UtilityCommands.Sudo::new);
        declare("broadcast", "dripleaf.broadcast", List.of("bc"), 0L, 0d,
                "Broadcast a message", UtilityCommands.Broadcast::new);
        declare("gm", "dripleaf.gamemode", List.of(), 0L, 0d, "Change game mode",
                UtilityCommands.GameModeCommand::new);
        declare("more", "dripleaf.more", List.of(), 0L, 0d, "Fill the held stack",
                UtilityCommands.More::new);
        declare("itemname", "dripleaf.itemname", List.of(), 0L, 0d, "Rename the held item",
                (s, spec) -> new UtilityCommands.ItemText(s, spec, false));
        declare("itemlore", "dripleaf.itemlore", List.of(), 0L, 0d, "Set the held item's lore",
                (s, spec) -> new UtilityCommands.ItemText(s, spec, true));
    }

    private void workstation(String id, String permission, List<String> aliases,
                             InventoryType type, String description) {
        declare(id, permission, aliases, 0L, 0d, description,
                (s, spec) -> new UtilityCommands.Workstation(s, spec, type));
    }

    private void declare(String id, String permission, List<String> aliases, long cooldown,
                         double warmup, String description,
                         java.util.function.BiFunction<Services, CommandSpec,
                                 net.dripleaf.core.common.command.DripleafCommand> factory) {
        commands.declare(new CommandSpec(id, false, permission, aliases, cooldown, warmup,
                description), factory);
    }

    // ------------------------------------------------------------- accessors

    public CoreCurrencies currencies() {
        return currencies;
    }

    public ShopService shops() {
        return shops;
    }

    public WarpService warps() {
        return warps;
    }

    public HomeService homes() {
        return homes;
    }

    public KitService kits() {
        return kits;
    }

    public TeleportService teleports() {
        return teleports;
    }

    public SocialService social() {
        return social;
    }

    public LeaderboardService leaderboards() {
        return leaderboards;
    }

    public AdminScreens admin() {
        return admin;
    }

    public CommandRegistry commands() {
        return commands;
    }
}
