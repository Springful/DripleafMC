package net.dripleafmc.core.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Inline item icons for dialog button labels.
 *
 * Minecraft 1.21.9 added the "object" text component — {"object":"atlas","sprite":"..."} —
 * which draws an 8x8 sprite inline in text with no resource pack. That is what Donut's
 * item picker is using.
 *
 * It is built here by handing raw JSON to the Gson serializer rather than through
 * Adventure's typed ObjectContents API, so this compiles and runs against older Paper
 * too: on a server whose Adventure doesn't know the object type the parse throws once,
 * gets caught, and the mode degrades instead of breaking every menu.
 *
 * FONT mode is the pre-1.21.9 route: private-use glyphs from your own resource pack.
 *
 * Resolved icons are cached per material — the JSON is parsed once, not per menu open.
 */
public final class Icons {

    public enum Mode { AUTO, SPRITE, FONT, NONE }

    private final Map<Material, Component> cache = new EnumMap<>(Material.class);
    private final Map<Material, String> spriteOverrides = new EnumMap<>(Material.class);
    private final Map<Material, String> glyphs = new EnumMap<>(Material.class);
    private final Logger log;

    private Mode mode = Mode.AUTO;
    private Mode resolved = Mode.NONE;
    private String atlas = "minecraft:blocks";
    private String fontKey = "minecraft:default";
    private boolean probed;

    public Icons(Logger log) {
        this.log = log;
    }

    public void load(File file) {
        cache.clear();
        spriteOverrides.clear();
        glyphs.clear();
        probed = false;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        try {
            mode = Mode.valueOf(yml.getString("mode", "AUTO").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mode = Mode.AUTO;
        }
        atlas = yml.getString("atlas", "minecraft:blocks");
        fontKey = yml.getString("font.key", "minecraft:default");

        ConfigurationSection sprites = yml.getConfigurationSection("sprites");
        if (sprites != null) {
            for (String key : sprites.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material != null) spriteOverrides.put(material, sprites.getString(key));
            }
        }
        ConfigurationSection font = yml.getConfigurationSection("font.glyphs");
        if (font != null) {
            for (String key : font.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material != null) glyphs.put(material, font.getString(key));
            }
        }
    }

    /**
     * Guesses the vanilla sprite path. Blocks live under block/, everything else under
     * item/ — right for the large majority, and icons.yml overrides the rest.
     */
    private String spritePath(Material material) {
        String override = spriteOverrides.get(material);
        if (override != null) return override;
        String name = material.getKey().getKey();
        return (material.isBlock() ? "block/" : "item/") + name;
    }

    private Component buildSprite(Material material) {
        String json = "{\"object\":\"atlas\",\"atlas\":\"" + atlas + "\",\"sprite\":\""
                + spritePath(material) + "\"}";
        return GsonComponentSerializer.gson().deserialize(json);
    }

    /** One probe decides whether this server can draw sprites at all. */
    private void probe() {
        if (probed) return;
        probed = true;
        if (mode == Mode.NONE) {
            resolved = Mode.NONE;
            return;
        }
        if (mode == Mode.FONT) {
            resolved = glyphs.isEmpty() ? Mode.NONE : Mode.FONT;
            return;
        }
        try {
            buildSprite(Material.STONE);
            resolved = Mode.SPRITE;
            log.info("[DripleafCore] item icons: sprite components supported.");
        } catch (Throwable t) {
            if (mode == Mode.SPRITE) {
                log.warning("[DripleafCore] icons.yml is set to SPRITE but this server can't render "
                        + "object components (needs 1.21.9+). Icons are off.");
                resolved = Mode.NONE;
            } else {
                resolved = glyphs.isEmpty() ? Mode.NONE : Mode.FONT;
                log.info("[DripleafCore] item icons: sprites unavailable, using "
                        + (resolved == Mode.FONT ? "resource pack glyphs." : "no icons."));
            }
        }
    }

    /** The icon on its own, or empty when this server or config can't draw one. */
    public Component icon(Material material) {
        probe();
        if (resolved == Mode.NONE) return Component.empty();
        return cache.computeIfAbsent(material, m -> {
            try {
                if (resolved == Mode.SPRITE) return buildSprite(m);
                String glyph = glyphs.get(m);
                if (glyph == null) return Component.empty();
                return Component.text(glyph)
                        .font(net.kyori.adventure.key.Key.key(fontKey))
                        .decoration(TextDecoration.ITALIC, false);
            } catch (Throwable t) {
                return Component.empty();
            }
        });
    }

    /** "<icon> Andesite" — the label shape the picker and shop lists use. */
    public Component label(Material material, String name, NamedTextColor color) {
        Component icon = icon(material);
        Component text = Component.text(name, color);
        return icon.equals(Component.empty()) ? text : icon.append(Component.text(" ")).append(text);
    }

    public Component label(Material material, Component name) {
        Component icon = icon(material);
        return icon.equals(Component.empty()) ? name : icon.append(Component.text(" ")).append(name);
    }
}
