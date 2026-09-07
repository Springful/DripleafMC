package net.dripleaf.core.rebirth.reward;

import java.util.List;

/**
 * One unlock at one tier.
 *
 * <p>Each carries both the string players are shown and the commands that
 * actually grant it, in the same config block — so the display and the grant
 * can never drift apart again. They already had: the executing Skript granted
 * bonus keys at tiers 5, 10, 15 and 20 that the info menu never mentioned.
 */
public record Unlock(String display, List<String> commands) {
}
