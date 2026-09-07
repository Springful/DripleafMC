package net.dripleaf.core.common.sound;

import net.dripleaf.core.common.config.Cfg;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Every meaningful action gets a sound, and every sound is configurable.
 *
 * <p>Entries in {@code config.yml} look like {@code ui.button.click 1.0 1.2} —
 * key, volume, pitch, with volume and pitch optional. A blank value silences
 * that event without disabling the feature, which is the setting most servers
 * actually want when a sound annoys them.
 *
 * <p>Parsing happens once at load; playing is a map lookup and an Adventure
 * call, so this is cheap enough to sit on a per-tick warmup path.
 */
public final class SoundService {

    /** The event names read from {@code sounds:}, with their shipped defaults. */
    public static final String MENU_OPEN = "menu-open";
    public static final String PURCHASE_SUCCESS = "purchase-success";
    public static final String PURCHASE_FAILURE = "purchase-failure";
    public static final String WARMUP_TICK = "warmup-tick";
    public static final String WARMUP_CANCEL = "warmup-cancel";
    public static final String TELEPORT_COMPLETE = "teleport-complete";
    public static final String REBIRTH_STANDARD = "rebirth-standard";
    public static final String REBIRTH_SOUL = "rebirth-soul";
    public static final String ADMIN_APPLIED = "admin-applied";

    private final Map<String, Sound> sounds = new HashMap<>(16);

    public void load(Cfg cfg) {
        sounds.clear();
        for (String key : cfg.keys()) {
            Sound sound = parse(cfg.string(key, ""));
            if (sound != null) {
                sounds.put(key.toLowerCase(Locale.ROOT), sound);
            }
        }
    }

    public void play(Player player, String event) {
        Sound sound = sounds.get(event);
        if (sound != null) {
            player.playSound(sound, Sound.Emitter.self());
        }
    }

    /**
     * Plays {@code event} with its configured volume but an overridden pitch —
     * used by the warmup countdown, whose note rises with progress.
     */
    public void play(Player player, String event, float pitch) {
        Sound sound = sounds.get(event);
        if (sound == null) {
            return;
        }
        player.playSound(Sound.sound(sound.name(), sound.source(), sound.volume(),
                clampPitch(pitch)), Sound.Emitter.self());
    }

    /** @return {@code null} for a blank or unparseable entry — silence, not a crash */
    private static Sound parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split("\\s+");
        Key key;
        try {
            key = Key.key(parts[0].toLowerCase(Locale.ROOT));
        } catch (Exception ex) {
            return null;
        }
        float volume = parts.length > 1 ? parseFloat(parts[1], 1f) : 1f;
        float pitch = parts.length > 2 ? parseFloat(parts[2], 1f) : 1f;
        return Sound.sound(key, Sound.Source.MASTER, volume, clampPitch(pitch));
    }

    private static float parseFloat(String raw, float fallback) {
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static float clampPitch(float pitch) {
        return Math.max(0.5f, Math.min(2f, pitch));
    }
}
