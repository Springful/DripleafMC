package net.dripleaf.core.common.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The only way this plugin writes a yml.
 *
 * <p>Write to {@code <file>.tmp}, {@code fsync} it, then {@code ATOMIC_MOVE}
 * over the target. The host has hard-crashed repeatedly and corrupted
 * SQLite-backed plugin data doing it. An atomic rename either fully lands or
 * does not land at all — there is no torn-write state to recover from.
 *
 * <p>The {@code force(true)} is the part people skip. Without it the rename can
 * be durable while the bytes it points at are not, which is the same corruption
 * with extra steps.
 */
public final class AtomicYaml {

    private AtomicYaml() {
    }

    /** Serialises and durably replaces {@code target}. Call from the shared worker. */
    public static void write(Path target, YamlConfiguration config) throws IOException {
        write(target, config.saveToString());
    }

    public static void write(Path target, String contents) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");

        Files.writeString(tmp, contents, StandardCharsets.UTF_8);
        fsync(tmp);

        try {
            Files.move(tmp, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            // Some container filesystems refuse ATOMIC_MOVE across the same dir.
            // A plain replace is weaker but still far better than writing in place.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void fsync(Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw");
             FileChannel channel = file.getChannel()) {
            channel.force(true);
        }
    }

    /**
     * Loads a yml, tolerating absence. A file that fails to parse is moved aside
     * as {@code <file>.corrupt} and an empty config returned, so one bad file
     * cannot take the server down with it.
     */
    public static YamlConfiguration read(Path path, java.util.logging.Logger logger) {
        if (!Files.exists(path)) {
            return new YamlConfiguration();
        }
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.load(path.toFile());
            return config;
        } catch (Exception ex) {
            logger.severe("Could not parse " + path.getFileName() + " (" + ex.getMessage()
                    + "). Moving it aside and continuing with defaults.");
            try {
                Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveFailure) {
                logger.severe("Could not quarantine the unreadable file: "
                        + moveFailure.getMessage());
            }
            return new YamlConfiguration();
        }
    }
}
