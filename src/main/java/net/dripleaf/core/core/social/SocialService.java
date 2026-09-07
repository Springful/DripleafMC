package net.dripleaf.core.core.social;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The small per-session state the chat and social commands share: private
 * message reply targets, AFK, vanish and social spy.
 *
 * <p>Nicknames and ignore lists persist in player data; everything here is
 * session state and is evicted on quit, so none of these maps can grow.
 */
public final class SocialService implements Listener {

    private final Services services;
    /** Who to reply to, per player. */
    private final Map<UUID, UUID> replyTargets = new ConcurrentHashMap<>();
    private final Set<UUID> afk = ConcurrentHashMap.newKeySet();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Set<UUID> socialSpy = ConcurrentHashMap.newKeySet();
    private final Set<UUID> god = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();

    private long afkAfterMillis = 300_000L;

    public SocialService(Services services) {
        this.services = services;
    }

    public void configure(long afkAfterSeconds) {
        this.afkAfterMillis = Math.max(30L, afkAfterSeconds) * 1000L;
    }

    /** One sweep for everyone, every ten seconds — not one task per player. */
    public void startAfkSweep() {
        services.schedulers().repeatSync(() -> {
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                Long last = lastActivity.get(player.getUniqueId());
                if (last == null) {
                    lastActivity.put(player.getUniqueId(), now);
                    continue;
                }
                if (now - last >= afkAfterMillis && afk.add(player.getUniqueId())) {
                    announce(player, true);
                }
            }
        }, 200L, 200L);
    }

    // ------------------------------------------------------------- messaging

    /** @return false when the message was blocked (ignored, or target offline) */
    public boolean message(Player from, Player to, String text) {
        if (services.players().get(to).ignored().contains(from.getUniqueId())
                && !from.hasPermission("dripleaf.msg.bypassignore")) {
            services.messages().send(from, "social.ignored-by",
                    Ctx.of("player", displayName(to)));
            return false;
        }
        Ctx ctx = new Ctx()
                .put("from", displayName(from))
                .put("to", displayName(to))
                .put("message", text);
        services.messages().send(from, "social.msg-out", ctx);
        services.messages().send(to, "social.msg-in", ctx);

        replyTargets.put(from.getUniqueId(), to.getUniqueId());
        replyTargets.put(to.getUniqueId(), from.getUniqueId());

        Component spy = Text.parse(new Ctx().putAll(ctx)
                .applyRaw(services.messages().raw("social.spy-format")));
        for (UUID uuid : socialSpy) {
            Player watcher = Bukkit.getPlayer(uuid);
            if (watcher != null && !watcher.equals(from) && !watcher.equals(to)) {
                watcher.sendMessage(spy);
            }
        }
        return true;
    }

    public Player replyTarget(Player player) {
        UUID uuid = replyTargets.get(player.getUniqueId());
        return uuid == null ? null : Bukkit.getPlayer(uuid);
    }

    // ------------------------------------------------------------------ afk

    public boolean afk(Player player) {
        return afk.contains(player.getUniqueId());
    }

    public void toggleAfk(Player player) {
        boolean nowAfk = afk.contains(player.getUniqueId())
                ? !afk.remove(player.getUniqueId())
                : afk.add(player.getUniqueId());
        announce(player, nowAfk);
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void announce(Player player, boolean nowAfk) {
        Ctx ctx = Ctx.of("player", displayName(player));
        Component line = Text.parse(ctx.applyRaw(
                services.messages().raw(nowAfk ? "social.afk-on" : "social.afk-off")));
        Bukkit.broadcast(line);
    }

    // --------------------------------------------------------------- vanish

    public boolean vanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    public boolean toggleVanish(Player player) {
        boolean nowVanished;
        if (vanished.remove(player.getUniqueId())) {
            nowVanished = false;
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.showPlayer(services.plugin(), player);
            }
        } else {
            vanished.add(player.getUniqueId());
            nowVanished = true;
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.hasPermission("dripleaf.vanish.see")) {
                    other.hidePlayer(services.plugin(), player);
                }
            }
        }
        return nowVanished;
    }

    // ----------------------------------------------------------- social spy

    public boolean toggleSocialSpy(Player player) {
        return socialSpy.contains(player.getUniqueId())
                ? !socialSpy.remove(player.getUniqueId())
                : socialSpy.add(player.getUniqueId());
    }

    public boolean spying(Player player) {
        return socialSpy.contains(player.getUniqueId());
    }

    // ------------------------------------------------------------------ god

    public boolean god(Player player) {
        return god.contains(player.getUniqueId());
    }

    public boolean toggleGod(Player player) {
        return god.contains(player.getUniqueId())
                ? !god.remove(player.getUniqueId())
                : god.add(player.getUniqueId());
    }

    // -------------------------------------------------------------- ignores

    public boolean toggleIgnore(Player player, UUID target) {
        Set<UUID> ignored = services.players().get(player).ignored();
        boolean nowIgnored = ignored.contains(target) ? !ignored.remove(target)
                : ignored.add(target);
        services.players().get(player).markDirty();
        return nowIgnored;
    }

    public Set<UUID> ignored(Player player) {
        return new HashSet<>(services.players().get(player).ignored());
    }

    // ------------------------------------------------------------ nicknames

    /** The nickname if set, otherwise the real name. Always MiniMessage-safe. */
    public String displayName(Player player) {
        String nickname = services.players().get(player).nickname();
        return nickname.isBlank() ? player.getName() : nickname;
    }

    public Player byNickname(String nickname) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (displayName(player).equalsIgnoreCase(nickname)) {
                return player;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- events

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        lastActivity.put(uuid, System.currentTimeMillis());
        if (afk.remove(uuid)) {
            announce(event.getPlayer(), false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        replyTargets.remove(uuid);
        replyTargets.values().remove(uuid);
        afk.remove(uuid);
        vanished.remove(uuid);
        socialSpy.remove(uuid);
        god.remove(uuid);
        lastActivity.remove(uuid);
    }
}
