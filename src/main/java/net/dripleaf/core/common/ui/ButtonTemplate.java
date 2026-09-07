package net.dripleaf.core.common.ui;

import net.dripleaf.core.common.config.Cfg;
import net.dripleaf.core.common.icon.IconService;
import net.dripleaf.core.common.text.Ctx;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * One button's appearance, read from {@code menus.yml}.
 *
 * <p>The split this type exists to enforce: <b>config owns how a button looks,
 * code owns what it does</b>. A screen builder asks for a template by id,
 * supplies the action and any dynamic values, and never writes a label, an icon
 * or a slot in Java.
 *
 * <p>Labels and lore are MiniMessage templates resolved against a {@link Ctx},
 * so a shop item's button can say {@code <display>} and {@code <buy_price>} and
 * have those filled in per render.
 */
public final class ButtonTemplate {

    private final String id;
    private final String label;
    private final String lockedLabel;
    private final List<String> lore;
    private final Material icon;
    private final ButtonStyle style;
    private final int slot;
    private final int amount;
    private final boolean glint;
    private final boolean hidden;
    private final String action;
    private final String permission;

    ButtonTemplate(String id, Cfg cfg, ButtonTemplate fallback) {
        this.id = id;
        String defaultLabel = fallback == null ? id : fallback.label;
        this.label = cfg.string("label", defaultLabel);
        this.lockedLabel = cfg.string("locked-label",
                fallback == null ? "" : fallback.lockedLabel);
        List<String> configuredLore = cfg.stringList("lore");
        this.lore = configuredLore.isEmpty() && fallback != null
                ? fallback.lore
                : List.copyOf(configuredLore);
        this.icon = IconService.material(cfg.string("icon", ""),
                fallback == null ? null : fallback.icon);
        this.style = cfg.enumValue("style", ButtonStyle.class,
                fallback == null ? ButtonStyle.NEUTRAL : fallback.style);
        this.slot = cfg.integer("slot", fallback == null ? -1 : fallback.slot, -1, 53);
        this.amount = cfg.integer("amount", fallback == null ? 1 : fallback.amount, 1, 64);
        this.glint = cfg.bool("glint", fallback != null && fallback.glint);
        this.hidden = cfg.bool("hidden", fallback != null && fallback.hidden);
        this.action = cfg.string("action", fallback == null ? "" : fallback.action);
        this.permission = cfg.string("permission",
                fallback == null ? "" : fallback.permission);
    }

    /** The placeholder used when {@code menus.yml} has no entry for a button. */
    static ButtonTemplate missing(String id) {
        return new ButtonTemplate(id);
    }

    private ButtonTemplate(String id) {
        this.id = id;
        this.label = "<#FF5555>" + id + "</#FF5555>";
        this.lockedLabel = "";
        this.lore = List.of("<#555555>Missing from menus.yml</#555555>");
        this.icon = Material.BARRIER;
        this.style = ButtonStyle.NEUTRAL;
        this.slot = -1;
        this.amount = 1;
        this.glint = false;
        this.hidden = false;
        this.action = "";
        this.permission = "";
    }

    public String id() {
        return id;
    }

    public boolean hidden() {
        return hidden;
    }

    public Material icon() {
        return icon;
    }

    public int slot() {
        return slot;
    }

    /**
     * A config-declared action, e.g. {@code command:home} or
     * {@code screen:shop-root}. Blank when the button's behaviour comes from
     * Java instead. Resolved by {@link MenuActions}.
     */
    public String action() {
        return action;
    }

    /** Node required to use this button; blank means everyone. */
    public String permission() {
        return permission;
    }

    /** Resolves the label against {@code ctx} without building a whole button. */
    public String label(Ctx ctx) {
        return ctx == null ? label : ctx.applyRaw(label);
    }

    /** Starts a builder pre-filled from config. Returns {@code null} when hidden. */
    public Builder builder(Ctx ctx) {
        return hidden ? null : new Builder(this, ctx);
    }

    /** Convenience for the common case: a visible, enabled button with one action. */
    public ScreenButton build(Ctx ctx, Consumer<Player> action) {
        Builder builder = builder(ctx);
        return builder == null ? null : builder.action(action).build();
    }

    /** Convenience for a button that is drawn but refuses, with a reason. */
    public ScreenButton buildLocked(Ctx ctx, String reason) {
        Builder builder = builder(ctx);
        return builder == null ? null : builder.locked(reason).build();
    }

    /**
     * Wraps {@link ScreenButton.Builder}, seeding it from config and letting the
     * caller override only what is genuinely dynamic.
     */
    public static final class Builder {

        private final ButtonTemplate template;
        private final Ctx ctx;
        private final ScreenButton.Builder delegate;
        private boolean locked;

        private Builder(ButtonTemplate template, Ctx ctx) {
            this.template = template;
            this.ctx = ctx;
            this.delegate = ScreenButton.of(template.id, template.label(ctx))
                    .style(template.style)
                    .slot(template.slot)
                    .amount(template.amount)
                    .glint(template.glint);
            if (template.icon != null) {
                delegate.material(template.icon);
            }
            for (String line : template.lore) {
                delegate.line(ctx == null ? line : ctx.applyRaw(line));
            }
        }

        public Builder action(Consumer<Player> action) {
            delegate.action(action);
            return this;
        }

        /** Disables the button, swapping in {@code locked-label} when one is set. */
        public Builder locked(String reason) {
            this.locked = true;
            if (!template.lockedLabel.isBlank()) {
                delegate.label(ctx == null
                        ? template.lockedLabel
                        : ctx.applyRaw(template.lockedLabel));
            }
            delegate.locked(reason);
            return this;
        }

        /** Locks the button only when {@code condition} holds. */
        public Builder lockedIf(boolean condition, String reason) {
            return condition ? locked(reason) : this;
        }

        /** Extra lore beyond what config supplies — live progress, prices, counts. */
        public Builder line(String line) {
            delegate.line(ctx == null ? line : ctx.applyRaw(line));
            return this;
        }

        public Builder lines(List<String> lines) {
            for (String line : lines) {
                line(line);
            }
            return this;
        }

        /** Overrides the configured icon. Used where the icon IS the data. */
        public Builder material(Material material) {
            if (material != null) {
                delegate.material(material);
            }
            return this;
        }

        public Builder item(ItemStack stack) {
            delegate.item(stack);
            return this;
        }

        public Builder slot(int slot) {
            delegate.slot(slot);
            return this;
        }

        public Builder amount(int amount) {
            delegate.amount(amount);
            return this;
        }

        public Builder glint(boolean glint) {
            delegate.glint(glint);
            return this;
        }

        public Builder style(ButtonStyle style) {
            delegate.style(style);
            return this;
        }

        public boolean isLocked() {
            return locked;
        }

        public ScreenButton build() {
            return delegate.build();
        }
    }
}
