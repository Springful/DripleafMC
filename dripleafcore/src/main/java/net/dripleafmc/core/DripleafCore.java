package net.dripleafmc.core;

import net.dripleafmc.core.bounty.Bounties;
import net.dripleafmc.core.cmd.CoreCommands;
import net.dripleafmc.core.combat.Combat;
import net.dripleafmc.core.config.Cfg;
import net.dripleafmc.core.config.Lang;
import net.dripleafmc.core.econ.Money;
import net.dripleafmc.core.homes.Homes;
import net.dripleafmc.core.hook.Floodgate;
import net.dripleafmc.core.hook.Placeholders;
import net.dripleafmc.core.listen.CoreListener;
import net.dripleafmc.core.profile.Profiles;
import net.dripleafmc.core.quickbuy.QuickBuy;
import net.dripleafmc.core.screen.Screens;
import net.dripleafmc.core.sell.Sell;
import net.dripleafmc.core.shards.ShardShop;
import net.dripleafmc.core.shards.Shards;
import net.dripleafmc.core.shop.Shop;
import net.dripleafmc.core.storage.Db;
import net.dripleafmc.core.tp.Rtp;
import net.dripleafmc.core.tp.Teleports;
import net.dripleafmc.core.tp.Tpa;
import net.dripleafmc.core.ui.MenuListener;
import net.dripleafmc.core.ui.Prompt;
import net.dripleafmc.core.ui.Ui;
import net.dripleafmc.core.worth.Worth;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * DripleafCore — economy, shop, shards, homes and the dialog UI layer for DripleafMC.
 *
 * Threading contract for everything below: gameplay state lives on the main thread,
 * the database is only ever touched from {@link #io}, and dialog callbacks are bounced
 * back to the main thread before they touch anything Bukkit owns.
 */
public final class DripleafCore extends JavaPlugin {

    private ExecutorService io;
    private Db db;

    private final Cfg cfg = new Cfg();
    private final Lang lang = new Lang();

    private Profiles profiles;
    private Homes homes;
    private Money money;
    private Worth worth;
    private Shop shop;
    private QuickBuy quickBuy;
    private ShardShop shardShop;
    private Sell sell;
    private Shards shards;
    private Combat combat;
    private Teleports teleports;
    private Rtp rtp;
    private Tpa tpa;
    private Bounties bounties;
    private Ui ui;
    private Prompt prompt;
    private Screens screens;
    private CoreCommands commands;

    @Override
    public void onEnable() {
        saveDefaultResources();
        reloadConfig();
        loadConfigs();

        io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "DripleafCore-IO");
            t.setDaemon(true);
            return t;
        });

        try {
            db = new Db(getConfig(), getDataFolder(), getLogger());
        } catch (SQLException e) {
            getLogger().severe("[DripleafCore] storage failed to start: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Floodgate.detect();

        profiles = new Profiles(db, io, getLogger());
        homes = new Homes(db, io, cfg, getLogger());
        money = new Money(getLogger());
        money.hook();

        worth = new Worth(cfg, getLogger());
        worth.load(new File(getDataFolder(), "worth.yml"));

        shop = new Shop(getLogger());
        shop.load(new File(getDataFolder(), "shop.yml"), worth::buyPrice);

        quickBuy = new QuickBuy(db, io, cfg.quickBuySlots, getLogger());

        shardShop = new ShardShop(getLogger());
        shardShop.load(new File(getDataFolder(), "shard-shop.yml"));

        sell = new Sell(worth, money, profiles, cfg, lang, db, io, getLogger());
        shards = new Shards(this, cfg, lang, profiles);
        combat = new Combat(cfg, lang);
        teleports = new Teleports(this, cfg, lang, combat);
        rtp = new Rtp(this, cfg, lang, teleports);
        rtp.load(getConfig());
        tpa = new Tpa(cfg, lang);
        bounties = new Bounties(db, io, cfg, getLogger());
        bounties.loadAll();

        prompt = new Prompt(this);
        ui = new Ui(this, cfg, profiles, prompt);
        screens = new Screens(this);
        commands = new CoreCommands(this);
        commands.register();

        getServer().getPluginManager().registerEvents(new CoreListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(prompt, this);

        shards.start();

        long flushTicks = cfg.flushInterval * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> profiles.flush(), flushTicks, flushTicks);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new Placeholders(this).register();
            getLogger().info("[DripleafCore] PlaceholderAPI expansion registered.");
        }

        getLogger().info("[DripleafCore] enabled.");
    }

    @Override
    public void onDisable() {
        if (shards != null) shards.stop();
        if (profiles != null) {
            profiles.flush();
            for (var player : getServer().getOnlinePlayers()) profiles.unload(player.getUniqueId());
        }
        if (io != null) {
            io.shutdown();
            try {
                if (!io.awaitTermination(10, TimeUnit.SECONDS)) io.shutdownNow();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                io.shutdownNow();
            }
        }
        if (db != null) db.close();
        getLogger().info("[DripleafCore] disabled.");
    }

    private void saveDefaultResources() {
        saveDefaultConfig();
        for (String name : new String[]{"messages.yml", "worth.yml", "shop.yml", "shard-shop.yml", "commands.yml"}) {
            if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
        }
    }

    private void loadConfigs() {
        lang.load(new File(getDataFolder(), "messages.yml"));
        YamlConfiguration worthYml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "worth.yml"));
        cfg.load(getConfig(), worthYml);
    }

    /** Reloads configs in place. Commands stay as registered — those need a restart. */
    public void reloadAll() {
        reloadConfig();
        loadConfigs();
        worth.load(new File(getDataFolder(), "worth.yml"));
        shop.load(new File(getDataFolder(), "shop.yml"), worth::buyPrice);
        shardShop.load(new File(getDataFolder(), "shard-shop.yml"));
        rtp.load(getConfig());
    }

    // ------------------------------------------------------------ accessors

    public Cfg cfg() { return cfg; }
    public Lang lang() { return lang; }
    public Profiles profiles() { return profiles; }
    public Homes homes() { return homes; }
    public Money money() { return money; }
    public Worth worth() { return worth; }
    public Shop shop() { return shop; }
    public QuickBuy quickBuy() { return quickBuy; }
    public ShardShop shardShop() { return shardShop; }
    public Sell sell() { return sell; }
    public Shards shards() { return shards; }
    public Combat combat() { return combat; }
    public Teleports teleports() { return teleports; }
    public Rtp rtp() { return rtp; }
    public Tpa tpa() { return tpa; }
    public Bounties bounties() { return bounties; }
    public Ui ui() { return ui; }
    public Screens screens() { return screens; }
    public CoreCommands commands() { return commands; }
}
