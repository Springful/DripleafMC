package net.dripleaf.core.common.icon;

import net.dripleaf.core.common.config.ValidationLog;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Material to atlas sprite, in one place, with an escape hatch.
 *
 * <p><b>The bug this exists to fix.</b> Minecraft 1.21.9 added the {@code object}
 * text component with an atlas-sprite implementation, exposed in Adventure as
 * {@link ObjectContents#sprite(Key, Key)}. There is a two-argument form taking
 * an explicit atlas and a one-argument form that inherits
 * {@code DEFAULT_ATLAS}. Code that used the one-argument form — or that
 * hardcoded {@code minecraft:blocks} — resolves every sprite against the block
 * atlas. {@code minecraft:block/sand} exists there and renders;
 * {@code minecraft:item/diamond} does not, so it renders as the pink-and-black
 * missing-texture checkerboard. That is exactly the split seen in the shop:
 * every block fine, every item broken.
 *
 * <p><b>Resolution order.</b>
 * <ol>
 *   <li>An explicit override from {@code core/icons.yml}. One line of config
 *       fixes any material that resolves wrongly, with no rebuild.</li>
 *   <li>The block atlas for a material that is a block:
 *       {@code minecraft:blocks} / {@code minecraft:block/<key>}.</li>
 *   <li>The item atlas otherwise: {@code minecraft:items} /
 *       {@code minecraft:item/<key>}.</li>
 *   <li>The configured fallback sprite plus the material's name in text. Never
 *       emit an unresolvable sprite — a clean placeholder is infinitely better
 *       than a checkerboard.</li>
 * </ol>
 *
 * <p>Block texture names do not reliably match material names —
 * {@code grass_block} has {@code grass_block_top} and {@code grass_block_side},
 * not {@code grass_block}. Ores, doors, beds and anything with a multi-face
 * model are the same story. The shipped {@code icons.yml} is pre-populated with
 * the known offenders and is where any newly discovered one goes.
 *
 * <p>Resolved components are cached per material. The cache is bounded by the
 * size of {@link Material} and is cleared on reload.
 */
public final class IconService {

    private static final Key BLOCK_ATLAS = Key.key("minecraft", "blocks");
    private static final Key ITEM_ATLAS = Key.key("minecraft", "items");

    /** One override entry: which atlas, and which sprite inside it. */
    public record Override(Key atlas, Key sprite) {
    }

    private final Map<Material, Override> overrides = new EnumMap<>(Material.class);
    private final Map<Material, Component> cache = new EnumMap<>(Material.class);
    private final Set<Material> fellBack = new LinkedHashSet<>();

    private Override fallback = new Override(ITEM_ATLAS, Key.key("minecraft", "item/barrier"));
    private int resolvedCount;

    /** Re-reads {@code core/icons.yml}. Safe at runtime; drops the cache. */
    public void load(YamlConfiguration yaml, ValidationLog log) {
        overrides.clear();
        cache.clear();
        fellBack.clear();
        resolvedCount = 0;

        ConfigurationSection section = yaml.getConfigurationSection("overrides");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = material(key);
                if (material == null) {
                    log.add("core/icons.yml", "overrides." + key,
                            "unknown material, ignored");
                    continue;
                }
                ConfigurationSection node = section.getConfigurationSection(key);
                if (node == null) {
                    continue;
                }
                Key atlas = key(node.getString("atlas", ""));
                Key sprite = key(node.getString("sprite", ""));
                if (atlas == null || sprite == null) {
                    log.add("core/icons.yml", "overrides." + key,
                            "atlas and sprite must both be namespaced keys, ignored");
                    continue;
                }
                overrides.put(material, new Override(atlas, sprite));
            }
        }

        ConfigurationSection fb = yaml.getConfigurationSection("fallback");
        if (fb != null) {
            Key atlas = key(fb.getString("atlas", ""));
            Key sprite = key(fb.getString("sprite", ""));
            if (atlas != null && sprite != null) {
                fallback = new Override(atlas, sprite);
            } else {
                log.add("core/icons.yml", "fallback",
                        "not a valid atlas/sprite pair, using minecraft:item/barrier");
            }
        }
    }

    /**
     * The rendered sprite for a material, with the material's readable name as
     * the component fallback so a client that cannot draw the sprite still
     * shows something meaningful.
     */
    public Component icon(Material material) {
        if (material == null) {
            return sprite(fallback, "Unknown");
        }
        Component cached = cache.get(material);
        if (cached != null) {
            return cached;
        }
        Component resolved = resolve(material);
        cache.put(material, resolved);
        return resolved;
    }

    private Component resolve(Material material) {
        Override override = overrides.get(material);
        if (override != null) {
            resolvedCount++;
            return sprite(override, pretty(material));
        }

        String path = material.getKey().getKey();
        // Blocks first: a material that is both (STONE, DIAMOND_BLOCK) draws from
        // the block atlas, which is where its texture actually lives.
        if (material.isBlock()) {
            resolvedCount++;
            return sprite(new Override(BLOCK_ATLAS, Key.key("minecraft", "block/" + path)),
                    pretty(material));
        }
        if (material.isItem()) {
            resolvedCount++;
            return sprite(new Override(ITEM_ATLAS, Key.key("minecraft", "item/" + path)),
                    pretty(material));
        }

        fellBack.add(material);
        return sprite(fallback, pretty(material));
    }

    private static Component sprite(Override override, String fallbackName) {
        return Component.object(ObjectContents.sprite(override.atlas(), override.sprite()))
                .fallback(Component.text(fallbackName));
    }

    /**
     * Resolves every material referenced by the shipped configs so a bad icon is
     * a start-up warning rather than a player report three weeks later.
     *
     * @return the summary line, e.g. {@code Icons: 128 resolved, 2 fell back (…)}
     */
    public String validate(Iterable<Material> referenced) {
        for (Material material : referenced) {
            icon(material);
        }
        StringBuilder sb = new StringBuilder(96);
        sb.append("Icons: ").append(resolvedCount).append(" resolved, ")
                .append(fellBack.size()).append(" fell back");
        if (!fellBack.isEmpty()) {
            sb.append(" (");
            List<String> names = new ArrayList<>(fellBack.size());
            for (Material material : fellBack) {
                names.add(material.name());
            }
            sb.append(String.join(", ", names)).append(')');
        }
        return sb.toString();
    }

    public Set<Material> unresolved() {
        return Set.copyOf(fellBack);
    }

    public int resolvedCount() {
        return resolvedCount;
    }

    public int overrideCount() {
        return overrides.size();
    }

    // ---------------------------------------------------------------- naming

    /** {@code DIAMOND_HORSE_ARMOR} to {@code Diamond Horse Armor}. */
    public static String pretty(Material material) {
        String raw = material.getKey().getKey().replace('_', ' ');
        StringBuilder sb = new StringBuilder(raw.length());
        boolean capitalise = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append(capitalise ? Character.toUpperCase(c) : c);
            capitalise = c == ' ';
        }
        return sb.toString();
    }

    /** Lenient material lookup: accepts {@code DIAMOND}, {@code diamond}, {@code minecraft:diamond}. */
    public static Material material(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim().toLowerCase(Locale.ROOT);
        int colon = cleaned.indexOf(':');
        if (colon >= 0) {
            cleaned = cleaned.substring(colon + 1);
        }
        try {
            return Material.valueOf(cleaned.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static Material material(String raw, Material fallback) {
        Material material = material(raw);
        return material == null ? fallback : material;
    }

    private static Key key(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Key.key(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
