package net.dripleaf.core.common.storage;

import org.bukkit.Location;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One player's persisted state, held in memory between join and quit.
 *
 * <p>Mutation happens on the main thread; the snapshot taken for a flush is
 * built on the main thread too and only the serialisation runs off it. The
 * {@code dirty} flag means an idle player costs nothing at autosave time.
 */
public final class PlayerData {

    private final UUID uuid;
    private volatile boolean dirty;

    private String lastKnownName = "";
    private long firstSeen;
    private long lastSeen;
    private long playtimeMillis;

    private BigDecimal shards = BigDecimal.ZERO;
    private BigDecimal souls = BigDecimal.ZERO;

    private int rebirthTier;
    private int rebirthTotal;
    private long rebirthLast;
    private long rebirthFirst;
    private BigDecimal rebirthSpent = BigDecimal.ZERO;
    private BigDecimal soulsEarned = BigDecimal.ZERO;
    private String lastPath = "";
    /** Tier number to epoch millis, for the "completed on" line in the tier browser. */
    private final Map<Integer, Long> tierDates = new LinkedHashMap<>();

    private final Map<String, Location> homes = new LinkedHashMap<>();
    private final Map<String, Long> kitUses = new LinkedHashMap<>();
    private Location lastLocation;
    private Location deathLocation;

    /** {@code null} means "follow the server default" — see {@code common.ui}. */
    private String uiPreference;

    private String nickname = "";
    private final Set<UUID> ignored = new LinkedHashSet<>();

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    // -------------------------------------------------------------- identity

    public String lastKnownName() {
        return lastKnownName;
    }

    public void lastKnownName(String name) {
        this.lastKnownName = name == null ? "" : name;
        markDirty();
    }

    public long firstSeen() {
        return firstSeen;
    }

    public void firstSeen(long value) {
        this.firstSeen = value;
        markDirty();
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void lastSeen(long value) {
        this.lastSeen = value;
        markDirty();
    }

    public long playtimeMillis() {
        return playtimeMillis;
    }

    public void addPlaytime(long millis) {
        if (millis > 0) {
            this.playtimeMillis += millis;
            markDirty();
        }
    }

    public void playtimeMillis(long value) {
        this.playtimeMillis = value;
        markDirty();
    }

    // ------------------------------------------------------------ currencies

    public BigDecimal shards() {
        return shards;
    }

    public void shards(BigDecimal value) {
        this.shards = value == null ? BigDecimal.ZERO : value;
        markDirty();
    }

    public BigDecimal souls() {
        return souls;
    }

    public void souls(BigDecimal value) {
        this.souls = value == null ? BigDecimal.ZERO : value;
        markDirty();
    }

    // --------------------------------------------------------------- rebirth

    public int rebirthTier() {
        return rebirthTier;
    }

    public void rebirthTier(int tier) {
        this.rebirthTier = Math.max(0, tier);
        markDirty();
    }

    public int rebirthTotal() {
        return rebirthTotal;
    }

    public void rebirthTotal(int total) {
        this.rebirthTotal = Math.max(0, total);
        markDirty();
    }

    public long rebirthLast() {
        return rebirthLast;
    }

    public void rebirthLast(long value) {
        this.rebirthLast = value;
        markDirty();
    }

    public long rebirthFirst() {
        return rebirthFirst;
    }

    public void rebirthFirst(long value) {
        this.rebirthFirst = value;
        markDirty();
    }

    public BigDecimal rebirthSpent() {
        return rebirthSpent;
    }

    public void addRebirthSpent(BigDecimal amount) {
        if (amount != null) {
            this.rebirthSpent = this.rebirthSpent.add(amount);
            markDirty();
        }
    }

    public void rebirthSpent(BigDecimal value) {
        this.rebirthSpent = value == null ? BigDecimal.ZERO : value;
        markDirty();
    }

    public BigDecimal soulsEarned() {
        return soulsEarned;
    }

    public void addSoulsEarned(BigDecimal amount) {
        if (amount != null) {
            this.soulsEarned = this.soulsEarned.add(amount);
            markDirty();
        }
    }

    public void soulsEarned(BigDecimal value) {
        this.soulsEarned = value == null ? BigDecimal.ZERO : value;
        markDirty();
    }

    public String lastPath() {
        return lastPath;
    }

    public void lastPath(String path) {
        this.lastPath = path == null ? "" : path;
        markDirty();
    }

    public Map<Integer, Long> tierDates() {
        return tierDates;
    }

    public void recordTierDate(int tier, long epochMillis) {
        tierDates.put(tier, epochMillis);
        markDirty();
    }

    // ----------------------------------------------------------------- homes

    public Map<String, Location> homes() {
        return homes;
    }

    public Map<String, Long> kitUses() {
        return kitUses;
    }

    public Location lastLocation() {
        return lastLocation;
    }

    public void lastLocation(Location location) {
        this.lastLocation = location;
        markDirty();
    }

    public Location deathLocation() {
        return deathLocation;
    }

    public void deathLocation(Location location) {
        this.deathLocation = location;
        markDirty();
    }

    // -------------------------------------------------------- ui preference

    /** {@code DIALOG}, {@code CHEST}, {@code AUTO}, or {@code null} for the server default. */
    public String uiPreference() {
        return uiPreference;
    }

    public void uiPreference(String value) {
        this.uiPreference = value;
        markDirty();
    }

    // ------------------------------------------------------------- social

    /** Empty when the player uses their real name. */
    public String nickname() {
        return nickname;
    }

    public void nickname(String value) {
        this.nickname = value == null ? "" : value;
        markDirty();
    }

    /** Players this one has muted with {@code /ignore}. Persisted deliberately. */
    public Set<UUID> ignored() {
        return ignored;
    }
}
