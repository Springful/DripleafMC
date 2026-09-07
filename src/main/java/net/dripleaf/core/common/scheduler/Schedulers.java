package net.dripleaf.core.common.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The whole plugin's thread budget: one thread.
 *
 * <p>This is not a style preference. The host has hard-crashed with
 * {@code OutOfMemoryError: unable to create native thread} while heap was
 * healthy — the ceiling being hit is the container's process limit, shared
 * across every plugin on the box. So: no {@code CompletableFuture.supplyAsync}
 * on the common pool, no per-feature executors, no HTTP clients, and no
 * per-player tasks. Everything off-main goes through {@link #async}, and
 * everything that touches the Bukkit API afterwards comes back through
 * {@link #sync}.
 *
 * <p>Repeating work is one task per concern — warmups, autosave, leaderboard
 * refresh, cooldown sweep — regardless of how many players are online.
 */
public final class Schedulers {

    private final Plugin plugin;
    private final ScheduledExecutorService executor;
    private final AtomicInteger scheduled = new AtomicInteger();
    private volatile boolean shuttingDown;

    public Schedulers(Plugin plugin) {
        this.plugin = plugin;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "DripleafCore-IO");
            thread.setDaemon(true);
            // Below the server's own threads: nothing here is latency critical.
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
        this.executor = Executors.newSingleThreadScheduledExecutor(factory);
    }

    /** Runs off the main thread on the shared worker. Never call Bukkit API inside. */
    public void async(Runnable task) {
        if (shuttingDown) {
            return;
        }
        scheduled.incrementAndGet();
        executor.execute(() -> {
            try {
                task.run();
            } catch (Throwable error) {
                plugin.getLogger().log(Level.SEVERE, "Async task failed", error);
            } finally {
                scheduled.decrementAndGet();
            }
        });
    }

    /** Runs on the main thread on the next tick. Safe from any thread. */
    public void sync(Runnable task) {
        if (shuttingDown || !plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /**
     * Computes off-thread and hands the result back on the main thread. The one
     * blessed way to do "load from disk, then touch the world".
     */
    public <T> void asyncThenSync(Supplier<T> work, java.util.function.Consumer<T> then) {
        async(() -> {
            T result = work.get();
            sync(() -> then.accept(result));
        });
    }

    /** A repeating off-thread task. One per concern, never one per player. */
    public ScheduledFuture<?> repeatAsync(Runnable task, long initialDelay, long period,
                                          TimeUnit unit) {
        return executor.scheduleAtFixedRate(() -> {
            try {
                task.run();
            } catch (Throwable error) {
                plugin.getLogger().log(Level.SEVERE, "Repeating async task failed", error);
            }
        }, initialDelay, period, unit);
    }

    /** A repeating main-thread task, in ticks. */
    public int repeatSync(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks)
                .getTaskId();
    }

    public void delaySync(Runnable task, long delayTicks) {
        if (shuttingDown || !plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(0L, delayTicks));
    }

    /** Reported on the admin Diagnostics screen — deliberately, given the crash history. */
    public int pendingAsyncTasks() {
        return scheduled.get();
    }

    /** The plugin's own thread count. Always 1 while enabled. */
    public int threadCount() {
        return executor.isShutdown() ? 0 : 1;
    }

    /**
     * Drains outstanding writes, then stops. Called from {@code onDisable}, so
     * a bounded block here is correct — losing a pending player save is worse
     * than a two-second shutdown.
     */
    public void shutdown() {
        shuttingDown = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Shared executor did not drain in 10s; forcing.");
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
