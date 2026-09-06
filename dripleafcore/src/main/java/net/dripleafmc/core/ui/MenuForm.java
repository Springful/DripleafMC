package net.dripleafmc.core.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A menu that collects input. Dialogs render this as a confirmation dialog with real
 * inputs; chest menus fall back to preset buttons (numbers), toggles (booleans) or a
 * chat prompt (text), which is why only one input *kind* is allowed per form.
 */
public final class MenuForm {

    public record Bool(String key, Component label, boolean initial) {}

    public final Component title;
    public final List<Component> body = new ArrayList<>(4);

    public @Nullable String numberKey;
    public Component numberLabel = Component.empty();
    public float min = 1, max = 64, initial = 1, step = 1;
    public int[] chestPresets = {1, 8, 16, 32, 64};

    public @Nullable String textKey;
    public Component textLabel = Component.empty();
    public String textInitial = "";
    public int textMaxLength = 16;

    public final List<Bool> bools = new ArrayList<>(4);

    /** Rendered as an item body at the top of the dialog. */
    public @Nullable org.bukkit.inventory.ItemStack icon;

    public Component confirmLabel = Component.text("Confirm");
    public Component cancelLabel = Component.text("Cancel");
    public BiConsumer<Player, MenuValues> onConfirm = (p, v) -> {};
    public @Nullable Consumer<Player> onCancel;

    public MenuForm(Component title) {
        this.title = title;
    }

    public MenuForm icon(org.bukkit.inventory.ItemStack stack) {
        this.icon = stack;
        return this;
    }

    public MenuForm body(Component line) {
        body.add(line);
        return this;
    }

    public MenuForm number(String key, Component label, float min, float max, float initial, float step) {
        this.numberKey = key;
        this.numberLabel = label;
        this.min = min;
        this.max = max;
        this.initial = initial;
        this.step = step;
        return this;
    }

    public MenuForm presets(int... presets) {
        this.chestPresets = presets;
        return this;
    }

    public MenuForm text(String key, Component label, String initial, int maxLength) {
        this.textKey = key;
        this.textLabel = label;
        this.textInitial = initial;
        this.textMaxLength = maxLength;
        return this;
    }

    public MenuForm bool(String key, Component label, boolean initial) {
        bools.add(new Bool(key, label, initial));
        return this;
    }

    public MenuForm confirm(Component label, BiConsumer<Player, MenuValues> handler) {
        this.confirmLabel = label;
        this.onConfirm = handler;
        return this;
    }

    public MenuForm cancel(Component label, Consumer<Player> handler) {
        this.cancelLabel = label;
        this.onCancel = handler;
        return this;
    }
}
