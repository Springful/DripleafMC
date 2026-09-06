package net.dripleafmc.core.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A list of buttons with a title and some body text. Renderer-agnostic. */
public final class Menu {

    public final Component title;
    public final List<Component> body = new ArrayList<>(4);
    public final List<MenuButton> buttons = new ArrayList<>(16);
    public @Nullable Consumer<Player> onBack;
    public @Nullable Component backLabel;
    public int columns = 2;

    public Menu(Component title) {
        this.title = title;
    }

    public Menu body(Component line) {
        body.add(line);
        return this;
    }

    public Menu add(MenuButton button) {
        buttons.add(button);
        return this;
    }

    public Menu back(Component label, Consumer<Player> action) {
        this.backLabel = label;
        this.onBack = action;
        return this;
    }

    public Menu columns(int columns) {
        this.columns = Math.max(1, Math.min(4, columns));
        return this;
    }
}
