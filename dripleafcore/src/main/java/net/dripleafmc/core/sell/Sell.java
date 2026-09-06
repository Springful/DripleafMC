package net.dripleafmc.core.sell;

import net.dripleafmc.core.config.Cfg;
import net.dripleafmc.core.config.Lang;
import net.dripleafmc.core.econ.Money;
import net.dripleafmc.core.profile.Profile;
import net.dripleafmc.core.profile.Profiles;
import net.dripleafmc.core.storage.Db;
import net.dripleafmc.core.util.Num;
import net.dripleafmc.core.util.Text;
import net.dripleafmc.core.worth.Worth;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The drag-items-in sell chest, plus the shared pricing path used by wands.
 *
 * Selling walks the container exactly once, accumulating per-material totals in an
 * EnumMap so the summary line costs nothing extra.
 */
public final class Sell {

    public record Result(double total, int items, String summary) {}

    public record Sale(long timestamp, double total, String summary) {}

    /** Marker holder so the close listener knows this inventory is a sell chest. */
    public static final class Holder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Worth worth;
    private final Money money;
    private final Profiles profiles;
    private final Cfg cfg;
    private final Lang lang;
    private final Db db;
    private final ExecutorService io;
    private final Logger log;

    public Sell(Worth worth, Money money, Profiles profiles, Cfg cfg, Lang lang, Db db,
                ExecutorService io, Logger log) {
        this.worth = worth;
        this.money = money;
        this.profiles = profiles;
        this.cfg = cfg;
        this.lang = lang;
        this.db = db;
        this.io = io;
        this.log = log;
    }

    public void openChest(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54, lang.get("sell.chest-title"));
        holder.inventory = inv;
        player.openInventory(inv);
    }

    /**
     * Prices and removes everything sellable in {@code contents}. Unsellable stacks are
     * left in place so the close handler can hand them straight back.
     */
    public Result sell(Player player, Inventory inventory) {
        Profile profile = profiles.get(player.getUniqueId());
        double multiplier = worth.multiplier(player, profile);

        Map<Material, Integer> counted = new EnumMap<>(Material.class);
        double total = 0;
        int items = 0;

        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) continue;
            double value = worth.sellValue(stack, multiplier);
            if (value <= 0) continue;
            total += value;
            items += stack.getAmount();
            counted.merge(stack.getType(), stack.getAmount(), Integer::sum);
            inventory.setItem(slot, null);
        }

        if (total <= 0) return new Result(0, 0, "");

        money.give(player, total, profile);

        StringBuilder summary = new StringBuilder(64);
        int shown = 0;
        for (Map.Entry<Material, Integer> e : counted.entrySet()) {
            if (shown++ > 0) summary.append(", ");
            if (shown > 6) {
                summary.append('+').append(counted.size() - 6).append(" more");
                break;
            }
            summary.append(e.getValue()).append("x ").append(Text.pretty(e.getKey()));
        }

        record(player.getUniqueId(), total, summary.toString());
        return new Result(total, items, summary.toString());
    }

    public void announce(Player player, Result result) {
        if (result.total() <= 0) {
            lang.send(player, "economy.sold-nothing");
            return;
        }
        Profile profile = profiles.get(player.getUniqueId());
        boolean actionBar = cfg.sellActionBar && (profile == null || profile.flag(Profile.FLAG_SELL_ACTION));
        if (actionBar) {
            player.sendActionBar(lang.get("economy.actionbar-sell", Text.p("price", Num.money(result.total()))));
        } else {
            lang.send(player, "economy.sold",
                    Text.p("price", Num.money(result.total())),
                    Text.p("amount", String.valueOf(result.items())));
        }
    }

    private void record(UUID uuid, double total, String summary) {
        io.execute(() -> {
            Connection c = null;
            try {
                c = db.borrow();
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dl_sell (uuid, ts, total, summary) VALUES (?,?,?,?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setLong(2, System.currentTimeMillis());
                    ps.setDouble(3, total);
                    ps.setString(4, summary);
                    ps.executeUpdate();
                }
                // Trim old rows so the table can't grow without bound.
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM dl_sell WHERE uuid=? AND id NOT IN " +
                        "(SELECT id FROM (SELECT id FROM dl_sell WHERE uuid=? ORDER BY ts DESC LIMIT ?) t)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, uuid.toString());
                    ps.setInt(3, cfg.historySize);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                log.log(Level.WARNING, "[DripleafCore] failed to log a sale", e);
            } finally {
                if (c != null) db.giveBack(c);
            }
        });
    }

    public CompletableFuture<List<Sale>> history(UUID uuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Sale> out = new ArrayList<>(limit);
            Connection c = null;
            try {
                c = db.borrow();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT ts, total, summary FROM dl_sell WHERE uuid=? ORDER BY ts DESC LIMIT ?")) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, limit);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            out.add(new Sale(rs.getLong("ts"), rs.getDouble("total"), rs.getString("summary")));
                        }
                    }
                }
            } catch (SQLException e) {
                log.log(Level.WARNING, "[DripleafCore] sell history query failed", e);
            } finally {
                if (c != null) db.giveBack(c);
            }
            return out;
        }, io);
    }

    public Component multiplierLine(Player player) {
        Profile profile = profiles.get(player.getUniqueId());
        return lang.get("sell.multiplier",
                Text.p("value", String.format(java.util.Locale.US, "%.2f", worth.multiplier(player, profile))));
    }
}
