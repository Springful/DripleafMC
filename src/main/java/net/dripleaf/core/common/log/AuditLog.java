package net.dripleaf.core.common.log;

import net.dripleaf.core.common.scheduler.Schedulers;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * Append-only plain-text logs under {@code plugins/DripleafCore/logs/}.
 *
 * <p>Three of them: {@code transactions.log}, {@code admin.log} and
 * {@code rebirth.log}. They exist so a duplication bug or a staff dispute is
 * resolvable after the fact instead of being someone's word against someone
 * else's.
 *
 * <p>Every append is queued onto the shared worker. Callers never block.
 */
public final class AuditLog {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Plugin plugin;
    private final Schedulers schedulers;
    private final Path directory;

    public AuditLog(Plugin plugin, Schedulers schedulers) {
        this.plugin = plugin;
        this.schedulers = schedulers;
        this.directory = plugin.getDataFolder().toPath().resolve("logs");
    }

    /** Item bought or sold: who, what, how many, unit price, total, resulting balance. */
    public void transaction(String line) {
        append("transactions.log", line);
    }

    /** Actor, action, target, before and after. */
    public void admin(String line) {
        append("admin.log", line);
    }

    /**
     * Rebirth state. Written on every completion and, critically, on every
     * failure part-way through — a half-applied rebirth is the worst outcome
     * there is and staff need enough to reconstruct it.
     */
    public void rebirth(String line) {
        append("rebirth.log", line);
    }

    private void append(String file, String line) {
        String stamped = '[' + LocalDateTime.now().format(STAMP) + "] " + line
                + System.lineSeparator();
        schedulers.async(() -> {
            try {
                Files.createDirectories(directory);
                Files.writeString(directory.resolve(file), stamped, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Could not append to " + file, ex);
            }
        });
    }

    /**
     * The last {@code limit} lines of a log, newest last. Blocking — the admin
     * screen reads it on the shared worker.
     */
    public java.util.List<String> tail(String file, int limit) {
        Path path = directory.resolve(file);
        if (!Files.exists(path)) {
            return java.util.List.of();
        }
        try {
            java.util.List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - Math.max(1, limit));
            return java.util.List.copyOf(lines.subList(from, lines.size()));
        } catch (IOException ex) {
            return java.util.List.of();
        }
    }
}
