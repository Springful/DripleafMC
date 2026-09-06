package net.dripleafmc.core.listen;

import net.dripleafmc.core.DripleafCore;
import net.dripleafmc.core.profile.Profile;
import net.dripleafmc.core.sell.Sell;
import net.dripleafmc.core.util.Num;
import net.dripleafmc.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.Locale;

/**
 * All gameplay hooks in one listener.
 *
 * The hot handlers here (block break/place) do nothing but bump a counter on an object
 * already in memory — no lookups, no I/O, no allocation.
 */
public final class CoreListener implements Listener {

    private final DripleafCore core;

    public CoreListener(DripleafCore core) {
        this.core = core;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        core.profiles().loadBlocking(event.getUniqueId(), event.getName());
        core.homes().loadBlocking(event.getUniqueId());
        core.quickBuy().loadBlocking(event.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Profile profile = core.profiles().get(player.getUniqueId());
        if (profile == null) {
            // Pre-login was skipped (plugin reload with players online) — load inline.
            profile = core.profiles().loadBlocking(player.getUniqueId(), player.getName());
            core.homes().loadBlocking(player.getUniqueId());
            core.quickBuy().loadBlocking(player.getUniqueId());
        }
        profile.name = player.getName();
        profile.beginSession();
        profile.touch();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (core.cfg().killOnLogout && core.combat().tagged(player)) {
            player.setHealth(0);
            Bukkit.broadcast(core.lang().prefixed("combat.logout", Text.p("name", player.getName())));
        }
        core.combat().clear(player.getUniqueId());
        core.teleports().cancel(player.getUniqueId());
        core.tpa().forget(player.getUniqueId());
        core.shards().forget(player.getUniqueId());
        core.homes().unload(player.getUniqueId());
        core.quickBuy().unload(player.getUniqueId());
        core.profiles().unload(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Profile profile = core.profiles().get(event.getPlayer().getUniqueId());
        if (profile == null) return;
        profile.blocksBroken++;
        profile.touch();
        core.shards().markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Profile profile = core.profiles().get(event.getPlayer().getUniqueId());
        if (profile == null) return;
        profile.blocksPlaced++;
        profile.touch();
        core.shards().markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.equals(victim)) return;
        core.combat().tag(attacker);
        core.combat().tag(victim);
        core.shards().markActive(attacker);
    }

    private static Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) return shooter;
        return null;
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        Profile profile = core.profiles().get(killer.getUniqueId());
        if (profile == null) return;
        profile.mobKills++;
        profile.touch();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Profile victimProfile = core.profiles().get(victim.getUniqueId());
        if (victimProfile != null) {
            victimProfile.deaths++;
            victimProfile.killStreak = 0;
            victimProfile.touch();
        }
        core.combat().clear(victim.getUniqueId());

        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        Profile killerProfile = core.profiles().get(killer.getUniqueId());
        if (killerProfile != null) {
            killerProfile.kills++;
            killerProfile.killStreak++;
            if (killerProfile.killStreak > killerProfile.bestStreak) {
                killerProfile.bestStreak = killerProfile.killStreak;
            }
            killerProfile.touch();
        }
        core.shards().onKill(killer, victim);

        double payout = core.bounties().claim(victim.getUniqueId());
        if (payout > 0) {
            core.money().give(killer, payout, killerProfile);
            Bukkit.broadcast(core.lang().prefixed("bounty.claimed",
                    Text.p("killer", killer.getName()),
                    Text.p("victim", victim.getName()),
                    Text.p("amount", Num.money(payout))));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!core.cfg().combatEnabled) return;
        Player player = event.getPlayer();
        if (!core.combat().tagged(player)) return;

        String message = event.getMessage();
        int space = message.indexOf(' ');
        String label = (space == -1 ? message.substring(1) : message.substring(1, space)).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon != -1) label = label.substring(colon + 1);

        if (core.combat().blocked(label)) {
            event.setCancelled(true);
            core.lang().send(player, "teleport.combat");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof Sell.Holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        Sell.Result result = core.sell().sell(player, inventory);
        core.sell().announce(player, result);

        // Anything left was unsellable — give it straight back rather than voiding it.
        for (var stack : inventory.getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            for (var leftover : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        inventory.clear();
    }
}
