package net.dripleaf.core.core.flight;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.common.storage.PlayerData;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.Palette;
import net.dripleaf.core.common.text.Text;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Purchasable flight, measured in seconds and spent only while airborne.
 *
 * <p>Bought in the shard shop, granted by reward commands, or handed out by
 * staff with {@code /flytime}. The balance is stored per player and survives
 * restarts.
 *
 * <p><b>It only ticks while the player is actually flying.</b> Buying an hour
 * and then walking around does not burn it, which is the behaviour players
 * expect and the one that avoids "I bought fly time and it vanished" tickets.
 * Creative and spectator never consume it, and neither does a player holding
 * permanent flight — {@code dripleaf.fly.permanent} — so granting a rank perk
 * does not silently drain a purchase.
 *
 * <p>One repeating task drives every player's countdown, per §14: not one task
 * each.
 */
public final class FlightService implements Listener {

    /** Players whose flight is currently being paid for out of their balance. */
    private final Set<UUID> metered = ConcurrentHashMap.newKeySet();

    private final Services services;
    private int warnAtSeconds = 60;
    private boolean actionBar = true;
    private int taskId = -1;

    public FlightService(Services services) {
        this.services = services;
    }

    public void configure(int warnAtSeconds, boolean actionBar) {
        this.warnAtSeconds = Math.max(0, warnAtSeconds);
        this.actionBar = actionBar;
    }

    /** Registered once at enable; runs for the life of the plugin. */
    public void start() {
        if (taskId != -1) {
            return;
        }
        taskId = services.schedulers().repeatSync(this::tick, 20L, 20L);
    }

    // ------------------------------------------------------------- balance

    public long remaining(Player player) {
        return services.players().get(player).flightSeconds();
    }

    /** @return the new balance */
    public long grant(Player player, long seconds) {
        long now = services.players().get(player).addFlightSeconds(seconds);
        if (seconds > 0) {
            services.messages().send(player, "flight.granted", new Ctx()
                    .put("time", DripleafCommand.formatDuration(seconds * 1000L))
                    .put("total", DripleafCommand.formatDuration(now * 1000L)));
        }
        return now;
    }

    public void set(Player player, long seconds) {
        services.players().get(player).flightSeconds(seconds);
    }

    /** True when this player's flight should be paid for rather than free. */
    public boolean meters(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        return !player.hasPermission("dripleaf.fly.permanent");
    }

    /**
     * Turns metered flight on for a player who has time banked.
     *
     * @return false when they have none, having told them so
     */
    public boolean enable(Player player) {
        if (!meters(player)) {
            player.setAllowFlight(true);
            player.setFlying(true);
            return true;
        }
        if (remaining(player) <= 0L) {
            services.messages().send(player, "flight.none");
            return false;
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        metered.add(player.getUniqueId());
        services.messages().send(player, "flight.enabled", Ctx.of("time",
                DripleafCommand.formatDuration(remaining(player) * 1000L)));
        return true;
    }

    public void disable(Player player, String reasonKey) {
        metered.remove(player.getUniqueId());
        if (meters(player)) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        if (reasonKey != null) {
            services.messages().send(player, reasonKey);
        }
    }

    public boolean isMetered(Player player) {
        return metered.contains(player.getUniqueId());
    }

    // ---------------------------------------------------------------- tick

    /** One pass per second over the players who are actually airborne. */
    private void tick() {
        if (metered.isEmpty()) {
            return;
        }
        for (UUID uuid : Set.copyOf(metered)) {
            Player player = services.plugin().getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                metered.remove(uuid);
                continue;
            }
            if (!meters(player)) {
                // They gained permanent flight mid-session; stop charging them.
                metered.remove(uuid);
                continue;
            }
            // Standing on the ground with flight enabled costs nothing.
            if (!player.isFlying()) {
                continue;
            }

            PlayerData data = services.players().get(player);
            long left = data.addFlightSeconds(-1L);

            if (left <= 0L) {
                disable(player, "flight.expired");
                continue;
            }
            if (left == warnAtSeconds) {
                services.messages().send(player, "flight.running-out", Ctx.of("time",
                        DripleafCommand.formatDuration(left * 1000L)));
            }
            if (actionBar) {
                player.sendActionBar(Text.parse(Ctx.of("time",
                                DripleafCommand.formatDuration(left * 1000L))
                        .applyRaw(services.messages().raw("flight.action-bar"))));
            }
        }
    }

    // -------------------------------------------------------------- events

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Never restore flight automatically: a player who logs in mid-air with
        // no balance would fall, and one with a balance would start paying for
        // flight they did not ask for.
        if (meters(player) && player.getAllowFlight()) {
            player.setAllowFlight(false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        metered.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.CREATIVE
                || event.getNewGameMode() == GameMode.SPECTATOR) {
            metered.remove(event.getPlayer().getUniqueId());
        }
    }

    /** A short coloured summary for menus. */
    public String describe(Player player) {
        if (!meters(player)) {
            return Palette.colour(Palette.SUCCESS,
                    services.messages().raw("flight.unlimited"));
        }
        long left = remaining(player);
        return left <= 0
                ? Palette.colour(Palette.FAILURE, services.messages().raw("flight.empty"))
                : Palette.colour(Palette.BRAND_TO,
                        DripleafCommand.formatDuration(left * 1000L));
    }

    /**
     * Parses {@code 30s}, {@code 10m}, {@code 2h}, {@code 1d} or a plain number
     * of seconds.
     *
     * @return seconds, or {@code -1} when the input is not a duration
     */
    public static long parseDuration(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1L;
        }
        String cleaned = raw.trim().toLowerCase(java.util.Locale.ROOT);
        long multiplier = 1L;
        char last = cleaned.charAt(cleaned.length() - 1);
        if (!Character.isDigit(last)) {
            multiplier = switch (last) {
                case 's' -> 1L;
                case 'm' -> 60L;
                case 'h' -> 3600L;
                case 'd' -> 86_400L;
                default -> -1L;
            };
            if (multiplier < 0) {
                return -1L;
            }
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        try {
            long value = Long.parseLong(cleaned);
            return value < 0 ? -1L : value * multiplier;
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }
}
