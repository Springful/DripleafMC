package net.dripleafmc.rebirth.config;

import net.dripleafmc.rebirth.util.Numbers;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

/** Typed, immutable snapshot of config.yml. Rebuilt on reload, never read live. */
public final class Settings {

    public enum UiMode { MENU, DIALOG }

    private final UiMode uiMode;
    private final boolean dialogFallbackToMenu;
    private final int autoSaveMinutes;
    private final Numbers numbers;
    private final int maxTier;
    private final long cooldownSeconds;
    private final boolean revalidateOnConfirm;
    private final boolean sacrificeFirst;
    private final boolean broadcast;
    private final long placeholderCacheMillis;
    private final String soundOpen;
    private final String soundDenied;
    private final String soundStandard;
    private final String soundSoul;
    private final String noneRoman;
    private final String maxedText;
    private final boolean debug;

    public Settings(FileConfiguration config) {
        String mode = config.getString("ui.mode", "MENU").toUpperCase(Locale.ROOT);
        this.uiMode = "DIALOG".equals(mode) ? UiMode.DIALOG : UiMode.MENU;
        this.dialogFallbackToMenu = config.getBoolean("ui.fallback-to-menu-for-unsupported", true);
        this.autoSaveMinutes = config.getInt("storage.auto-save-minutes", 5);
        this.numbers = new Numbers(
                config.getString("economy.format", "#,##0"),
                config.getBoolean("economy.compact-format", true));
        this.maxTier = config.getInt("rebirth.max-tier", 25);
        this.cooldownSeconds = config.getLong("rebirth.cooldown-seconds", 0L);
        this.revalidateOnConfirm = config.getBoolean("rebirth.revalidate-on-confirm", true);
        this.sacrificeFirst = config.getBoolean("rebirth.sacrifice-first", true);
        this.broadcast = config.getBoolean("rebirth.broadcast", true);
        this.placeholderCacheMillis = config.getLong("requirements.placeholder-cache-millis", 250L);
        this.soundOpen = config.getString("sounds.open", "");
        this.soundDenied = config.getString("sounds.denied", "");
        this.soundStandard = config.getString("sounds.standard-success", "");
        this.soundSoul = config.getString("sounds.soul-success", "");
        this.noneRoman = config.getString("placeholders.none-roman", "-");
        this.maxedText = config.getString("placeholders.maxed", "MAX");
        this.debug = config.getBoolean("debug", false);
    }

    public UiMode uiMode() { return uiMode; }
    public boolean dialogFallbackToMenu() { return dialogFallbackToMenu; }
    public int autoSaveMinutes() { return autoSaveMinutes; }
    public Numbers numbers() { return numbers; }
    public int maxTier() { return maxTier; }
    public long cooldownSeconds() { return cooldownSeconds; }
    public boolean revalidateOnConfirm() { return revalidateOnConfirm; }
    public boolean sacrificeFirst() { return sacrificeFirst; }
    public boolean broadcast() { return broadcast; }
    public long placeholderCacheMillis() { return placeholderCacheMillis; }
    public String soundOpen() { return soundOpen; }
    public String soundDenied() { return soundDenied; }
    public String soundStandard() { return soundStandard; }
    public String soundSoul() { return soundSoul; }
    public String noneRoman() { return noneRoman; }
    public String maxedText() { return maxedText; }
    public boolean debug() { return debug; }
}
