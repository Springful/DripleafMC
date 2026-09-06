package net.dripleafmc.rebirth.requirement;

import org.bukkit.entity.Player;

/**
 * A single gate on a tier. Implementations are built once at config load and
 * are stateless afterwards, so evaluating a whole menu is a handful of
 * primitive reads rather than a string-parsing pass.
 */
public interface Requirement {

    /** Config key, used for admin output and error messages. */
    String id();

    /** Raw MiniMessage display name, e.g. "sᴛᴏɴᴇ ᴍɪɴᴇᴅ". */
    String display();

    /** How far this player has got. */
    double progress(Player player);

    /** What they need. */
    double target();

    default boolean met(Player player) {
        return progress(player) >= target();
    }

    default double percent(Player player) {
        double goal = target();
        if (goal <= 0d) {
            return 100d;
        }
        return Math.min(100d, progress(player) / goal * 100d);
    }
}
