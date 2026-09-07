package net.dripleaf.core.common.ui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dripleaf.core.common.scheduler.Schedulers;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.MessageService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * "Type your answer in chat."
 *
 * <p>A chest GUI cannot host a text field, so this is how the chest renderer
 * satisfies a {@link ScreenInput}: the menu closes, the player types, and the
 * screen reopens with the value. It is also the only input path that is
 * reliable on Bedrock, which is exactly why it exists rather than an anvil-rename
 * trick.
 *
 * <p>Pending prompts are held in one bounded map, evicted on answer, on cancel
 * and on quit. The chat event is cancelled for a player who has one open so
 * their answer never reaches public chat — typing a shop search into global is
 * a small embarrassment, typing a confirmation numeral is a bigger one.
 */
public final class ChatPrompt implements Listener {

    private record Pending(Consumer<String> onAnswer, Runnable onCancel, String cancelWord) {
    }

    private final Schedulers schedulers;
    private final MessageService messages;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatPrompt(Schedulers schedulers, MessageService messages) {
        this.schedulers = schedulers;
        this.messages = messages;
    }

    /**
     * Asks {@code player} for a line of text.
     *
     * @param promptKey {@code messages.yml} key describing what is wanted
     * @param onAnswer  run on the main thread with the typed text
     * @param onCancel  run on the main thread if the player types the cancel word
     */
    public void ask(Player player, String promptKey, Ctx ctx,
                    Consumer<String> onAnswer, Runnable onCancel) {
        String cancelWord = messages.raw("ui.prompt-cancel-word");
        pending.put(player.getUniqueId(), new Pending(onAnswer, onCancel, cancelWord));
        player.closeInventory();
        messages.send(player, promptKey, ctx);
        messages.send(player, "ui.prompt-cancel-hint", Ctx.of("word", cancelWord));
    }

    public boolean waiting(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    public void cancel(Player player) {
        pending.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending prompt = pending.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);

        String answer = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();

        // Chat arrives off the main thread; everything the callback touches is not.
        schedulers.sync(() -> {
            if (answer.equalsIgnoreCase(prompt.cancelWord())) {
                messages.send(player, "ui.prompt-cancelled");
                prompt.onCancel().run();
                return;
            }
            prompt.onAnswer().accept(answer);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
