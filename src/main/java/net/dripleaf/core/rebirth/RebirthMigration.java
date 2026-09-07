package net.dripleaf.core.rebirth;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.storage.PlayerData;
import net.dripleaf.core.common.text.Ctx;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * The one-time import from LuckPerms groups.
 *
 * <p>The Skript kept a player's tier in {@code group.rebirth-N}. Reading that on
 * every menu open costs up to twenty-five permission lookups per open, so the
 * new system holds the tier as an integer in player data — but the group still
 * has to be honoured for anyone who earned it under the old system.
 *
 * <p>So: on each join, if the player's stored tier is 0 and they hold a
 * {@code rebirth-N} group, the group's number is written into player data and
 * the migration is recorded. It is idempotent by construction — a player with a
 * stored tier is never re-read — and it runs before {@code tier-source: data} is
 * trusted for that player.
 *
 * <p>The group grant itself stays as a reward command in {@code tiers.yml}, so
 * external plugins keying off {@code group.rebirth-N} keep working. It simply
 * stops being the source of truth.
 */
public final class RebirthMigration implements Listener {

    private final Services services;
    private final RebirthService rebirth;
    private int migrated;

    public RebirthMigration(Services services, RebirthService rebirth) {
        this.services = services;
        this.rebirth = rebirth;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!rebirth.settings().migrateFromLuckPerms()
                || !services.hooks().luckPerms().available()) {
            return;
        }
        Player player = event.getPlayer();

        // The profile load is asynchronous; give it a moment before deciding
        // that a stored tier of zero means "never rebirthed".
        services.schedulers().delaySync(() -> {
            if (!player.isOnline()) {
                return;
            }
            PlayerData data = services.players().get(player);
            if (data.rebirthTier() > 0) {
                return;
            }
            int fromGroups = highestGroup(player);
            if (fromGroups <= 0) {
                return;
            }
            data.rebirthTier(fromGroups);
            data.rebirthTotal(Math.max(data.rebirthTotal(), fromGroups));
            rebirth.invalidate(player.getUniqueId());
            migrated++;
            services.plugin().getLogger().info("Migrated " + player.getName()
                    + " to rebirth tier " + fromGroups + " from their LuckPerms group.");
            services.audit().rebirth("MIGRATE " + player.getName() + " -> tier " + fromGroups);
        }, 40L);
    }

    private int highestGroup(Player player) {
        String format = rebirth.settings().permissionFormat();
        for (int tier = rebirth.tiers().max(); tier >= 1; tier--) {
            if (player.hasPermission(new Ctx().put("tier", tier).applyRaw(format))) {
                return tier;
            }
        }
        return 0;
    }

    /** How many players this session has imported — reported on Diagnostics. */
    public int migratedCount() {
        return migrated;
    }
}
