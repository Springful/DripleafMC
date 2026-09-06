package net.dripleafmc.rebirth.ui.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Identity for our inventories.
 * <p>
 * The Skript matched on the stripped inventory title, which breaks the moment
 * another plugin opens a similarly named menu. A holder is exact and free.
 */
public final class RebirthHolder implements InventoryHolder {

    private final Map<Integer, Consumer<Player>> actions = new HashMap<>(8);
    private Inventory inventory;

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    public void action(int slot, Consumer<Player> action) {
        actions.put(slot, action);
    }

    public void click(Player player, int slot) {
        Consumer<Player> action = actions.get(slot);
        if (action != null) {
            action.accept(player);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
