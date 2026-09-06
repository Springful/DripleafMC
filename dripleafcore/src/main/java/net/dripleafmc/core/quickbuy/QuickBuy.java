package net.dripleafmc.core.quickbuy;

import net.dripleafmc.core.storage.Db;
import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Per-player quick-buy presets: a slot holds an item and an amount, and one click
 * repurchases it at the current server shop price.
 *
 * Only items that exist in shop.yml can ever be saved here — the picker is built from
 * the shop, not from the material registry, so a preset can never point at something
 * the server doesn't sell.
 *
 * Slots live in memory for the session (loaded at pre-login with the profile) and are
 * written through on change; clicking a preset must never wait on the database.
 */
public final class QuickBuy {

    public record Slot(Material item, int amount) {}

    private final Map<UUID, Slot[]> cache = new ConcurrentHashMap<>(64);
    private final Db db;
    private final ExecutorService io;
    private final Logger log;
    private final int size;

    public QuickBuy(Db db, ExecutorService io, int size, Logger log) {
        this.db = db;
        this.io = io;
        this.size = size;
        this.log = log;
    }

    public int size() {
        return size;
    }

    /** Blocking — pre-login only. */
    public void loadBlocking(UUID uuid) {
        Slot[] slots = new Slot[size];
        Connection c = null;
        try {
            c = db.borrow();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT slot, item, amount FROM dl_quickbuy WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int index = rs.getInt("slot");
                        if (index < 0 || index >= size) continue;
                        Material item = Material.matchMaterial(rs.getString("item"));
                        if (item == null) continue;
                        slots[index] = new Slot(item, Math.max(1, rs.getInt("amount")));
                    }
                }
            }
        } catch (SQLException e) {
            log.log(Level.SEVERE, "[DripleafCore] failed to load quick buy slots", e);
        } finally {
            if (c != null) db.giveBack(c);
        }
        cache.put(uuid, slots);
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    public Slot[] slots(UUID uuid) {
        return cache.computeIfAbsent(uuid, k -> new Slot[size]);
    }

    public Slot get(UUID uuid, int index) {
        if (index < 0 || index >= size) return null;
        return slots(uuid)[index];
    }

    public void set(UUID uuid, int index, Material item, int amount) {
        if (index < 0 || index >= size) return;
        slots(uuid)[index] = new Slot(item, amount);

        String sql = db.kind() == Db.Kind.MYSQL
                ? "INSERT INTO dl_quickbuy (uuid,slot,item,amount) VALUES (?,?,?,?) " +
                  "ON DUPLICATE KEY UPDATE item=VALUES(item),amount=VALUES(amount)"
                : "INSERT INTO dl_quickbuy (uuid,slot,item,amount) VALUES (?,?,?,?) " +
                  "ON CONFLICT(uuid,slot) DO UPDATE SET item=excluded.item,amount=excluded.amount";

        io.execute(() -> {
            Connection c = null;
            try {
                c = db.borrow();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, index);
                    ps.setString(3, item.name());
                    ps.setInt(4, amount);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                log.log(Level.WARNING, "[DripleafCore] failed to save a quick buy slot", e);
            } finally {
                if (c != null) db.giveBack(c);
            }
        });
    }

    public void clear(UUID uuid, int index) {
        if (index < 0 || index >= size) return;
        if (slots(uuid)[index] == null) return;
        slots(uuid)[index] = null;
        io.execute(() -> {
            Connection c = null;
            try {
                c = db.borrow();
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM dl_quickbuy WHERE uuid=? AND slot=?")) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, index);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                log.log(Level.WARNING, "[DripleafCore] failed to clear a quick buy slot", e);
            } finally {
                if (c != null) db.giveBack(c);
            }
        });
    }

    public void clearAll(UUID uuid) {
        cache.put(uuid, new Slot[size]);
        io.execute(() -> {
            Connection c = null;
            try {
                c = db.borrow();
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM dl_quickbuy WHERE uuid=?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                log.log(Level.WARNING, "[DripleafCore] failed to clear quick buy", e);
            } finally {
                if (c != null) db.giveBack(c);
            }
        });
    }

    /** First free index, or -1 when the panel is full. */
    public int firstEmpty(UUID uuid) {
        Slot[] slots = slots(uuid);
        for (int i = 0; i < slots.length; i++) if (slots[i] == null) return i;
        return -1;
    }
}
