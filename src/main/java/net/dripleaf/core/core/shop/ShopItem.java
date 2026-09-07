package net.dripleaf.core.core.shop;

import org.bukkit.Material;

import java.math.BigDecimal;
import java.util.List;

/**
 * One purchasable or sellable entry.
 *
 * @param key         unique within the file; also the transaction-log identifier
 * @param material    what the player receives or hands over
 * @param category    category key; an unknown one lands in "Uncategorised"
 * @param display     shown name, defaulting to a prettified material name
 * @param buy         unit purchase price, or {@code null} when buying is disabled
 * @param sell        unit sale price, or {@code null} when selling is disabled
 * @param stackSizes  quantities offered in the buy screen
 * @param permission  required to see or buy this item; blank means everyone
 * @param lore        extra MiniMessage lines
 * @param maxPerDay   0 for unlimited
 * @param commands    run on purchase, in addition to (or instead of) giving the item
 * @param giveItem    whether the material is actually handed over. Defaults to
 *                    false for an entry that has commands — those entries use
 *                    the material as an icon and the command as the product —
 *                    and true otherwise. Set it explicitly to do both.
 */
public record ShopItem(String key, Material material, String category, String display,
                       BigDecimal buy, BigDecimal sell, List<Integer> stackSizes,
                       String permission, List<String> lore, int maxPerDay,
                       List<String> commands, boolean giveItem) {

    public boolean buyable() {
        return buy != null && buy.signum() >= 0;
    }

    public boolean sellable() {
        return sell != null && sell.signum() >= 0;
    }

    /** True when {@code sell} exceeds {@code buy} — a money printer, warned about at load. */
    public boolean inverted() {
        return buyable() && sellable() && sell.compareTo(buy) > 0;
    }
}
