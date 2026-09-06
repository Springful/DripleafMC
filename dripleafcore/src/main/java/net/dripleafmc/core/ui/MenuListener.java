package net.dripleafmc.core.ui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.function.Consumer;

/** Routes clicks in chest-rendered menus. Menus are read-only, so every click is cancelled. */
public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ChestRenderer.Holder menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Consumer<Player> action = event.isRightClick()
                ? menu.secondary.getOrDefault(event.getRawSlot(), menu.actions.get(event.getRawSlot()))
                : menu.actions.get(event.getRawSlot());
        if (action != null) action.accept(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ChestRenderer.Holder) event.setCancelled(true);
    }
}
