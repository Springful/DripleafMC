package net.dripleaf.core.common.cooldown;

import net.dripleaf.core.common.scheduler.Schedulers;
import net.dripleaf.core.common.sound.SoundService;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.MessageService;
import net.dripleaf.core.common.text.Palette;
import net.dripleaf.core.common.text.ProgressBar;
import net.dripleaf.core.common.text.Text;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delays before a command runs — teleports, mostly.
 *
 * <p><b>One task, not one per warmup.</b> A single repeating main-thread task at
 * 1-tick resolution drives every active countdown. With a hundred players
 * warping that is one scheduled task, not a hundred.
 *
 * <p>A warmup cancels on movement past a configurable threshold (default 0.5
 * blocks, so turning on the spot is fine) and, optionally, on damage. It shows
 * a Unicode progress bar on the action bar and plays a rising note as it counts,
 * a chime on completion and a bass note on cancel.
 */
public final class WarmupService implements Listener {

    private static final class Active {

        final Player player;
        final String label;
        final Location origin;
        final Runnable onComplete;
        final long totalTicks;
        long elapsedTicks;

        Active(Player player, String label, Runnable onComplete, long totalTicks) {
            this.player = player;
            this.label = label;
            this.origin = player.getLocation().clone();
            this.onComplete = onComplete;
            this.totalTicks = totalTicks;
        }
    }

    private final Schedulers schedulers;
    private final MessageService messages;
    private final SoundService sounds;
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();

    private double moveThreshold = 0.5d;
    private boolean cancelOnDamage = true;
    private int barCells = 20;
    private int taskId = -1;

    public WarmupService(Schedulers schedulers, MessageService messages, SoundService sounds) {
        this.schedulers = schedulers;
        this.messages = messages;
        this.sounds = sounds;
    }

    public void configure(double moveThreshold, boolean cancelOnDamage, int barCells) {
        this.moveThreshold = Math.max(0d, moveThreshold);
        this.cancelOnDamage = cancelOnDamage;
        this.barCells = Math.max(4, Math.min(40, barCells));
    }

    /** Registered once at enable. The task runs for the life of the plugin. */
    public void start() {
        if (taskId != -1) {
            return;
        }
        taskId = schedulers.repeatSync(this::tick, 1L, 1L);
    }

    /**
     * @param seconds     warmup length; zero or less runs {@code onComplete} immediately
     * @param label       shown on the action bar, already MiniMessage
     * @param onComplete  run on the main thread when the countdown finishes
     */
    public void begin(Player player, String label, double seconds, Runnable onComplete) {
        if (seconds <= 0d) {
            onComplete.run();
            return;
        }
        long ticks = Math.max(1L, Math.round(seconds * 20d));
        active.put(player.getUniqueId(), new Active(player, label, onComplete, ticks));
    }

    public boolean isWarmingUp(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    public void cancel(Player player, String reasonKey) {
        Active entry = active.remove(player.getUniqueId());
        if (entry == null) {
            return;
        }
        player.sendActionBar(net.kyori.adventure.text.Component.empty());
        sounds.play(player, SoundService.WARMUP_CANCEL);
        if (reasonKey != null) {
            messages.send(player, reasonKey);
        }
    }

    private void tick() {
        if (active.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Active>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Active entry = iterator.next().getValue();
            Player player = entry.player;

            if (!player.isOnline()) {
                iterator.remove();
                continue;
            }
            if (moved(entry)) {
                iterator.remove();
                player.sendActionBar(net.kyori.adventure.text.Component.empty());
                sounds.play(player, SoundService.WARMUP_CANCEL);
                messages.send(player, "warmup.cancelled-move");
                continue;
            }

            entry.elapsedTicks++;
            if (entry.elapsedTicks >= entry.totalTicks) {
                iterator.remove();
                player.sendActionBar(net.kyori.adventure.text.Component.empty());
                entry.onComplete.run();
                continue;
            }

            render(entry);
        }
    }

    private boolean moved(Active entry) {
        Location now = entry.player.getLocation();
        Location origin = entry.origin;
        if (now.getWorld() != origin.getWorld()) {
            return true;
        }
        return now.distanceSquared(origin) > moveThreshold * moveThreshold;
    }

    private void render(Active entry) {
        double fraction = (double) entry.elapsedTicks / entry.totalTicks;
        double remaining = (entry.totalTicks - entry.elapsedTicks) / 20d;

        // Only redraw four times a second: the action bar does not need 20 Hz and
        // a component build per player per tick is exactly the kind of waste §14
        // is about.
        if (entry.elapsedTicks % 5L == 0L) {
            Ctx ctx = new Ctx()
                    .put("label", entry.label)
                    .put("bar", ProgressBar.coloured(fraction, 1d, barCells, Palette.BRAND_TO))
                    .put("seconds", String.format(java.util.Locale.US, "%.1f", remaining));
            entry.player.sendActionBar(
                    Text.parse(messages.raw("warmup.action-bar"), ctx.resolver()));
            // Pitch rises from 0.5 to 2.0 across the countdown.
            sounds.play(entry.player, SoundService.WARMUP_TICK,
                    (float) (0.5d + fraction * 1.5d));
        }
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!cancelOnDamage || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (active.containsKey(player.getUniqueId())) {
            cancel(player, "warmup.cancelled-damage");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
    }
}
