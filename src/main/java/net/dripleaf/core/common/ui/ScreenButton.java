package net.dripleaf.core.common.ui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One actionable element of a {@link Screen}, renderer-agnostic.
 *
 * <p>The dialog renderer turns it into an {@code ActionButton} with a tooltip;
 * the chest renderer turns it into an item with lore. Both run the same
 * {@link #action()}, so a click means the same thing on Java and on Bedrock.
 *
 * <p>A disabled button is still drawn — greyed, with {@link #lockedReason()}
 * shown. Visible goals drive progression; hidden ones do not.
 */
public final class ScreenButton {

    private final String id;
    private final String label;
    private final List<String> description;
    private final ButtonStyle style;
    private final boolean enabled;
    private final String lockedReason;
    private final Material material;
    private final ItemStack customItem;
    private final int slot;
    private final boolean glint;
    private final int amount;
    private final Consumer<Player> action;

    private ScreenButton(Builder builder) {
        this.id = builder.id;
        this.label = builder.label;
        this.description = List.copyOf(builder.description);
        this.style = builder.style;
        this.enabled = builder.enabled;
        this.lockedReason = builder.lockedReason;
        this.material = builder.material;
        this.customItem = builder.customItem;
        this.slot = builder.slot;
        this.glint = builder.glint;
        this.amount = builder.amount;
        this.action = builder.action;
    }

    public static Builder of(String id, String label) {
        return new Builder(id, label);
    }

    public String id() {
        return id;
    }

    /** MiniMessage source, already styled. */
    public String label() {
        return label;
    }

    /** Tooltip in a dialog, lore in a chest. MiniMessage source, one entry per line. */
    public List<String> description() {
        return description;
    }

    public ButtonStyle style() {
        return enabled ? style : ButtonStyle.LOCKED;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Why this is locked. Shown in the tooltip and appended to the lore. */
    public String lockedReason() {
        return lockedReason;
    }

    /** Chest icon. Falls back to the style's material. */
    public Material material() {
        return material == null ? style().material() : material;
    }

    /** A fully built stack wins over {@link #material()} — used for shop items. */
    public ItemStack customItem() {
        return customItem;
    }

    /** Explicit chest slot, or {@code -1} to let the renderer arrange it. */
    public int slot() {
        return slot;
    }

    public boolean glint() {
        return glint;
    }

    public int amount() {
        return amount;
    }

    public Consumer<Player> action() {
        return action;
    }

    public static final class Builder {

        private final String id;
        private String label;
        private final List<String> description = new ArrayList<>(8);
        private ButtonStyle style = ButtonStyle.NEUTRAL;
        private boolean enabled = true;
        private String lockedReason = "";
        private Material material;
        private ItemStack customItem;
        private int slot = -1;
        private boolean glint;
        private int amount = 1;
        private Consumer<Player> action = player -> {
        };

        private Builder(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public Builder label(String value) {
            this.label = value;
            return this;
        }

        public Builder line(String value) {
            this.description.add(value);
            return this;
        }

        public Builder lines(List<String> values) {
            this.description.addAll(values);
            return this;
        }

        public Builder style(ButtonStyle value) {
            this.style = value;
            return this;
        }

        /** Disables the button and records why, in one call. */
        public Builder locked(String reason) {
            this.enabled = false;
            this.lockedReason = reason;
            return this;
        }

        public Builder enabled(boolean value) {
            this.enabled = value;
            return this;
        }

        public Builder material(Material value) {
            this.material = value;
            return this;
        }

        public Builder item(ItemStack value) {
            this.customItem = value;
            return this;
        }

        public Builder slot(int value) {
            this.slot = value;
            return this;
        }

        public Builder glint(boolean value) {
            this.glint = value;
            return this;
        }

        public Builder amount(int value) {
            this.amount = Math.max(1, Math.min(64, value));
            return this;
        }

        public Builder action(Consumer<Player> value) {
            this.action = value == null ? player -> {
            } : value;
            return this;
        }

        public ScreenButton build() {
            return new ScreenButton(this);
        }
    }
}
