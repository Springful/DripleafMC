package net.dripleafmc.core.profile;

import net.dripleafmc.core.storage.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Profile cache and persistence.
 *
 * Reads happen once at pre-login (off the main thread) and everything after that is
 * a map lookup. Writes are batched: the flush task walks the cache, takes only the
 * dirty profiles, and sends them as one JDBC batch.
 */
public final class Profiles {

    private static final String UPSERT_SQLITE =
            "INSERT INTO dl_profile (uuid,name,shards,kills,deaths,mob_kills,blocks_broken,blocks_placed," +
            "money_spent,money_made,playtime,killstreak,best_streak,booster_until,flags,last_seen) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
            "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,shards=excluded.shards,kills=excluded.kills," +
            "deaths=excluded.deaths,mob_kills=excluded.mob_kills,blocks_broken=excluded.blocks_broken," +
            "blocks_placed=excluded.blocks_placed,money_spent=excluded.money_spent,money_made=excluded.money_made," +
            "playtime=excluded.playtime,killstreak=excluded.killstreak,best_streak=excluded.best_streak," +
            "booster_until=excluded.booster_until,flags=excluded.flags,last_seen=excluded.last_seen";

    private static final String UPSERT_MYSQL =
            "INSERT INTO dl_profile (uuid,name,shards,kills,deaths,mob_kills,blocks_broken,blocks_placed," +
            "money_spent,money_made,playtime,killstreak,best_streak,booster_until,flags,last_seen) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
            "ON DUPLICATE KEY UPDATE name=VALUES(name),shards=VALUES(shards),kills=VALUES(kills)," +
            "deaths=VALUES(deaths),mob_kills=VALUES(mob_kills),blocks_broken=VALUES(blocks_broken)," +
            "blocks_placed=VALUES(blocks_placed),money_spent=VALUES(money_spent),money_made=VALUES(money_made)," +
            "playtime=VALUES(playtime),killstreak=VALUES(killstreak),best_streak=VALUES(best_streak)," +
            "booster_until=VALUES(booster_until),flags=VALUES(flags),last_seen=VALUES(last_seen)";

    private final Map<UUID, Profile> cache = new ConcurrentHashMap<>(64);
    private final Db db;
    private final ExecutorService io;
    private final Logger log;
    private final String upsert;

    public Profiles(Db db, ExecutorService io, Logger log) {
        this.db = db;
        this.io = io;
        this.log = log;
        this.upsert = db.kind() == Db.Kind.MYSQL ? UPSERT_MYSQL : UPSERT_SQLITE;
    }

    public Profile get(UUID uuid) {
        return cache.get(uuid);
    }

    public Collection<Profile> online() {
        return cache.values();
    }

    /** Blocking load — call from AsyncPlayerPreLoginEvent only. */
    public Profile loadBlocking(UUID uuid, String name) {
        Profile cached = cache.get(uuid);
        if (cached != null) {
            cached.name = name;
            return cached;
        }
        Profile p = new Profile(uuid, name);
        Connection c = null;
        try {
            c = db.borrow();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM dl_profile WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) read(p, rs);
                }
            }
        } catch (SQLException e) {
            log.log(Level.SEVERE, "[DripleafCore] failed to load profile for " + name, e);
        } finally {
            if (c != null) db.giveBack(c);
        }
        cache.put(uuid, p);
        return p;
    }

    private static void read(Profile p, ResultSet rs) throws SQLException {
        p.shards = rs.getLong("shards");
        p.kills = rs.getInt("kills");
        p.deaths = rs.getInt("deaths");
        p.mobKills = rs.getInt("mob_kills");
        p.blocksBroken = rs.getLong("blocks_broken");
        p.blocksPlaced = rs.getLong("blocks_placed");
        p.moneySpent = rs.getDouble("money_spent");
        p.moneyMade = rs.getDouble("money_made");
        p.playtime = rs.getLong("playtime");
        p.killStreak = rs.getInt("killstreak");
        p.bestStreak = rs.getInt("best_streak");
        p.boosterUntil = rs.getLong("booster_until");
        p.flags = rs.getInt("flags");
        p.lastSeen = rs.getLong("last_seen");
    }

    public void unload(UUID uuid) {
        Profile p = cache.remove(uuid);
        if (p == null) return;
        p.bankSession();
        p.lastSeen = System.currentTimeMillis();
        p.touch();
        io.execute(() -> write(List.of(p)));
    }

    /** Writes every dirty profile in one batch. Runs on the IO thread. */
    public void flush() {
        List<Profile> dirty = new ArrayList<>();
        for (Profile p : cache.values()) {
            p.bankSession();
            if (p.dirty()) dirty.add(p);
        }
        if (dirty.isEmpty()) return;
        io.execute(() -> write(dirty));
    }

    private void write(Collection<Profile> batch) {
        Connection c = null;
        try {
            c = db.borrow();
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(upsert)) {
                for (Profile p : batch) {
                    int i = 1;
                    ps.setString(i++, p.uuid.toString());
                    ps.setString(i++, p.name);
                    ps.setLong(i++, p.shards);
                    ps.setInt(i++, p.kills);
                    ps.setInt(i++, p.deaths);
                    ps.setInt(i++, p.mobKills);
                    ps.setLong(i++, p.blocksBroken);
                    ps.setLong(i++, p.blocksPlaced);
                    ps.setDouble(i++, p.moneySpent);
                    ps.setDouble(i++, p.moneyMade);
                    ps.setLong(i++, p.playtime);
                    ps.setInt(i++, p.killStreak);
                    ps.setInt(i++, p.bestStreak);
                    ps.setLong(i++, p.boosterUntil);
                    ps.setInt(i++, p.flags);
                    ps.setLong(i, p.lastSeen == 0 ? System.currentTimeMillis() : p.lastSeen);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
            c.setAutoCommit(auto);
            for (Profile p : batch) p.clean();
        } catch (SQLException e) {
            log.log(Level.SEVERE, "[DripleafCore] profile flush failed", e);
        } finally {
            if (c != null) db.giveBack(c);
        }
    }

    /** Leaderboard read. Column is validated by the caller against a fixed set. */
    public CompletableFuture<Map<String, Double>> top(String column, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Double> out = new LinkedHashMap<>(limit);
            Connection c = null;
            try {
                c = db.borrow();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT name," + column + " AS v FROM dl_profile WHERE name IS NOT NULL " +
                        "ORDER BY " + column + " DESC LIMIT ?")) {
                    ps.setInt(1, limit);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) out.put(rs.getString("name"), rs.getDouble("v"));
                    }
                }
            } catch (SQLException e) {
                log.log(Level.WARNING, "[DripleafCore] leaderboard query failed", e);
            } finally {
                if (c != null) db.giveBack(c);
            }
            return out;
        }, io);
    }
}
