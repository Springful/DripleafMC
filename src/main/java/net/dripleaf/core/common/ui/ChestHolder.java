package net.dripleaf.core.common.ui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Identity and click routing for a chest-rendered {@link Screen}.
 *
 * <p>Matching on a stripped inventory title breaks the moment another plugin
 * opens a similarly named menu. A holder is exact, free, and carries the slot
 * to action map with it, so the listener never has to demultiplex anything.
 */
public final class ChestHolder implements InventoryHolder {

    private final String screenId;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>(16);
    private Consumer<Player> onClose = player -> {
    };
    private Inventory inventory;
    /** Set while the renderer itself is swapping screens, to suppress the close hook. */
    private boolean reopening;

    public ChestHolder(String screenId) {
        this.screenId = screenId;
    }

    public String screenId() {
        return screenId;
    }

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    void action(int slot, Consumer<Player> action) {
        actions.put(slot, action);
    }

    void onClose(Consumer<Player> action) {
        this.onClose = action == null ? player -> {
        } : action;
    }

    public void click(Player player, int slot) {
        Consumer<Player> action = actions.get(slot);
        if (action != null) {
            action.accept(player);
        }
    }

    public void closed(Player player) {
        if (!reopening) {
            onClose.accept(player);
        }
    }

    /** Marks the next close as an internal navigation rather than the player leaving. */
    public void reopening() {
        this.reopening = true;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
