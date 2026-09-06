package net.dripleafmc.core.bounty;

import net.dripleafmc.core.config.Cfg;
import net.dripleafmc.core.storage.Db;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bounties. The whole table is small enough to keep in memory, so lookups on kill
 * are a map probe and the database only sees writes.
 */
public final class Bounties {

    public record Bounty(UUID target, String targetName, double amount, String placedBy, long placedAt) {}

    private final Map<UUID, Bounty> active = new ConcurrentHashMap<>(32);
    private final Db db;
    private final ExecutorService io;
    private final Cfg cfg;
    private final Logger log;

    public Bounties(Db db, ExecutorService io, Cfg cfg, Logger log) {
        this.db = db;
        this.io = io;
        this.cfg = cfg;
        this.log = log;
    }

    public void loadAll() {
        io.execute(() -> {
            Connection c = null;
            try {
                c = db.borrow();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT target, target_name, amount, placed_by, ts FROM dl_bounty");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID target = UUID.fromString(rs.getString("target"));
                        active.put(target, new Bounty(target, rs.getString("target_name"),
                                rs.getDouble("amount"), rs.getString("placed_by"), rs.getLong("ts")));
                    }
                }
                log.info("[DripleafCore] loaded " + active.size() + " bounties.");
            } catch (SQLException e) {
                log.log(Level.WARNING, "[DripleafCore] failed to load bounties", e);
            } finally {
                if (c != null) db.giveBack(c);
            }
        });
    }

    public List<Bounty> all() {
        List<Bounty> out = new ArrayList<>(active.values());
        out.sort((a, b) -> Double.compare(b.amount(), a.amount()));
        return out;
    }

    public Bounty on(UUID target) {
        return active.get(target);
    }

    public void add(Player target, double amount, String placedBy) {
        Bounty existing = active.get(target.getUniqueId());
        double total = (existing == null ? 0 : existing.amount()) + amount;
        Bounty bounty = new Bounty(target.getUniqueId(), target.getName(), total, placedBy, System.currentTimeMillis());
        active.put(target.getUniqueId(), bounty);
        save(bounty);
    }

    /** Removes and returns the payout after tax, or 0 when nothing was on this head. */
    public double claim(UUID target) {
        Bounty bounty = active.remove(target);
        if (bounty == null) return 0;
        delete(target);
        return bounty.amount() * (1.0 - cfg.bountyTax);
    }

    private void save(Bounty bounty) {
        String sql = db.kind() == Db.Kind.MYSQL
                ? "INSERT INTO dl_bounty (target,target_name,amount,placed_by,ts) VALUES (?,?,?,?,?) " +
                  "ON DUPLICATE KEY UPDATE amount=VALUES(amount),placed_by=VALUES(placed_by),ts=VALUES(ts)"
                : "INSERT INTO dl_bounty (target,target_name,amount,placed_by,ts) VALUES (?,?,?,?,?) " +
                  "ON CONFLICT(target) DO UPDATE SET amount=excluded.amount,placed_by=excluded.placed_by,ts=excluded.ts";
        io.execute(() -> {
            Connection c = null;
            try {
                c = db.borrow();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, bounty.target().toString());
                    ps.setString(2, bounty.targetName());
                    ps.setDouble(3, bounty.amount());
                    ps.setString(4, bounty.placedBy());
                    ps.setLong(5, bounty.placedAt());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                log.log(Level.WARNING, "[DripleafCore] failed to save bounty", e);
            } finally {
                if (c != null) db.giveBack(c);
            }
        });
    }

    private void delete(UUID target) {
        io.execute(() -> {
            Connection c = null;
            try {
                c = db.borrow();
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM dl_bounty WHERE target=?")) {
                    ps.setString(1, target.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                log.log(Level.WARNING, "[DripleafCore] failed to delete bounty", e);
            } finally {
                if (c != null) db.giveBack(c);
            }
        });
    }
}
