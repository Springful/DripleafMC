package net.dripleaf.core.common.ui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * One menu, described once, drawn either way.
 *
 * <p>This is the single most load-bearing type in the plugin's presentation
 * layer. Nothing in {@code core/} or {@code rebirth/} builds a dialog or an
 * inventory directly — they build a {@code Screen} and hand it to
 * {@link UiService}, which picks the renderer for that player. That is what
 * makes "dialogs everywhere, chest GUIs on Bedrock, and a toggle to prove it"
 * a routing decision rather than two parallel implementations that drift.
 *
 * <p>A screen is built per open, never cached — §14's lazy-construction rule.
 * Building one is a handful of small allocations; keeping one alive across
 * players is a correctness bug waiting to happen.
 */
public final class Screen {

    private final String id;
    private final String title;
    private final List<String> body;
    private final List<ScreenButton> buttons;
    private final List<ScreenInput> inputs;
    private final ChestLayout layout;
    private final ItemStack heroItem;
    private final boolean closeable;
    private final int dialogColumns;
    private final int dialogButtonWidth;
    private final Consumer<Player> onClose;
    private final BiConsumer<Player, Map<String, String>> onSubmit;
    private final String submitLabel;

    private Screen(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.body = List.copyOf(builder.body);
        this.buttons = List.copyOf(builder.buttons);
        this.inputs = List.copyOf(builder.inputs);
        this.layout = builder.layout;
        this.heroItem = builder.heroItem;
        this.closeable = builder.closeable;
        this.dialogColumns = builder.dialogColumns;
        this.dialogButtonWidth = builder.dialogButtonWidth;
        this.onClose = builder.onClose;
        this.onSubmit = builder.onSubmit;
        this.submitLabel = builder.submitLabel;
    }

    public static Builder of(String id, String title) {
        return new Builder(id, title);
    }

    /** Stable identifier — used for the chest holder and for log lines. */
    public String id() {
        return id;
    }

    /** MiniMessage source. */
    public String title() {
        return title;
    }

    /** Description lines. Dialog body; chest header lore. MiniMessage source. */
    public List<String> body() {
        return body;
    }

    public List<ScreenButton> buttons() {
        return buttons;
    }

    public List<ScreenInput> inputs() {
        return inputs;
    }

    public ChestLayout layout() {
        return layout;
    }

    /** Optional item shown large in a dialog and as the chest header icon. */
    public ItemStack heroItem() {
        return heroItem;
    }

    public boolean closeable() {
        return closeable;
    }

    public int dialogColumns() {
        return dialogColumns;
    }

    public int dialogButtonWidth() {
        return dialogButtonWidth;
    }

    public Consumer<Player> onClose() {
        return onClose;
    }

    /** Invoked with every {@link #inputs()} value once the player submits. */
    public BiConsumer<Player, Map<String, String>> onSubmit() {
        return onSubmit;
    }

    public String submitLabel() {
        return submitLabel;
    }

    public boolean hasInputs() {
        return !inputs.isEmpty();
    }

    public static final class Builder {

        private final String id;
        private String title;
        private final List<String> body = new ArrayList<>(16);
        private final List<ScreenButton> buttons = new ArrayList<>(8);
        private final List<ScreenInput> inputs = new ArrayList<>(2);
        private ChestLayout layout = ChestLayout.auto();
        private ItemStack heroItem;
        private boolean closeable = true;
        private int dialogColumns = 1;
        private int dialogButtonWidth = 220;
        private Consumer<Player> onClose = player -> {
        };
        private BiConsumer<Player, Map<String, String>> onSubmit;
        private String submitLabel = "";

        private Builder(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public Builder title(String value) {
            this.title = value;
            return this;
        }

        public Builder line(String value) {
            this.body.add(value);
            return this;
        }

        public Builder blank() {
            this.body.add("");
            return this;
        }

        public Builder lines(List<String> values) {
            if (values != null) {
                this.body.addAll(values);
            }
            return this;
        }

        public Builder button(ScreenButton value) {
            if (value != null) {
                this.buttons.add(value);
            }
            return this;
        }

        public Builder buttons(List<ScreenButton> values) {
            if (values != null) {
                this.buttons.addAll(values);
            }
            return this;
        }

        public Builder input(ScreenInput value) {
            this.inputs.add(value);
            return this;
        }

        public Builder layout(ChestLayout value) {
            this.layout = value == null ? ChestLayout.auto() : value;
            return this;
        }

        public Builder hero(ItemStack value) {
            this.heroItem = value;
            return this;
        }

        public Builder closeable(boolean value) {
            this.closeable = value;
            return this;
        }

        public Builder dialogColumns(int value) {
            this.dialogColumns = Math.max(1, Math.min(4, value));
            return this;
        }

        public Builder dialogButtonWidth(int value) {
            this.dialogButtonWidth = Math.max(1, Math.min(1024, value));
            return this;
        }

        public Builder onClose(Consumer<Player> value) {
            this.onClose = value == null ? player -> {
            } : value;
            return this;
        }

        /** Required when the screen has inputs; the label names the submit button. */
        public Builder onSubmit(String label, BiConsumer<Player, Map<String, String>> value) {
            this.submitLabel = label;
            this.onSubmit = value;
            return this;
        }

        public Screen build() {
            return new Screen(this);
        }
    }
}
