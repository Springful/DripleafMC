package net.dripleaf.core.common.ui;

/**
 * A value the screen asks the player for.
 *
 * <p>A dialog renders this natively as a text field. A chest cannot, so the
 * chest renderer degrades it to a chat prompt — the player is asked to type the
 * value and the screen reopens with it filled in. That degradation is the whole
 * reason inputs are described rather than constructed: the screen author states
 * what is needed, and each renderer meets it the best way it can.
 *
 * @param key         identifier the action reads the value back by
 * @param label       MiniMessage label shown beside the field
 * @param initial     pre-filled value, may be empty
 * @param maxLength   character cap
 * @param placeholder hint shown in the chat prompt when the field is empty
 */
public record ScreenInput(String key, String label, String initial, int maxLength,
                          String placeholder) {

    public static ScreenInput text(String key, String label) {
        return new ScreenInput(key, label, "", 64, "");
    }

    public static ScreenInput text(String key, String label, String placeholder) {
        return new ScreenInput(key, label, "", 64, placeholder);
    }

    public ScreenInput withInitial(String value) {
        return new ScreenInput(key, label, value == null ? "" : value, maxLength, placeholder);
    }

    public ScreenInput withMaxLength(int value) {
        return new ScreenInput(key, label, initial, Math.max(1, value), placeholder);
    }
}
