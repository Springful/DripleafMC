package net.dripleafmc.core.homes;

import net.dripleafmc.core.config.Cfg;
import net.dripleafmc.core.storage.Db;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Home storage. Loaded once per session at pre-login alongside the profile, then
 * served from memory — /home should never touch the database.
 */
public final class Homes {

    public record Home(String name, String world, double x, double y, double z, float yaw, float pitch) {
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    private final Map<UUID, Map<String, Home>> cache = new ConcurrentHashMap<>(64);
    private final Db db;
    private final ExecutorService io;
    private final Cfg cfg;
    private final Logger log;

    public Homes(Db db, ExecutorService io, Cfg cfg, Logger log) {
        this.db = db;
        this.io = io;
        this.cfg = cfg;
        this.log = log;
    }

    public static boolean validName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    /** Blocking — pre-login only. */
    public void loadBlocking(UUID uuid) {
        Map<String, Home> homes = new LinkedHashMap<>(4);
        Connection c = null;
        try {
            c = db.borrow();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT name, world, x, y, z, yaw, pitch FROM dl_home WHERE uuid=? ORDER BY name")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        homes.put(rs.getString("name").toLowerCase(Locale.ROOT),
                                new Home(rs.getString("name"), rs.getString("world"),
                                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                                        rs.getFloat("yaw"), rs.getFloat("pitch")));
                    }
                }
            }
        } catch (SQLException e) {
            log.log(Level.SEVERE, "[DripleafCore] failed to load homes", e);
        } finally {
            if (c != null) db.giveBack(c);
        }
        cache.put(uuid, homes);
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    public Map<String, Home> homes(UUID uuid) {
        return cache.getOrDefault(uuid, Map.of());
    }

    public Home get(UUID uuid, String name) {
        return homes(uuid).get(name.toLowerCase(Locale.ROOT));
    }

    /** Slots come from permissions so LuckPerms ranks own the limit. */
    public int slots(Player player) {
        for (int n = cfg.homeMaxSlots; n >= 1; n--) {
            if (player.hasPermission("dripleaf.home.slot." + n)) return n;
        }
        return cfg.homeDefaultSlots;
    }

    public void set(Player player, String name) {
        Map<String, Home> homes = cache.computeIfAbsent(player.getUniqueId(), k -> new LinkedHashMap<>(4));
        Location loc = player.getLocation();
        Home home = new Home(name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch());
        homes.put(name.toLowerCase(Locale.ROOT), home);

        UUID uuid = player.getUniqueId();
        String sql = db.kind() == Db.Kind.MYSQL
                ? "INSERT INTO dl_home (uuid,name,world,x,y,z,yaw,pitch) VALUES (?,?,?,?,?,?,?,?) " +
                  "ON DUPLICATE KEY UPDATE world=VALUES(world),x=VALUES(x),y=VALUES(y),z=VALUES(z)," +
                  "yaw=VALUES(yaw),pitch=VALUES(pitch)"
                : "INSERT INTO dl_home (uuid,name,world,x,y,z,yaw,pitch) VALUES (?,?,?,?,?,?,?,?) " +
                  "ON CONFLICT(uuid,name) DO UPDATE SET world=excluded.world,x=excluded.x,y=excluded.y," +
                  "z=excluded.z,yaw=excluded.yaw,pitch=excluded.pitch";

        io.execute(() -> {
            Connection c = null;
            try {
                c = db.borrow();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, home.name());
                    ps.setString(3, home.world());
                    ps.setDouble(4, home.x());
                    ps.setDouble(5, home.y());
                    ps.setDouble(6, home.z());
                    ps.setFloat(7, home.yaw());
                    ps.setFloat(8, home.pitch());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                log.log(Level.SEVERE, "[DripleafCore] failed to save home", e);
            } finally {
                if (c != null) db.giveBack(c);
            }
        });
    }

    public boolean delete(UUID uuid, String name) {
        Map<String, Home> homes = cache.get(uuid);
        if (homes == null) return false;
        Home removed = homes.remove(name.toLowerCase(Locale.ROOT));
        if (removed == null) return false;
        io.execute(() -> {
            Connection c = null;
            try {
                c = db.borrow();
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM dl_home WHERE uuid=? AND name=?")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, removed.name());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                log.log(Level.WARNING, "[DripleafCore] failed to delete home", e);
            } finally {
                if (c != null) db.giveBack(c);
            }
        });
        return true;
    }
}
