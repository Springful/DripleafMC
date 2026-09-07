package net.dripleaf.core.common.ui;

import java.util.Locale;

/**
 * How a screen is drawn for one player.
 *
 * <p>Every menu in this plugin is authored once as a {@link Screen} and can be
 * rendered either way. {@code DIALOG} is the house default — dialogs are the
 * richer surface, they scroll, they take real text input, and they do not eat
 * an inventory slot. {@code CHEST} exists because Bedrock clients coming
 * through Geyser do not render native dialogs reliably, and a Bedrock player
 * staring at a blank screen is worse than any amount of design purity.
 */
public enum UiMode {

    /** Native Paper dialogs. */
    DIALOG,
    /** Chest GUI. Always available, always works on Bedrock. */
    CHEST,
    /**
     * Let the server decide per player: Bedrock gets {@link #CHEST}, everyone
     * else gets {@link #DIALOG}. This is what a player who has never touched
     * {@code /uimode} is on.
     */
    AUTO;

    public static UiMode from(String raw, UiMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "DIALOG", "DIALOGS", "MENU_DIALOG" -> DIALOG;
            case "CHEST", "GUI", "MENU", "CHESTGUI", "CHEST_GUI" -> CHEST;
            case "AUTO", "DEFAULT", "SERVER" -> AUTO;
            default -> fallback;
        };
    }

    /** {@code AUTO} is never a renderer — resolve it before you draw. */
    public boolean concrete() {
        return this != AUTO;
    }
}
