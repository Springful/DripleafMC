package net.dripleaf.core.core.leaderboard;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.storage.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * {@code /baltop} and {@code /rebirthtop}, computed on a timer and served from
 * cache.
 *
 * <p>Never computed on request. With a large player base, walking every stored
 * profile on a command is a main-thread stall measured in seconds — and it is
 * the kind of stall that only shows up once the server is popular enough for it
 * to hurt.
 *
 * <p>The walk itself runs on the shared worker and touches only flat files and
 * Vault's offline balance API. The result is swapped in atomically as an
 * immutable list.
 */
public final class LeaderboardService {

    /** @param value money balance or tier number, depending on the board */
    public record Entry(UUID uuid, String name, BigDecimal value) {
    }

    private final Services services;

    private volatile List<Entry> balances = List.of();
    private volatile List<Entry> rebirths = List.of();
    private volatile long lastRefresh;

    public LeaderboardService(Services services) {
        this.services = services;
    }

    public void start(int minutes) {
        long period = Math.max(1, minutes) * 60L;
        // Five seconds, not thirty: /baltop returning "nothing yet" for half a
        // minute after a restart reads as broken.
        services.schedulers().repeatAsync(this::refresh, 5L, period, TimeUnit.SECONDS);
    }

    /** Forced refresh, from the admin screen. Runs off-thread like the timer does. */
    public void refreshNow() {
        services.schedulers().async(this::refresh);
    }

    private void refresh() {
        // Online players first. Someone who joined this session may not have
        // been flushed to disk yet, so a directory listing alone misses them
        // entirely — which is why the board read as empty on a fresh server
        // with players connected.
        Map<UUID, PlayerData> online = services.players().snapshot();

        Set<UUID> ids = new LinkedHashSet<>(online.keySet());
        ids.addAll(services.players().allStoredIds());

        List<Entry> money = new ArrayList<>(ids.size());
        List<Entry> tiers = new ArrayList<>(ids.size());
        // Precomputed so the tie-break below is a map lookup rather than a disk
        // read per comparison.
        Map<UUID, Long> reachedAt = new HashMap<>(ids.size());

        for (UUID uuid : ids) {
            PlayerData data = online.get(uuid);
            if (data == null) {
                data = services.players().loadOffline(uuid);
            }
            String name = data.lastKnownName();
            if (name.isBlank()) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                name = offline.getName() == null ? uuid.toString() : offline.getName();
            }

            if (services.hooks().vault().available()) {
                money.add(new Entry(uuid, name,
                        services.hooks().vault().balance(Bukkit.getOfflinePlayer(uuid))));
            }
            if (data.rebirthTier() > 0) {
                tiers.add(new Entry(uuid, name, BigDecimal.valueOf(data.rebirthTier())));
            }
            reachedAt.put(uuid, data.rebirthLast());
        }

        money.sort(Comparator.comparing(Entry::value).reversed());
        // Ties on tier fall back to who reached it first.
        tiers.sort(Comparator.comparing(Entry::value).reversed()
                .thenComparing(entry -> reachedAt.getOrDefault(entry.uuid(), Long.MAX_VALUE)));

        this.balances = List.copyOf(money);
        this.rebirths = List.copyOf(tiers);
        this.lastRefresh = System.currentTimeMillis();
    }

    public List<Entry> balances() {
        return balances;
    }

    public List<Entry> rebirths() {
        return rebirths;
    }

    /** 1-based rank, for the {@code %dripleaf_baltop_1%} family of placeholders. */
    public Entry balanceAt(int rank) {
        return at(balances, rank);
    }

    public Entry rebirthAt(int rank) {
        return at(rebirths, rank);
    }

    private static Entry at(List<Entry> list, int rank) {
        int index = rank - 1;
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    public long lastRefresh() {
        return lastRefresh;
    }

    /** One page of a board, 10 per page. */
    public List<Entry> page(List<Entry> board, int page) {
        int from = Math.max(0, (page - 1) * 10);
        if (from >= board.size()) {
            return List.of();
        }
        return board.subList(from, Math.min(board.size(), from + 10));
    }

    public int pages(List<Entry> board) {
        return Math.max(1, (board.size() + 9) / 10);
    }
}
