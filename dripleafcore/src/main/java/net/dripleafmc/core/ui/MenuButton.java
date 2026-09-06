package net.dripleafmc.core.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * One entry in a menu. Rendered as a dialog action button, or as an inventory item
 * when the viewer is on chest menus.
 */
public record MenuButton(Component label,
                         @Nullable Component tooltip,
                         List<Component> lore,
                         Material icon,
                         int amount,
                         Consumer<Player> onClick,
                         @Nullable Consumer<Player> onRightClick,
                         @Nullable BiConsumer<Player, MenuValues> onSubmit) {

    public static MenuButton of(Component label, Material icon, Consumer<Player> onClick) {
        return new MenuButton(label, null, List.of(), icon, 1, onClick, null, null);
    }

    public static MenuButton of(Component label, Component tooltip, Material icon, Consumer<Player> onClick) {
        return new MenuButton(label, tooltip, tooltip == null ? List.of() : List.of(tooltip), icon, 1, onClick, null, null);
    }

    /** Chest menus dispatch the secondary action on right-click; dialogs ignore it. */
    public static MenuButton of(Component label, List<Component> lore, Material icon, int amount,
                                Consumer<Player> onClick, Consumer<Player> onRightClick) {
        return new MenuButton(label, lore.isEmpty() ? null : lore.get(0), lore, icon, amount,
                onClick, onRightClick, null);
    }

    /**
     * A button that needs whatever the viewer typed into the same dialog. Dialogs call
     * onSubmit with the inputs; chest menus have no inputs and fall back to onClick.
     */
    public static MenuButton submitting(Component label, Component tooltip, Material icon,
                                        Consumer<Player> onClick,
                                        BiConsumer<Player, MenuValues> onSubmit) {
        return new MenuButton(label, tooltip, tooltip == null ? List.of() : List.of(tooltip), icon, 1,
                onClick, null, onSubmit);
    }

    public static MenuButton of(Component label, List<Component> lore, Material icon, int amount, Consumer<Player> onClick) {
        return new MenuButton(label, lore.isEmpty() ? null : lore.get(0), lore, icon, amount, onClick, null, null);
    }
}
