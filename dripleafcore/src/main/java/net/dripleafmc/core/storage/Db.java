package net.dripleafmc.core.storage;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Minimal JDBC pool. Deliberately not HikariCP: the plugin needs three or four
 * connections at peak and pulling a pooling library in for that is dead weight.
 *
 * SQLite runs with one connection in WAL mode (concurrent readers, serialised
 * writers). MySQL gets a fixed-size pool. Every caller must return the connection.
 */
public final class Db implements AutoCloseable {

    public enum Kind { SQLITE, MYSQL }

    private final Kind kind;
    private final String url;
    private final Properties props = new Properties();
    private final Deque<Connection> idle = new ArrayDeque<>();
    private final int maxSize;
    private int created;
    private final Logger log;

    public Db(FileConfiguration cfg, File dataFolder, Logger log) throws SQLException {
        this.log = log;
        String type = cfg.getString("storage.type", "sqlite");
        if (type.equalsIgnoreCase("mysql")) {
            this.kind = Kind.MYSQL;
            this.maxSize = Math.max(2, cfg.getInt("storage.mysql.pool-size", 6));
            this.url = "jdbc:mysql://" + cfg.getString("storage.mysql.host", "127.0.0.1")
                    + ':' + cfg.getInt("storage.mysql.port", 3306)
                    + '/' + cfg.getString("storage.mysql.database", "dripleaf")
                    + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&rewriteBatchedStatements=true";
            props.setProperty("user", cfg.getString("storage.mysql.username", "root"));
            props.setProperty("password", cfg.getString("storage.mysql.password", ""));
        } else {
            this.kind = Kind.SQLITE;
            this.maxSize = 1;
            File db = new File(dataFolder, cfg.getString("storage.file", "data.db"));
            this.url = "jdbc:sqlite:" + db.getAbsolutePath();
        }
        schema();
    }

    public Kind kind() {
        return kind;
    }

    public synchronized Connection borrow() throws SQLException {
        Connection c = idle.pollLast();
        while (c != null && !valid(c)) {
            close(c);
            created--;
            c = idle.pollLast();
        }
        if (c != null) return c;
        if (created >= maxSize) {
            // Only reachable on MySQL; wait briefly for a returned connection.
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    wait(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("interrupted waiting for a connection");
                }
                c = idle.pollLast();
                if (c != null && valid(c)) return c;
            }
            throw new SQLException("connection pool exhausted");
        }
        c = DriverManager.getConnection(url, props);
        created++;
        if (kind == Kind.SQLITE) {
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA busy_timeout=5000");
            }
        }
        return c;
    }

    public synchronized void giveBack(Connection c) {
        if (c == null) return;
        idle.addLast(c);
        notifyAll();
    }

    private static boolean valid(Connection c) {
        try {
            return !c.isClosed() && c.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private static void close(Connection c) {
        try {
            c.close();
        } catch (SQLException ignored) {
        }
    }

    private void schema() throws SQLException {
        boolean my = kind == Kind.MYSQL;
        String text = my ? "VARCHAR(191)" : "TEXT";
        String uuid = my ? "CHAR(36)" : "TEXT";
        String auto = my ? "BIGINT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";

        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS dl_profile (" +
                "uuid " + uuid + " PRIMARY KEY," +
                "name " + text + "," +
                "shards BIGINT NOT NULL DEFAULT 0," +
                "kills INT NOT NULL DEFAULT 0," +
                "deaths INT NOT NULL DEFAULT 0," +
                "mob_kills INT NOT NULL DEFAULT 0," +
                "blocks_broken BIGINT NOT NULL DEFAULT 0," +
                "blocks_placed BIGINT NOT NULL DEFAULT 0," +
                "money_spent DOUBLE NOT NULL DEFAULT 0," +
                "money_made DOUBLE NOT NULL DEFAULT 0," +
                "playtime BIGINT NOT NULL DEFAULT 0," +
                "killstreak INT NOT NULL DEFAULT 0," +
                "best_streak INT NOT NULL DEFAULT 0," +
                "booster_until BIGINT NOT NULL DEFAULT 0," +
                "flags INT NOT NULL DEFAULT 0," +
                "last_seen BIGINT NOT NULL DEFAULT 0)",

            "CREATE TABLE IF NOT EXISTS dl_home (" +
                "uuid " + uuid + " NOT NULL," +
                "name VARCHAR(16) NOT NULL," +
                "world " + text + " NOT NULL," +
                "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL," +
                "yaw FLOAT NOT NULL, pitch FLOAT NOT NULL," +
                "PRIMARY KEY (uuid, name))",

            "CREATE TABLE IF NOT EXISTS dl_sell (" +
                "id " + auto + "," +
                "uuid " + uuid + " NOT NULL," +
                "ts BIGINT NOT NULL," +
                "total DOUBLE NOT NULL," +
                "summary " + text + " NOT NULL)",

            "CREATE TABLE IF NOT EXISTS dl_bounty (" +
                "target " + uuid + " PRIMARY KEY," +
                "target_name " + text + " NOT NULL," +
                "amount DOUBLE NOT NULL," +
                "placed_by " + text + " NOT NULL," +
                "ts BIGINT NOT NULL)",

            "CREATE TABLE IF NOT EXISTS dl_quickbuy (" +
                "uuid " + uuid + " NOT NULL," +
                "slot INT NOT NULL," +
                "item " + text + " NOT NULL," +
                "amount INT NOT NULL," +
                "PRIMARY KEY (uuid, slot))",

            "CREATE INDEX IF NOT EXISTS idx_sell_uuid ON dl_sell (uuid, ts)",
            "CREATE INDEX IF NOT EXISTS idx_profile_shards ON dl_profile (shards)",
            "CREATE INDEX IF NOT EXISTS idx_profile_kills ON dl_profile (kills)",
            "CREATE INDEX IF NOT EXISTS idx_profile_playtime ON dl_profile (playtime)"
        };

        Connection c = borrow();
        try (Statement st = c.createStatement()) {
            for (String sql : ddl) st.execute(sql);
        } finally {
            giveBack(c);
        }
        log.info("[DripleafCore] storage ready (" + kind + ")");
    }

    @Override
    public synchronized void close() {
        for (Connection c : idle) close(c);
        idle.clear();
        created = 0;
    }
}
