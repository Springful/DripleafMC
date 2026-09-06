package net.dripleafmc.core.profile;

import java.util.UUID;

/**
 * In-memory player state. Mutated on the main thread, read by the flush task, so
 * every field is either volatile or only ever touched under the main thread.
 * The dirty flag is what keeps the flush batch small.
 */
public final class Profile {

    // Setting bits, stored in one int column.
    public static final int FLAG_DIALOGS      = 1;
    public static final int FLAG_TPA_ALLOWED  = 1 << 1;
    public static final int FLAG_SELL_ACTION  = 1 << 2;
    public static final int FLAG_BUY_ACTION   = 1 << 3;
    public static final int FLAG_PUBLIC_STATS = 1 << 4;
    public static final int DEFAULT_FLAGS =
            FLAG_DIALOGS | FLAG_TPA_ALLOWED | FLAG_SELL_ACTION | FLAG_BUY_ACTION | FLAG_PUBLIC_STATS;

    public final UUID uuid;
    public String name;

    public long shards;
    public int kills;
    public int deaths;
    public int mobKills;
    public long blocksBroken;
    public long blocksPlaced;
    public double moneySpent;
    public double moneyMade;
    public long playtime;          // seconds, excluding the current session
    public int killStreak;
    public int bestStreak;
    public long boosterUntil;      // epoch millis
    public int flags = DEFAULT_FLAGS;
    public long lastSeen;

    private transient long sessionStart;
    private volatile boolean dirty;

    public Profile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public void beginSession() {
        sessionStart = System.currentTimeMillis();
    }

    /** Folds the current session into the stored total. Called on quit and on flush. */
    public void bankSession() {
        if (sessionStart == 0) return;
        long now = System.currentTimeMillis();
        playtime += (now - sessionStart) / 1000L;
        sessionStart = now;
        dirty = true;
    }

    public long totalPlaytime() {
        long live = sessionStart == 0 ? 0 : (System.currentTimeMillis() - sessionStart) / 1000L;
        return playtime + live;
    }

    public boolean flag(int bit) {
        return (flags & bit) != 0;
    }

    public void flag(int bit, boolean on) {
        int next = on ? (flags | bit) : (flags & ~bit);
        if (next != flags) {
            flags = next;
            dirty = true;
        }
    }

    public boolean boosterActive() {
        return boosterUntil > System.currentTimeMillis();
    }

    public void addShards(long amount) {
        if (amount == 0) return;
        shards = Math.max(0, shards + amount);
        dirty = true;
    }

    public void touch() {
        dirty = true;
    }

    public boolean dirty() {
        return dirty;
    }

    public void clean() {
        dirty = false;
    }
}
