package net.dripleaf.core.common.ui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * One listener for every chest-rendered screen in the plugin.
 *
 * <p>Dispatch is by {@link ChestHolder}, never by title, and registration
 * happens exactly once at enable — reload swaps data, never listeners.
 */
public final class ChestListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof ChestHolder holder)) {
            return;
        }
        // Cancel first, unconditionally: a shift-click from the player's own
        // inventory would otherwise shovel items into a menu that never gives
        // them back.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        holder.click(player, event.getRawSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof ChestHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof ChestHolder holder
                && event.getPlayer() instanceof Player player) {
            holder.closed(player);
        }
    }
}
