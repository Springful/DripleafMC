package net.dripleaf.core.rebirth;

import net.dripleaf.core.common.config.Cfg;

import java.util.List;
import java.util.Locale;

/** Typed snapshot of {@code rebirth/rebirth.yml}. Rebuilt on reload, never read live. */
public final class RebirthSettings {

    /** Where a player's current tier is read from. */
    public enum TierSource { DATA, PERMISSION }

    private boolean sacrificeMoney = true;
    private boolean sacrificeMcMmo = true;
    private String mcMmoCommand = "mmoedit <player> all 0";
    private TierSource tierSource = TierSource.DATA;
    private String permissionFormat = "group.rebirth-<tier>";
    private int infoVisibilityBlock = 5;
    private boolean broadcast = true;
    private boolean broadcastStandard = true;
    private boolean broadcastSoul = true;
    private int typeToConfirmAbove = 15;
    private long cooldownSeconds;
    private long requirementCacheMillis = 2000L;
    private boolean migrateFromLuckPerms = true;
    private List<String> soulShopCommands = List.of("soulshop");

    public void load(Cfg cfg) {
        Cfg sacrifice = cfg.childOrEmpty("sacrifice");
        this.sacrificeMoney = sacrifice.bool("money", true);
        this.sacrificeMcMmo = sacrifice.bool("mcmmo", true);
        this.mcMmoCommand = sacrifice.string("mcmmo-command", "mmoedit <player> all 0");

        this.tierSource = "permission".equalsIgnoreCase(
                cfg.string("tier-source", "data").trim().toLowerCase(Locale.ROOT))
                ? TierSource.PERMISSION : TierSource.DATA;
        this.permissionFormat = cfg.string("permission-format", "group.rebirth-<tier>");
        this.infoVisibilityBlock = cfg.integer("info-visibility-block", 5, 1, 100);

        Cfg broadcastCfg = cfg.childOrEmpty("broadcast");
        this.broadcast = broadcastCfg.bool("enabled", true);
        this.broadcastStandard = broadcastCfg.bool("standard", true);
        this.broadcastSoul = broadcastCfg.bool("soul", true);

        this.typeToConfirmAbove = cfg.integer("type-to-confirm-above", 15, 0, 10_000);
        this.cooldownSeconds = (long) cfg.number("cooldown-seconds", 0d, 0d, 2_592_000d);
        this.requirementCacheMillis =
                (long) cfg.number("requirement-cache-millis", 2000d, 0d, 60_000d);
        this.migrateFromLuckPerms = cfg.bool("migrate-from-luckperms", true);

        List<String> commands = cfg.stringList("soul-shop-command");
        if (!commands.isEmpty()) {
            this.soulShopCommands = List.copyOf(commands);
        }
    }

    public boolean sacrificeMoney() {
        return sacrificeMoney;
    }

    public boolean sacrificeMcMmo() {
        return sacrificeMcMmo;
    }

    public String mcMmoCommand() {
        return mcMmoCommand;
    }

    public TierSource tierSource() {
        return tierSource;
    }

    public String permissionFormat() {
        return permissionFormat;
    }

    /** Tiers become visible in blocks of this size. Five, by default. */
    public int infoVisibilityBlock() {
        return infoVisibilityBlock;
    }

    public boolean broadcast() {
        return broadcast;
    }

    public boolean broadcastFor(RebirthPath path) {
        if (!broadcast) {
            return false;
        }
        return path == RebirthPath.SOUL ? broadcastSoul : broadcastStandard;
    }

    /** Above this tier the confirmation requires typing the numeral. */
    public int typeToConfirmAbove() {
        return typeToConfirmAbove;
    }

    public long cooldownSeconds() {
        return cooldownSeconds;
    }

    public long requirementCacheMillis() {
        return requirementCacheMillis;
    }

    public boolean migrateFromLuckPerms() {
        return migrateFromLuckPerms;
    }

    public List<String> soulShopCommands() {
        return soulShopCommands;
    }
}
