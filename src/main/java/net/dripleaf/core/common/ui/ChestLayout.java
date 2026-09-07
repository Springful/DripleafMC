package net.dripleaf.core.common.ui;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;

/**
 * How a {@link Screen} is shaped when it is drawn as a chest GUI.
 *
 * <p>Ignored entirely by the dialog renderer. A screen that says nothing here
 * gets {@link #auto()}, which sizes itself to the number of buttons.
 *
 * @param type      {@code CHEST} for a sized grid, {@code HOPPER} for the compact
 *                  five-slot bar the rebirth path menu uses
 * @param rows      chest rows, 1-6; ignored for a hopper
 * @param decorated whether to lay a filler pane behind the empty slots
 * @param filler    the pane material used when {@code decorated}
 * @param headerSlot where the title/summary item goes, or {@code -1} for none
 */
public record ChestLayout(InventoryType type, int rows, boolean decorated, Material filler,
                          int headerSlot) {

    private static final ChestLayout AUTO =
            new ChestLayout(InventoryType.CHEST, 0, true, Material.GRAY_STAINED_GLASS_PANE, 4);

    /** Size from the button count, decorated, with a header item at the top centre. */
    public static ChestLayout auto() {
        return AUTO;
    }

    /** A fixed-height chest. */
    public static ChestLayout chest(int rows) {
        return new ChestLayout(InventoryType.CHEST, clampRows(rows), true,
                Material.GRAY_STAINED_GLASS_PANE, 4);
    }

    /** The compact five-slot bar. Buttons must carry explicit slots. */
    public static ChestLayout hopper() {
        return new ChestLayout(InventoryType.HOPPER, 1, false,
                Material.GRAY_STAINED_GLASS_PANE, -1);
    }

    public ChestLayout undecorated() {
        return new ChestLayout(type, rows, false, filler, headerSlot);
    }

    public ChestLayout withHeaderSlot(int slot) {
        return new ChestLayout(type, rows, decorated, filler, slot);
    }

    public ChestLayout withFiller(Material material) {
        return new ChestLayout(type, rows, decorated, material, headerSlot);
    }

    public boolean isHopper() {
        return type == InventoryType.HOPPER;
    }

    /** True when the row count should be derived from the content. */
    public boolean autoSized() {
        return type == InventoryType.CHEST && rows <= 0;
    }

    private static int clampRows(int rows) {
        return Math.max(1, Math.min(6, rows));
    }
}
