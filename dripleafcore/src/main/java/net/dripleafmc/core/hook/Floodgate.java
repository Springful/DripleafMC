package net.dripleafmc.core.hook;

import org.bukkit.Bukkit;

import java.util.UUID;

/**
 * Bedrock detection without a Floodgate compile dependency.
 *
 * Floodgate issues Bedrock players a UUID whose most significant bits are zero
 * (the "0000-0000-0000-000x" form), which is a stable, documented property — far
 * cheaper than a reflective API call on every menu open.
 */
public final class Floodgate {

    private static boolean present;

    private Floodgate() {}

    public static void detect() {
        present = Bukkit.getPluginManager().isPluginEnabled("floodgate");
    }

    public static boolean present() {
        return present;
    }

    public static boolean isBedrock(UUID uuid) {
        return present && uuid.getMostSignificantBits() == 0L;
    }
}
