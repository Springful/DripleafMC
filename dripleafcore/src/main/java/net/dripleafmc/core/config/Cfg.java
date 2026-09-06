package net.dripleafmc.core.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Hot config values are read once into final-ish fields. Anything touched inside an
 * event handler or a repeating task must live here, not behind a getString() call.
 */
public final class Cfg {

    public boolean dialogsEnabled;
    public boolean chestForBedrock;
    public int callbackLifetimeMinutes;
    public int buttonsPerPage;
    public int menuColumns;

    public boolean sellActionBar;
    public boolean buyActionBar;

    public boolean shardsEnabled;
    public int shardsPerTick;
    public int shardTickSeconds;
    public int shardsPerKill;
    public int shardKillCooldown;
    public int shardActiveWindow;
    public int shardBooster;
    public java.util.Set<String> shardWorldBlacklist = java.util.Set.of();

    public double sellRatio;
    public double rebirthStep;
    public double maxMultiplier;
    public int historySize;
    public boolean worthLore;

    public String shopPermission;
    public boolean quickBuyEnabled;
    public int quickBuySlots;
    public int quickBuyMaxPerPurchase;

    public int homeDefaultSlots;
    public int homeMaxSlots;
    public int homeWarmup;
    public boolean cancelOnMove;

    public int rtpWarmup;
    public int rtpCooldown;
    public int rtpAttempts;

    public int tpaExpiry;
    public int tpaWarmup;

    public boolean combatEnabled;
    public int combatSeconds;
    public boolean killOnLogout;
    public java.util.Set<String> combatBlocked = java.util.Set.of();

    public boolean bountyEnabled;
    public double bountyMin;
    public double bountyMax;
    public double bountyTax;

    public int boardRefresh;
    public int boardSize;

    public int flushInterval;

    public void load(FileConfiguration c, FileConfiguration worth) {
        dialogsEnabled = c.getBoolean("ui.dialogs", true);
        chestForBedrock = c.getBoolean("ui.force-chest-menus-for-bedrock", true);
        callbackLifetimeMinutes = c.getInt("ui.callback-lifetime-minutes", 10);
        buttonsPerPage = Math.max(4, c.getInt("ui.buttons-per-page", 20));
        menuColumns = Math.max(1, Math.min(4, c.getInt("ui.columns", 2)));

        sellActionBar = c.getBoolean("economy.sell-message-actionbar", true);
        buyActionBar = c.getBoolean("economy.buy-message-actionbar", true);

        shardsEnabled = c.getBoolean("shards.enabled", true);
        shardsPerTick = c.getInt("shards.per-tick", 1);
        shardTickSeconds = Math.max(20, c.getInt("shards.tick-seconds", 120));
        shardsPerKill = c.getInt("shards.per-kill", 10);
        shardKillCooldown = c.getInt("shards.kill-cooldown-seconds", 300);
        shardActiveWindow = c.getInt("shards.active-window-seconds", 180);
        shardBooster = Math.max(1, c.getInt("shards.booster-multiplier", 4));
        shardWorldBlacklist = java.util.Set.copyOf(c.getStringList("shards.worlds-blacklist"));

        sellRatio = worth.getDouble("settings.sell-ratio", 0.65);
        rebirthStep = c.getDouble("sell.rebirth-multiplier-step", 0.05);
        maxMultiplier = c.getDouble("sell.max-multiplier", 3.0);
        historySize = c.getInt("sell.history-size", 50);
        worthLore = c.getBoolean("sell.worth-lore", true);

        shopPermission = c.getString("shop.permission", "dripleaf.shop.use");
        quickBuyEnabled = c.getBoolean("quick-buy.enabled", true);
        quickBuySlots = Math.max(9, Math.min(45, c.getInt("quick-buy.slots", 45)));
        quickBuyMaxPerPurchase = Math.max(1, c.getInt("quick-buy.max-per-purchase", 64));

        homeDefaultSlots = c.getInt("homes.default-slots", 2);
        homeMaxSlots = c.getInt("homes.max-slots", 10);
        homeWarmup = c.getInt("homes.teleport-warmup-seconds", 3);
        cancelOnMove = c.getBoolean("homes.cancel-warmup-on-move", true);

        rtpWarmup = c.getInt("rtp.warmup-seconds", 3);
        rtpCooldown = c.getInt("rtp.cooldown-seconds", 60);
        rtpAttempts = Math.max(4, c.getInt("rtp.max-attempts", 24));

        tpaExpiry = c.getInt("tpa.request-expiry-seconds", 90);
        tpaWarmup = c.getInt("tpa.warmup-seconds", 3);

        combatEnabled = c.getBoolean("combat.enabled", true);
        combatSeconds = c.getInt("combat.tag-seconds", 16);
        killOnLogout = c.getBoolean("combat.kill-on-logout", true);
        combatBlocked = java.util.Set.copyOf(c.getStringList("combat.blocked-commands"));

        bountyEnabled = c.getBoolean("bounty.enabled", true);
        bountyMin = c.getDouble("bounty.minimum", 1000);
        bountyMax = c.getDouble("bounty.maximum", 10_000_000);
        bountyTax = c.getDouble("bounty.tax", 0.05);

        boardRefresh = Math.max(30, c.getInt("leaderboard.refresh-seconds", 120));
        boardSize = Math.max(3, Math.min(25, c.getInt("leaderboard.size", 10)));

        flushInterval = Math.max(15, c.getInt("storage.flush-interval", 60));
    }
}
