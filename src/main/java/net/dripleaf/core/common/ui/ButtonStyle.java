package net.dripleaf.core.common.ui;

import net.dripleaf.core.common.text.Palette;
import org.bukkit.Material;

/**
 * The visual role of a button, so the two renderers stay in step.
 *
 * <p>A style carries both a MiniMessage colour (used for the dialog label and
 * the chest item name) and a default chest material, so a screen author writes
 * {@code .style(SOUL)} once instead of picking a purple dye and a purple
 * gradient separately in two places and getting one of them wrong.
 */
public enum ButtonStyle {

    /** Ordinary action. Brand gradient. */
    PRIMARY(Material.LIME_DYE, Palette.BRAND_FROM, Palette.BRAND_TO),
    /** Soul / ascension action. */
    SOUL(Material.PURPLE_DYE, Palette.SOUL_FROM, Palette.SOUL_TO),
    /** Shard-priced action. */
    SHARD(Material.LIGHT_BLUE_DYE, Palette.SHARD_FROM, Palette.SHARD_TO),
    /** Irreversible or destructive. */
    DANGER(Material.REDSTONE, Palette.DANGER, Palette.DANGER_DEEP),
    /** Navigation, information, "back". */
    NEUTRAL(Material.PAPER, Palette.BODY, Palette.MUTED),
    /** Unavailable — shown, never hidden, with the reason in the tooltip. */
    LOCKED(Material.GRAY_DYE, Palette.MUTED, Palette.STRUCTURE);

    private final Material material;
    private final String from;
    private final String to;

    ButtonStyle(Material material, String from, String to) {
        this.material = material;
        this.from = from;
        this.to = to;
    }

    public Material material() {
        return material;
    }

    /** Wraps {@code text} in this style's gradient, as MiniMessage source. */
    public String paint(String text) {
        return from.equals(to)
                ? Palette.colour(from, text)
                : Palette.gradient(from, to, text);
    }

    public String primaryColour() {
        return from;
    }
}
