package net.dripleafmc.core.ui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Chat text capture, used only by the chest fallback where dialogs can't take text. */
public final class Prompt implements Listener {

    private record Pending(Consumer<String> handler, long expiresAt) {}

    private final Map<UUID, Pending> waiting = new ConcurrentHashMap<>();
    private final Plugin plugin;

    public Prompt(Plugin plugin) {
        this.plugin = plugin;
    }

    public void ask(Player player, Consumer<String> handler) {
        waiting.put(player.getUniqueId(), new Pending(handler, System.currentTimeMillis() + 60_000L));
        player.sendMessage(Component.text("Type your answer in chat, or 'cancel'.", NamedTextColor.GRAY));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Pending pending = waiting.remove(event.getPlayer().getUniqueId());
        if (pending == null) return;
        event.setCancelled(true);
        if (pending.expiresAt() < System.currentTimeMillis()) return;

        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player player = event.getPlayer();
        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(Component.text("Cancelled.", NamedTextColor.GRAY));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> pending.handler().accept(input));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        waiting.remove(event.getPlayer().getUniqueId());
    }
}
