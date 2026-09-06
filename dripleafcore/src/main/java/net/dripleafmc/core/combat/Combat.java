package net.dripleafmc.core.combat;

import net.dripleafmc.core.config.Cfg;
import net.dripleafmc.core.config.Lang;
import net.dripleafmc.core.util.Text;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Combat tagging with no scheduler at all: tags carry an expiry timestamp and are
 * checked lazily. A player who never fights again costs nothing to keep tracked.
 */
public final class Combat {

    private final Map<UUID, Long> tagged = new HashMap<>(32);
    private final Cfg cfg;
    private final Lang lang;

    public Combat(Cfg cfg, Lang lang) {
        this.cfg = cfg;
        this.lang = lang;
    }

    public void tag(Player player) {
        if (!cfg.combatEnabled) return;
        if (player.hasPermission("dripleaf.combat.bypass")) return;
        long until = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cfg.combatSeconds);
        Long previous = tagged.put(player.getUniqueId(), until);
        if (previous == null || previous < System.currentTimeMillis()) {
            player.sendActionBar(lang.get("combat.tagged", Text.p("time", String.valueOf(cfg.combatSeconds))));
        }
    }

    public boolean tagged(Player player) {
        if (!cfg.combatEnabled) return false;
        Long until = tagged.get(player.getUniqueId());
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            tagged.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public long secondsLeft(Player player) {
        Long until = tagged.get(player.getUniqueId());
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    public void clear(UUID uuid) {
        tagged.remove(uuid);
    }

    public boolean blocked(String command) {
        return cfg.combatBlocked.contains(command);
    }

    /** Returns true and messages the player when the action must be refused. */
    public boolean deny(Player player) {
        if (!tagged(player)) return false;
        lang.send(player, "teleport.combat");
        return true;
    }
}
