package net.dripleafmc.rebirth.reward;

import java.util.Locale;

/**
 * One line from a reward-set in rewards.yml, pre-parsed at load time.
 *
 * <pre>
 *   console: give &lt;player&gt; diamond 1
 *   player:  warp spawn
 *   op:      fly on
 *   message: &lt;green&gt;Nice one!
 *   broadcast: &lt;player&gt; ascended!
 *   delay: 20
 * </pre>
 */
public record RewardAction(Kind kind, String value) {

    public enum Kind {
        CONSOLE,
        PLAYER,
        OP,
        MESSAGE,
        BROADCAST,
        DELAY
    }

    public static RewardAction parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        int split = line.indexOf(':');
        if (split > 0) {
            String prefix = line.substring(0, split).trim().toUpperCase(Locale.ROOT);
            for (Kind kind : Kind.values()) {
                if (kind.name().equals(prefix)) {
                    return new RewardAction(kind, line.substring(split + 1).trim());
                }
            }
        }
        // No recognised prefix: treat the whole line as a console command.
        return new RewardAction(Kind.CONSOLE, line.trim());
    }

    public int delayTicks() {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
