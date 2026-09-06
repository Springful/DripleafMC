package net.dripleafmc.rebirth.ui;

import net.dripleafmc.rebirth.tier.RebirthPath;
import org.bukkit.entity.Player;

/**
 * The front end contract. MenuUI and DialogUI are the two implementations;
 * neither owns any progression logic, they only render what
 * {@link net.dripleafmc.rebirth.core.RebirthService} tells them.
 */
public interface RebirthUI {

    /** Path selection screen. */
    void openMain(Player player);

    /** Point-of-no-return confirmation for one path. */
    void openConfirm(Player player, RebirthPath path);

    /** Called on reload so an implementation can drop cached layout. */
    default void invalidate() {
    }
}
