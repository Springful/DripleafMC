package net.dripleafmc.rebirth.data;

/**
 * A player's rebirth state.
 *
 * @param tier        completed tiers (0 = never rebirthed)
 * @param path        the path used for the most recent rebirth, or null
 * @param lastRebirth epoch millis of the most recent rebirth, 0 if never
 * @param total       lifetime rebirth count, including any resets
 */
public record Profile(int tier, String path, long lastRebirth, int total) {

    public static final Profile EMPTY = new Profile(0, null, 0L, 0);

    public Profile advance(int newTier, String newPath, long now) {
        return new Profile(newTier, newPath, now, total + 1);
    }

    public Profile withTier(int newTier) {
        return new Profile(newTier, path, lastRebirth, total);
    }
}
